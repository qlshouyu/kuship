## ADDED Requirements

### Requirement: 企业团队概览
系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/overview/team`（对齐 `EnterpriseTeamOverView.get`），返回 bean `{active_teams, new_join_team, request_join_team}`。

`active_teams`：当前用户在该企业的团队（PermRelTenant 关联，按关联 -ID 去重），仅含有 region 的团队，每项 `{tenant_id, team_alias, owner, owner_name, enterprise_id, create_time, team_name, region, region_list, num, role}`；`num` 为该团队 ServiceGroup 数；按 `num` 降序取前 3；`role` 为创建者时 `"owner"`。

`new_join_team`：用户团队前 3，每项 `{team_name, team_alias, team_id, create_time, region, region_list, enterprise_id, owner, owner_name, roles, is_pass:true}`；`roles` 为团队角色名列表，创建者追加 `"owner"`。

`request_join_team`：加入申请列表（无 Applicants 域时为空）。

#### Scenario: 返回团队概览
- **WHEN** 已认证用户请求 `GET /console/enterprise/{enterprise_id}/overview/team`
- **THEN** 返回 `code=200`，bean 含 active_teams/new_join_team/request_join_team，与 7070 一致

#### Scenario: 创建者角色为 owner
- **WHEN** 用户是某团队创建者
- **THEN** 该团队 active_teams.role 为 `"owner"`、new_join_team.roles 含 `"owner"`
