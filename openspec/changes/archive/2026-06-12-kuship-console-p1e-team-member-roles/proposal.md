## Why

P1-d 让团队能管理"角色"，但还不能把角色分配给成员。团队管理员目前无法在 kuship 上查看团队成员、给成员分配/调整/清除角色——这些是团队设置「成员」页的核心。本轮补"成员角色管理"：成员列表与角色查看 + 成员角色的写（`user_role` 重建）+ 成员权限树视图。直接建立在 P1-d 的 `user_role` 写与 P1-b 的角色/权限解析之上。

## What Changes

- 成员角色服务（对齐 `UserKindRoleService`/`UserKindPermService`）：
  - `getTeamUsers(tenant)`：团队成员（`perm_rel_tenant` 按团队 PK → `user_info`）；
  - `getUsersRoles`：成员 + 各自团队角色列表（创建者额外附 `{role_id:0, role_name:"拥有者"}`）；
  - `getUserRoles(tenant, userId)`：单成员角色 `{nick_name, user_id, roles:[{role_id, role_name}]}`；
  - `updateUserRoles(tenant, userId, roleIds)`：事务重建该成员在本团队的 `user_role`——先删其团队角色关联，再按 `roleIds ∩ 团队角色ID` 批量写；传入全不可分配且非空时报错；
  - `deleteUserRoles(tenant, userId)`：清空该成员团队角色；
  - `getUserPerms(tenant, userId)`：成员权限树 `{user_id, permissions}`（复用 P1-b，owner/企业管理员短路口径同 7070）。
- 成员写实体能力：`user_role`（按用户+团队角色删 + 批量写）——在 P1-b/P1-d 基础上补。
- 接口（对齐 rainbond 路由，TEAM_MEMBER_PERMS 鉴权 get=610001/put=610003/delete=610004）：
  - `GET /console/teams/{team_name}/users`：成员分页列表（每页 8，含 `role_info`）；
  - `GET /console/teams/{team_name}/users/roles`：全部成员 + 角色；
  - `GET/PUT/DELETE /console/teams/{team_name}/users/{user_id}/roles`：单成员角色 查 / 改 / 清；
  - `GET /console/teams/{team_name}/users/{user_id}/perms`：成员权限树。
- **不包含**（留 P1-f）：未加入用户列表（`notjoinusers`）、成员加入/移除团队（动 `perm_rel_tenant` 成员关系、`UserDelView` 批量删）、移交团队管理权（`pemtransfer`）、团队生命周期（建/退/改名/删）。本轮只动 `user_role`（角色分配），不动 `perm_rel_tenant`（团队成员关系）。

## Capabilities

### New Capabilities
- `team-member-management`: 团队成员列表与角色查看、成员角色分配写（`user_role` 重建）、成员权限树视图，受 `TEAM_MEMBER_PERMS` 鉴权保护。

## Impact

- 代码：`modules/rbac` 下新增成员角色服务与 `TeamMemberController`；`UserRoleRepository` 补写（按用户+角色集合删、批量写）；复用 `PermRelTenantRepository`、P1-b `RbacReadService`、P1-d `@RequiresPerms`。
- 数据：写 `user_role`（角色分配）；不动 `perm_rel_tenant` 成员关系；无 schema 变更。
- 接口：新增 6 个成员相关接口；读对 7070 deep-diff、写后回读校准；用 viewer 验无权 403。
- 不变：不改现有接口与鉴权行为；不引入团队成员加入/移除。
