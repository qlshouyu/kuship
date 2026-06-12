## Context

P0 已交付契约层（响应信封/异常/JWT 认证/RequestContext/RegionClient 骨架）并真机验证（含 7070 双向 token 互认、validate 对真实 console schema 通过）。本轮在其上补"进入控制台"的最小读路径。全局架构见 `docs/kuship-console-架构设计.md`。

参照源（rainbond-console，base.py 实测）：
- `JWTAuthApiView.initial`：`self.enterprise = TenantEnterprise.objects.filter(enterprise_id=user.enterprise_id).first()`；`is_enterprise_admin = enterprise_user_perm_repo.is_admin(...)`。用户 **单一** `enterprise_id`。
- `TenantHeaderView.initial`：`self.tenant = Tenants.objects.get(tenant_name=<path>, enterprise_id=user.enterprise_id)`；查不到 → `ServiceHandleException(msg="team not found", msg_show="团队不存在")`。**注意：initial 不做逐用户成员强校验**，只校验"团队属于当前用户的企业"；细粒度权限（权限码/owner 短路）在 `check_perms` 按路由 perms 标签做——**那部分属于 P1-b RBAC，本轮不做**。

## Goals / Non-Goals

**Goals:**
- 实现 6 个读接口：`users/details`、`enterprises`、`enterprise/{eid}/overview`、`enterprise/{eid}/teams`、`enterprise/{eid}/user/{user_id}/teams`、`teams/{team_name}/overview`。
- 升级多租户上下文：解析企业（user.enterprise_id → `tenant_enterprise`）与团队（`{team_name}` + enterprise_id → `tenant_info`），注入 RequestContext；团队查不到返回 `团队不存在`。
- 反向映射本轮实体并通过 `validate`。
- 逐接口与 7070 对照，信封/字段/错误码一致。

**Non-Goals:**
- 完整 RBAC（权限码分段、owner 短路、check_perms 按路由标签鉴权）→ P1-b。
- 团队成员增删、角色/权限 CRUD、团队创建/退出 → P1-b。
- 企业概览的复杂聚合统计（应用数/资源用量等跨域聚合）先返回基础字段，重聚合后续补。
- openapi/v1、多 region。

## Decisions

**D1 企业上下文：用户单一 `enterprise_id`。**
- `tenant_enterprise.enterprise_id` char(32) 唯一；用户的 `user_info.enterprise_id` 指向它。
- `GET /console/enterprises` 返回当前用户可见企业（实质是其所属企业，列表形态对齐 7070）。
- enterprise 路由的 `{enterprise_id}` 必须等于（或属于）当前用户 enterprise_id，否则按 rainbond 行为拒绝（实测校准 code/msg）。

**D2 团队上下文：按 (tenant_name, enterprise_id) 解析，不做逐用户成员强校验。**
- `Tenants(tenant_info)`：`tenant_id` char(33) 唯一、`tenant_name` 唯一、`enterprise_id`。
- 解析：`tenant_info` where `tenant_name={team_name}` and `enterprise_id=当前用户.enterprise_id`；查不到 → `ServiceHandleException(404? / 透传, msg="team not found", msg_show="团队不存在")`（HTTP code 以 7070 实测为准）。
- 注入 `RequestContext.team`（含 tenant_id/tenant_name/enterprise_id）。本轮访问粒度=团队属于用户企业即可读。

**D3 上下文解析落点：轻量 ContextResolver，由 controller 调用（不污染全局拦截器）。**
- P0 的 `TenantContextInterceptor` 仅注入路径变量字符串。本轮新增 `EnterpriseContextResolver` / `TeamContextResolver`（service），team-scoped controller 在进入业务前调用，解析实体 + 校验 + 写入 RequestContext。
- 理由：实体解析需查库，只对相关路由按需解析比在拦截器里对所有 `/console/**` 解析更省、更易测；抛出的 `ServiceHandleException` 由 P0 的 GlobalExceptionHandler 统一成信封。

