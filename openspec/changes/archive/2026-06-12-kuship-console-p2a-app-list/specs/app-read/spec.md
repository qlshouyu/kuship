## ADDED Requirements

### Requirement: 团队应用列表

系统 SHALL 提供 `GET /console/teams/{team_name}/groups`（对齐 `TenantGroupView.get`），返回该团队在指定集群（`region_name`）下的应用列表：按 `service_group` 的 `tenant_id`（团队 tenant_id）+ `region_name` 过滤，按 `update_time` 降序、`order_index` 降序排序；每项含 `group_name`、`group_id`（即 `service_group.ID`）、`group_note`（即 `note`）。该接口对团队成员开放（无权限码门槛，对齐 `APP_CREATE_PERMS` 的 GET 所需码为空）。

#### Scenario: 返回应用列表

- **WHEN** 已认证用户请求 `GET /console/teams/{team_name}/groups?region_name=<region>`
- **THEN** 返回 `code=200`、`msg_show=查询成功`，`data.list` 为该团队该集群下的应用项（`{group_name, group_id, group_note}`），排序与 7070 一致

#### Scenario: 无应用返回空列表

- **WHEN** 团队在该集群下无应用
- **THEN** 返回 `code=200`，`data.list` 为空数组，与 7070 一致
