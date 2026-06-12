## ADDED Requirements

### Requirement: 未加入团队的用户列表

系统 SHALL 提供 `GET /console/teams/{team_name}/notjoinusers`，返回当前企业内、未加入该团队的用户（对齐 `get_not_join_users`）：取 `user_info` 中 `enterprise_id` 等于当前企业、且 `user_id` 不在该团队 `tenant_perms` 的用户，每项含 `user_id`、`nick_name`、`enterprise_id`、`email`。支持 `query` 模糊 `nick_name`、分页（`page`/`page_size`，默认 1/10），`data` 顶层 SHALL 含 `page`/`page_size`/`total`。

#### Scenario: 返回未加入用户分页

- **WHEN** 已认证且有成员查看权的用户请求 `GET /console/teams/{team_name}/notjoinusers`
- **THEN** 返回 `code=200`，`data.list` 为未加入用户项、`data` 顶层含 `page`/`page_size`/`total`，与 7070 一致

#### Scenario: query 过滤

- **WHEN** 带 `query` 请求
- **THEN** 仅返回 `nick_name` 匹配的未加入用户

### Requirement: 批量移除团队成员

系统 SHALL 提供 `DELETE /console/teams/{team_name}/users/batch/delete`，按请求体 `user_ids`（用户 ID 列表）移除团队成员（对齐 `batch_delete_users`）：事务内删除 `tenant_perms`（`user_id ∈ user_ids` 且 `tenant_id=团队 PK`）与该批用户在本团队角色的 `user_role`。

校验（对齐 `UserDelView`）：

- `user_ids` 为空 SHALL 返回 400「删除成员不能为空」；
- `user_ids` 含当前请求用户自身 SHALL 返回 400「不能删除自己」；
- `user_ids` 含团队创建者 SHALL 返回 400「不能删除团队创建者！」；
- 校验通过后移除并返回成功（`msg_show=删除成功`）。

#### Scenario: 移除成员成功

- **WHEN** `DELETE` 携带合法 `user_ids`（非自身、非创建者）
- **THEN** 这些用户的 `tenant_perms` 成员关系与团队内 `user_role` 被事务删除，返回 `msg_show=删除成功`

#### Scenario: 不能删除自己

- **WHEN** `user_ids` 含当前请求用户
- **THEN** 返回 400「不能删除自己」，不删除

#### Scenario: 不能删除团队创建者

- **WHEN** `user_ids` 含团队创建者
- **THEN** 返回 400「不能删除团队创建者！」，不删除

#### Scenario: 空列表被拒

- **WHEN** `user_ids` 为空
- **THEN** 返回 400「删除成员不能为空」

### Requirement: 成员移除接口鉴权

`notjoinusers` 与 `users/batch/delete` SHALL 受 `TEAM_MEMBER_PERMS` 鉴权：`get=610001`、`delete=610004`。

#### Scenario: 无成员管理权限被拒

- **WHEN** 不具备相应 `TEAM_MEMBER_PERMS` 码的用户访问上述接口
- **THEN** 返回 HTTP 403、信封 `code=10402`
