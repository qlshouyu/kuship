## Why

P1-a 把进控制台 6 个读接口落地了，但 `GET /console/users/details` 里三个 RBAC 派生字段——企业 `permissions`、每团队 `role_name_list` 与 `tenant_actions`——本轮以空值/默认占位放过（owner 与企业管理员只走了短路），前端的权限渲染因此拿不到真实数据。要继续推进，必须先把 RBAC 的读路径底座做出来：权限码体系、角色/权限关系实体、以及"用户在企业/团队下有哪些角色、这些角色展开成哪些权限码/权限树"的解析服务。这是后续 check_perms 强制鉴权（P1-c）与团队管理写接口的共同地基。

## What Changes

- 移植 rainbond `console/utils/perms.py` 的权限码体系到 kuship-console：分段常量（`1xxxxx` 企业 / `2xxxxx` 团队 / `3xxxxx` 应用 / `4xxxxx` 组件 等）、企业角色→权限码映射（`ENTERPRISE`/`common_perms`）、团队/应用权限树（`TEAM`/`APP`）、以及权限树装配函数（对齐 `get_perms`/`assemble_perms`/`list_enterprise_perms_by_roles`/`list_enterprise_perm_codes_by_roles`）。
- 新增 RBAC 关系实体与只读仓储：`perms_info`、`role_info`、`role_perm`、`user_role`（对照 rainbond 同名表），仅做查询，不含写。
- 新增角色/权限解析服务：`list_roles(enterprise_id, user_id)`（企业管理员角色）、团队维度 `get_user_roles(kind="team", ...)`（角色名列表）、`get_user_perms(kind="team", ..., is_owner, is_ent_admin)`（团队权限树/权限码，含 owner 与企业管理员短路），逐项对照 7070 行为。
- 补全 `users/details` 的三个派生字段：企业 `permissions`、每团队 `role_name_list`、每团队 `tenant_actions`，移除 P1-a 的空占位。
- **不包含**（明确留 P1-c）：`check_perms` 路由级强制鉴权拦截、团队管理写接口（roles/perms/members CRUD、建/退团队）。本轮只做"读出权限"，不做"用权限拦截"。

## Capabilities

### New Capabilities
- `rbac-read`: 权限码体系（分段常量与权限树装配）、RBAC 关系实体（perms_info/role_info/role_perm/user_role）只读仓储、以及角色与权限解析服务（用户在企业/团队下的角色名、权限码、权限树，含 owner/企业管理员短路）。

### Modified Capabilities
- `account-profile`: `GET /console/users/details` 的 RBAC 派生字段从 P1-a 的空占位改为真实值——企业 `permissions`、每团队 `role_name_list`、每团队 `tenant_actions` 与 7070 实测一致。

## Impact

- 代码：新增 `modules/rbac`（实体/仓储/服务/权限码常量与装配工具）；改动 `modules/account/service/AccountProfileService`（注入 RBAC 解析、填充派生字段）。
- 数据：只读访问共享 console 库的 `perms_info`、`role_info`、`role_perm`、`user_role` 表（rainbond 既有表，无 schema 变更）。
- 接口：`GET /console/users/details` 响应字段内容变化（结构不变），仍走 P0 鉴权与响应契约。
- 联调：沿用 P0/P1-a 的 7070 deep-diff 打法，对 `permissions`/`role_name_list`/`tenant_actions` 逐叶子校准。
- 不变：不新增对外写接口，不引入鉴权拦截，不改动其它 P1-a 接口的现有字段。
