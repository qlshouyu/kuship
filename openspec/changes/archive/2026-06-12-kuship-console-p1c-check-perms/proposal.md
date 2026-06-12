## Why

P1-b 把 RBAC 读出来了（用户在企业/团队下有哪些角色、权限码、权限树），但还没有"用权限拦截请求"。当前 kuship 的团队域接口（如 `teams/{team_name}/overview`）对任何已认证用户都放行，与 rainbond 不一致——rainbond 每条受保护路由都带一份 `__message`（方法→所需权限码），在 `check_perms` 处用用户的整型权限码集合做交集校验，无权即 403。本轮补这层强制鉴权底座，它是后续团队管理写接口（P1-d）能安全暴露的前提。

## What Changes

- 移植整型权限码计算（对齐 `perms.py` 的 `list_enterprise_perm_codes_by_roles`/`get_enterprise_adminer_codes`/`list_enterprise_perm_codes_by_role`）：企业 `admin` 角色展开为全部企业+团队权限码，其它角色展开为该角色码并叠加 `common_perms` 码。
- 实现用户权限码计算（对齐 `base.py` 的两类 `get_perms`）：
  - 企业级（`JWTAuthApiView.get_perms`）：用户企业角色 → 整型企业权限码集合；
  - 团队级（`TenantHeaderView.get_perms`）：企业角色码 ∪ 团队权限——owner（user==creater）短路为全部团队权限码 + `100001`；普通成员取其团队角色 `role_perms` 全局码（app_id=-1）并集。企业管理员经企业 `admin` 角色的整型码展开（已含全部团队码）通过，不单独短路（实现中据源校正，见 design 决策 3）。
- 引入路由→所需权限码的绑定机制（对齐 `perms_route_config` + URL 第三参 `__message`）：以方法（get/post/put/delete）维度声明每个受保护接口所需的权限码列表。
- 实现 `check_perms` 校验：取当前请求方法所需码，与用户权限码集合求交集，缺任一即抛无权异常；空所需码（如只读放行项）直接通过。
- 无权异常 → HTTP 403 + 信封 `code=10402`、`msg_show=没有操作权限`（对齐 rainbond `NoPermissionsError`）。为此扩展异常体系以支持独立于 HTTP status 的业务 error_code。
- 把现有团队域接口纳入强制鉴权：`teams/{team_name}/overview` 声明所需码 `200001`（对齐 `TEAM_OVERVIEW_DESCRIBE`），对 7070 校准"有权 200 / 无权 403"。
- **不包含**（明确留 P1-d）：团队角色/成员/权限写接口（roles/perms/members CRUD）、建/退团队、`perms_info` 实体（写时名↔码映射用）、应用级 `perm_apps` 细粒度鉴权（依赖应用域）。

## Capabilities

### New Capabilities
- `authorization`: 整型权限码体系（角色→码展开）、用户权限码计算（企业级与团队级，含 owner/企业管理员短路）、路由→所需码绑定机制、`check_perms` 交集校验、无权 403 响应。

### Modified Capabilities
- `team-read`: `teams/{team_name}/overview` 从"任何已认证用户放行"改为"需团队 `describe`（码 200001）权限"，无权返回 403。

## Impact

- 代码：新增 `modules/authorization`（权限码展开工具、用户权限码计算服务、check_perms 校验、路由所需码声明与拦截接入）；扩展 `common/exception`（无权异常 + 业务 error_code）；团队域接口标注所需权限码。
- 复用：P1-b 的 `role_info`/`user_role`/`role_perms` 仓储与 `EnterpriseUserPerm`（角色来源）、`PermsCatalog`（码定义）。
- 数据：仍只读共享 console 库，无 schema 变更、无写入。
- 接口：受保护接口在无权时行为变化（200→403），有权路径与字段不变；沿用 P0 鉴权与响应契约。
- 联调：沿用 7070 deep-diff/状态码校准；无权 403 需用低权限成员用户验证（现仅 interop=admin，按需在 default 团队加一个成员用户校准）。
- 不变：不新增写接口，不改动现有读接口的成功响应字段。
