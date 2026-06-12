# team-read Specification

## Purpose

TBD - created by archiving change kuship-console-p1a-console-entry. Update Purpose after archive.

## Requirements

### Requirement: 企业下团队列表

系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/teams`，返回该企业下的团队列表（`tenant_info` where enterprise_id）。条目字段以 7070 实测为准。

#### Scenario: 返回企业团队列表

- **WHEN** 已登录用户请求自身企业的 `GET /console/enterprise/{enterprise_id}/teams`
- **THEN** 返回 `code=200`，`data.list` 为团队数组，条目至少含 `tenant_name`、`team_alias`/名称、`tenant_id`

### Requirement: 用户加入的团队

系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/user/{user_id}/teams`，返回指定用户在该企业加入的团队，供前端团队切换器。路径变量保持 snake_case。

#### Scenario: 返回用户的团队

- **WHEN** 已登录用户请求 `GET /console/enterprise/{enterprise_id}/user/{user_id}/teams`
- **THEN** 返回 `code=200`，`data.list` 为该用户加入的团队，字段与 7070 一致

### Requirement: 团队概览

系统 SHALL 提供 `GET /console/teams/{team_name}/overview`，对存在且属于当前用户企业的团队返回概览。`{team_name}` 保持 snake_case。

#### Scenario: 返回团队概览

- **WHEN** 已登录用户请求其企业下存在团队的 `GET /console/teams/{team_name}/overview`
- **THEN** 返回 `code=200`，`data.bean` 含团队概览基础字段（与 7070 对照）

#### Scenario: 团队不存在

- **WHEN** `{team_name}` 在当前用户企业下不存在
- **THEN** 返回 `msg="team not found"`、`msg_show="团队不存在"` 的信封（HTTP 状态码以 7070 实测为准）
