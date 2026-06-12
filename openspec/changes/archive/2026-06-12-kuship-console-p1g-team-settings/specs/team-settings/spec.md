## ADDED Requirements

### Requirement: 移交团队管理权

系统 SHALL 提供 `POST /console/teams/{team_name}/pemtransfer`（对齐 `UserPemTraView`），将团队 `creater` 改为请求体 `user_id` 指定的用户。该操作 SHALL 仅团队创建者（owner）可执行；非 owner SHALL 返回 HTTP 403、信封 `code=10402`（对齐 `TeamOwnerView` 的 owner-only 门槛）。成功返回 `msg_show=移交成功`。

#### Scenario: owner 移交成功

- **WHEN** 团队创建者 `POST .../pemtransfer` 携带 `{user_id}`
- **THEN** 团队 `creater` 更新为该用户，返回 `code=200`、`msg_show=移交成功`

#### Scenario: 非 owner 被拒

- **WHEN** 非团队创建者请求该接口
- **THEN** 返回 HTTP 403、信封 `code=10402`

### Requirement: 修改团队名称

系统 SHALL 提供 `POST /console/teams/{team_name}/modifyname`（对齐 `TeamNameModView`），按请求体 `new_team_alias` 更新团队 `tenant_alias`，`new_logo` 非空时一并更新 `logo`，并更新 `update_time`；返回团队完整信息 bean（对齐 `tenant.to_dict()`，含 ID/tenant_id/tenant_name/is_active/create_time/creater/limit_memory/update_time/tenant_alias/enterprise_id/namespace/logo）。该接口对团队成员开放（无权限码门槛）。

#### Scenario: 改名成功

- **WHEN** `POST .../modifyname` 携带非空 `new_team_alias`
- **THEN** 团队 `tenant_alias` 更新，返回 `msg_show=团队信息修改成功` 与团队完整 bean，与 7070 一致（`update_time` 为当前时间，不参与逐字节比对）

### Requirement: 退出团队

系统 SHALL 提供 `GET /console/teams/{team_name}/exit`（对齐 `TeamExitView`），让当前用户退出该团队：若当前用户是团队创建者 SHALL 返回 HTTP 409、`msg_show=您是当前团队创建者，不能退出此团队`；否则事务删除其在该团队的 `tenant_perms` 成员关系与 `user_role` 角色关联，返回 `msg_show=退出团队成功`。

#### Scenario: 成员退出成功

- **WHEN** 非创建者成员 `GET .../exit`
- **THEN** 其 `tenant_perms` 成员关系与本团队 `user_role` 被事务删除，返回 `code=200`、`msg_show=退出团队成功`

#### Scenario: 创建者不可退出

- **WHEN** 团队创建者 `GET .../exit`
- **THEN** 返回 HTTP 409、`msg_show=您是当前团队创建者，不能退出此团队`，不删除
