## Why

P0 打通了登录鉴权与契约层，但登录成功后 rainbond-ui 立刻要拉一组**企业/团队读接口**来渲染控制台外壳（当前用户、企业列表、团队列表与切换、团队概览）。这些接口缺失，用户登录后就卡在空白控制台。

P1-a 是"进入控制台"的最小读路径：补齐 account 当前用户 + enterprise 基础 + team 读路径，并把 P0 只做注入的多租户上下文升级为**真正解析企业/团队实体并校验成员**，让 rainbond-ui 登录后能进外壳、切团队、看概览。完整的角色/权限 CRUD 与 RBAC 强制留给 P1-b。

## What Changes

- **account**：`GET /console/users/details` —— 返回当前登录用户详情（取自 P0 的 RequestContext.currentUser）。
- **enterprise 基础**：
  - `GET /console/enterprises` —— 当前用户可见企业列表。
  - `GET /console/enterprise/{enterprise_id}/overview` —— 企业概览（基础字段优先，复杂聚合可后续补）。
- **team 读路径**：
  - `GET /console/enterprise/{enterprise_id}/teams` —— 企业下团队列表。
  - `GET /console/enterprise/{enterprise_id}/user/{user_id}/teams` —— 某用户加入的团队（团队切换器）。
  - `GET /console/teams/{team_name}/overview` —— 团队概览。
- **上下文与成员校验升级**（对齐 rainbond View 基类链）：
  - 企业上下文解析（enterprise_id 来源优先级 URL → Header(`X-Enterprise-Id`) → 当前用户 enterprise_id）注入 RequestContext。
  - 团队上下文解析：按 `{team_name}` 查 `tenant_info`，校验当前用户为该团队成员，注入 RequestContext.team；非成员返回与 rainbond 一致的错误（实测校准）。
  - 仅"成员可读"级别校验，**不含**权限码分段 / owner 短路的完整 RBAC。
- **数据层**：反向映射本轮必需实体（`tenant_enterprise` / `tenant_info` / `tenant_region` / `enterprise_user_perm` / 成员判定所需的 `tenant_perms`·`user_role`·`role_info` 最小集）到既有 console 库，`validate` 模式、无 DB 外键、无 `@Version`。

非本轮（留 P1-b 及以后）：角色/权限 CRUD（`teams/{team}/roles`、`roles/perms`）、团队成员增删与角色分配、团队创建/退出、RBAC 权限码强制、openapi/v1、多 region。

## Capabilities

### New Capabilities
- `account-profile`: 当前登录用户详情读接口（`/console/users/details`）。
- `enterprise-read`: 企业基础读路径——企业列表与企业概览。
- `team-read`: 团队读路径——企业下团队列表、用户加入的团队、团队概览。

### Modified Capabilities
- `tenant-context`: 在 P0「从路径变量注入 team_name/region_name」基础上，新增**企业上下文解析**与**团队实体解析 + 成员校验**（按 `{team_name}` 查 `tenant_info`、校验成员、注入 team；非成员拒绝）。

## Impact

- **新增代码**：`kuship-console` 内 `modules/account`（扩展）、`modules/team`、`modules/enterprise` 的 controller/service/entity/repository；`common/context` 与拦截器增强。
- **共享数据库**：新增对 `tenant_enterprise`/`tenant_info`/`tenant_region`/`enterprise_user_perm`/`tenant_perms`/`user_role`/`role_info` 的只读映射，仅 `validate`，不改 schema。
- **契约**：沿用 P0 不可变契约（`/console/...` 前缀、snake_case 路径变量、`general_message` 信封、错误码=HTTP 状态码、GRJWT/HS256 同源）。
- **前端**：rainbond-ui 零源码改动，proxyTarget 指向 8000 即可登录进控制台外壳。
- **基线**：region-api 兼容基线 Rainbond v6.9.0-release（`reference/rainbond@44c5c34d`）。
- **验收**：登录后控制台外壳加载、企业/团队列表、团队切换、团队概览均可用；逐接口与 7070 对照信封/字段/错误码一致；同源 SECRET_KEY + 共享 console 库下两端成员关系一致。
