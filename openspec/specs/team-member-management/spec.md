# team-member-management Specification

## Purpose
TBD - created by archiving change kuship-console-p1e-team-member-roles. Update Purpose after archive.
## Requirements
### Requirement: 团队成员列表

系统 SHALL 提供 `GET /console/teams/{team_name}/users`，分页（每页 8）返回团队成员（`perm_rel_tenant` 按团队 PK 关联 `user_info`），每项含 `user_id`、`user_name`、`nick_name`、`email`、`role_info`，并在 `data.total` 给总数。`role_info` 对齐 rainbond 现状（取当前请求用户在该团队的角色列表）。支持 `query` 模糊过滤（nick_name/real_name）。

#### Scenario: 返回成员分页列表

- **WHEN** 已认证且有成员查看权的用户请求 `GET /console/teams/{team_name}/users`
- **THEN** 返回 `code=200`，`data.list` 为成员项、`data.total` 为总数，与 7070 一致

### Requirement: 团队成员角色总览

系统 SHALL 提供 `GET /console/teams/{team_name}/users/roles`，返回团队成员及各自角色：每项 `{nick_name, email, user_id, roles}`，`roles` 为该成员在本团队的角色项 `[{role_id, role_name}]`；团队创建者 SHALL 额外附带 `{role_id:0, role_name:"拥有者"}`。

#### Scenario: 成员角色总览含拥有者标记

- **WHEN** 请求 `GET /console/teams/{team_name}/users/roles`
- **THEN** 返回各成员 `{...,roles}` 列表，创建者的 `roles` 含 `{role_id:0, role_name:"拥有者"}`，与 7070 一致

### Requirement: 单成员角色查看与分配

系统 SHALL 提供 `GET/PUT/DELETE /console/teams/{team_name}/users/{user_id}/roles`：

- `GET`：返回 `{nick_name, user_id, roles:[{role_id, role_name}]}`（该成员在本团队的角色）；
- `PUT`：以 `roles`（角色 ID 列表）重建该成员在本团队的 `user_role`——事务内先删其团队角色关联，再按 `roleIds ∩ 团队角色ID` 批量写；若传入角色全部不可分配且列表非空 SHALL 报错；返回更新后的角色；
- `DELETE`：清空该成员在本团队的全部角色，返回（空）角色。

#### Scenario: 分配角色重建关联

- **WHEN** `PUT` 携带 `roles=[role_id...]`（均为本团队有效角色）
- **THEN** 该成员在本团队的 `user_role` 被替换为这些角色，返回更新后的 `roles`

#### Scenario: 传入无效角色被拒

- **WHEN** `PUT` 的 `roles` 非空但无一是本团队有效角色
- **THEN** 返回错误（`传入角色不可被分配，请检查参数`），不改动

#### Scenario: 清空成员角色

- **WHEN** `DELETE .../users/{user_id}/roles`
- **THEN** 该成员在本团队的 `user_role` 关联被清除，返回空角色

### Requirement: 成员权限树视图

系统 SHALL 提供 `GET /console/teams/{team_name}/users/{user_id}/perms`，返回该成员的团队权限树 `{user_id, permissions}`（复用 P1-b 的团队权限树解析；owner/企业管理员短路口径与 7070 一致）。

#### Scenario: 返回成员权限树

- **WHEN** 请求 `GET /console/teams/{team_name}/users/{user_id}/perms`
- **THEN** 返回 `{user_id, permissions:树}`，与 7070 一致

### Requirement: 成员接口鉴权

团队成员相关接口 SHALL 受强制鉴权保护，码对齐 `TEAM_MEMBER_PERMS`：`get=610001`、`put=610003`、`delete=610004`。

#### Scenario: 无成员管理权限被拒

- **WHEN** 不具备相应 `TEAM_MEMBER_PERMS` 码的用户访问成员接口
- **THEN** 返回 HTTP 403、信封 `code=10402`（沿用 P1-c 无权契约）