**D4 实体反向映射（既有 console 库，validate、无 DB 外键、无 @Version）。**
| 实体 | 表 | PK | 关键列 |
|------|----|----|--------|
| `TenantEnterprise` | `tenant_enterprise` | `ID` | enterprise_id(uniq char32)、enterprise_name、enterprise_alias、is_active、enable_team_resource_view、logo、create_time |
| `Tenants` | `tenant_info` | `ID` | tenant_id(uniq char33)、tenant_name(uniq)、enterprise_id、is_active、creater、limit_memory、create_time、update_time |
| `TenantRegionInfo` | `tenant_region` | `ID` | tenant_id、region_name、is_active、is_init、region_tenant_name/id、region_scope |
| `EnterpriseUserPerm` | `enterprise_user_perm` | `ID` | user_id、enterprise_id、identity、token(uniq)、is_initial_enterprise_admin |
| `TenantUserRole`(最小) | `tenant_user_role` | `ID` | role_name、tenant_id、is_default —— 仅成员/角色判定所需最小集，完整 RBAC 留 P1-b |

> 复用 P0 `UserInfo`、`RegionConfig`。BaseModel 系列 PK 均为大写 `ID`（与 P0 `RegionConfig` 一致）。

**D5 响应 bean 字段以 7070 实测为准（P0 教训）。**
- 各接口 bean 的精确字段集、命名、嵌套，**不靠源码推断，靠 7070 实跑对照**（P0 已证明源码推断会漏，如 token 载荷的 nick_name）。
- 实现节奏：先搭骨架返回核心字段 → 启 kuship(8000) 与 7070 同库同密钥并跑 → diff 出参 → 补齐字段。

**D6 RBAC 派生字段：本轮用 owner 短路覆盖，真 RBAC 留 P1-b（实现期发现的范围修正）。**
- 实测 `UserDetailsView` 的响应富含 RBAC 派生字段：每个团队带 `roles`/`tenant_actions`/`role_name_list`/`is_team_owner`，外层还有企业级 `roles`/`permissions`；且每个团队带 `region`（无 region 的团队被过滤掉）。比"最小读"预期更耦合。
- 本轮策略：**保留完整响应结构**（所有 key 都给，前端不缺字段），但 RBAC 派生值这样填：
  - `is_team_owner = (tenant.creater == 当前 user_id)`；owner 的 `tenant_actions` 给全量团队权限（owner 短路，**唯一在本轮启用的 RBAC 简化**，因为响应需要它且实现成本低）；
  - 非 owner 的团队权限、企业级 `roles`/`permissions` 暂返回空/默认（完整计算留 P1-b）。
  - 覆盖"用户进自己创建的团队"happy path；非 owner 的细粒度按钮可见性待 P1-b。
- `region` 字段需 `tenant_region`(+`region_info`) 数据；与 rainbond 一致：无 region 的团队在 `users/details` 中被过滤。
- 成员关系表更正：用户加入的团队来自 `tenant_perms`(PermRelTenant，user_id↔tenant_id 整型)，而非 `tenant_user_role`（后者是角色定义，归 P1-b）。本轮实体集相应用 `PermRelTenant` 取代 `TenantUserRole`。

## Risks / Trade-offs

- **出参字段漂移** → 以 7070 逐接口 diff 为验收（同库同密钥），建对照清单。
- **企业"列表 vs 单个"语义** → `enterprises` 是否多企业，按 7070 实际返回形态对齐；当前单企业模型下通常返回 1 条。
- **概览统计耦合下游** → overview 的资源/应用聚合可能要查 region-api 或多表；本轮返回基础字段，重聚合标注 TODO，避免拖累主路径。
- **HTTP 状态码细节** → 团队不存在/越权的 code 以 7070 实测为准（可能 404 或透传 400）。
- **enterprise_user_perm.is_admin 语义** → 判定企业管理员的具体规则按 repo 实测，本轮只读用于 overview/可见性，不做强鉴权。

## Migration Plan

- 纯增量：新增 controller/service/entity/repository + 上下文 resolver；不改 P0 已交付件，不改 schema。
- 验收切流：kuship(8000) 连共享 console 库 + 同源 SECRET_KEY；rainbond-ui proxyTarget 指 8000，逐接口对照 7070(7070)。
- 回滚：proxyTarget 指回 7070 即可，纯读无写副作用。

## Open Questions

- `enterprises` 是否需要分页/多企业（按 7070 实测形态定）。
- `teams/{team_name}/overview` 概览字段里哪些必须立即给、哪些可后续补（与 rainbond-ui 实际渲染依赖对照）。
