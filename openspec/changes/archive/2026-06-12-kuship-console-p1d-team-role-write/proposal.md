## Why

P1-b/P1-c 把 RBAC 读出来、用起来（鉴权），但还不能管理它。团队管理员目前无法在 kuship 上增删团队角色、调整角色权限——这些是 rainbond 团队设置页的核心写操作。本轮补"角色管理写底座"：团队角色 CRUD + 角色权限树读写，并引入 RBAC 的事务写入路径（角色权限树降维落 `role_perms`）。这是后续成员管理（P1-e：给成员分配这些角色）与团队生命周期的前提。

## What Changes

- 角色写实体能力：`role_info`（创建/改名/删除）、`role_perms`（按角色删 + 批量写）、`user_role`（删角色时清成员关联）——在 P1-b 只读实体上补写。
- 角色服务（对齐 `RoleKindService`/`RolePermService`）：
  - `get_roles(team, tenant_id, with_default)`：团队角色（`kind_id=tenant_id`）∪ 系统默认角色（`kind_id="default"`）；
  - `create_role`/`update_role`/`delete_role`：名称非空与唯一校验（含默认角色查重）；删除为事务（连带删 `role_perms` 与 `user_role`）；默认角色（`kind_id="default"`）不可改删；
  - `get_roles_perms`/`get_role_perms`：角色权限树（复用 P1-b 的 `packRolePermsTree`，单角色含应用子模型——无应用域则空）；
  - `update_role_perms`：先删该角色 `role_perms`，再把提交的权限树降维（`unpack_role_perms_tree`）成 `role_perms` 行；名↔码映射用硬编码 `get_perms_name_code_kv()`（**不读 perms_info 表**）。
- 权限码 catalog 扩展：`getPermsNameCodeKv()`（名→码，供 unpack）、`getPermsStructure(tenantId)`（权限元数据树，name/desc/code 形态，供权限设置页）。
- 接口（对齐 rainbond 路由）：
  - `GET /console/perms`（`PermsInfoLView`，免鉴权）：权限元数据树；
  - `GET/POST /console/teams/{team_name}/roles`（列表 / 创建）；
  - `GET/PUT/DELETE /console/teams/{team_name}/roles/{role_id}`（详情 / 改名 / 删除）；
  - `GET /console/teams/{team_name}/roles/perms`（全部角色的权限树）；
  - `GET/PUT /console/teams/{team_name}/roles/{role_id}/perms`（单角色权限树 读 / 改）。
- 鉴权：上述团队角色接口标 `@RequiresPerms(TEAM, ...)`，码对齐 `TEAM_ROLE_PERMS`（get=630001 / post=630002 / put=630003 / delete=630004）；`PermsInfoLView` 免鉴权。
- **不包含**（留 P1-e / 后续）：成员管理（users 增删、角色分配）、团队生命周期（建/退/改名/删/移交）、`perms_info` 实体（本流程不读该表）、应用级权限子树（依赖应用域）。

## Capabilities

### New Capabilities
- `team-role-management`: 团队角色 CRUD、角色权限树读写（提交权限树→降维落 `role_perms`）、权限元数据树，受 `TEAM_ROLE_PERMS` 鉴权保护。

## Impact

- 代码：新增 `modules/team`（或 `modules/rbac`）下角色写服务与 controller；`role_info`/`role_perms`/`user_role` 仓储补写方法；`PermsCatalog` 加 `getPermsNameCodeKv`/`getPermsStructure`/`getStructure`。
- 复用：P1-b 的实体与 `packRolePermsTree`、P1-c 的 `@RequiresPerms`/`CheckPermsInterceptor`。
- 数据：首次对共享 console 库**写入**（`role_info`/`role_perms`/`user_role`）；仍无 schema 变更。写操作具破坏性，联调需谨慎（建议用本轮 viewer 之外的可丢弃角色，避免动 interop 默认 3 角色）。
- 接口：新增 6 个团队角色相关接口；逐接口对 7070 deep-diff 校准（读响应）+ 写后回读校准。
- 不变：不动现有读接口与鉴权行为；不引入 perms_info 表依赖。
