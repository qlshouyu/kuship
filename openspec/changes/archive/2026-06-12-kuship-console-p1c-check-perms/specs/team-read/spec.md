## MODIFIED Requirements

### Requirement: 团队概览

系统 SHALL 提供 `GET /console/teams/{team_name}/overview`，对存在且属于当前用户企业的团队返回概览。`{team_name}` 保持 snake_case。该接口 SHALL 受强制鉴权保护：需要团队 `describe`（权限码 `200001`，对齐 rainbond `TEAM_OVERVIEW_DESCRIBE`）；缺该权限的已认证用户 SHALL 收到 403 无权响应。

#### Scenario: 返回团队概览

- **WHEN** 已登录且拥有团队 `describe`（200001）权限的用户请求其企业下存在团队的 `GET /console/teams/{team_name}/overview`
- **THEN** 返回 `code=200`，`data.bean` 含团队概览基础字段（与 7070 对照）

#### Scenario: 无权被拒

- **WHEN** 已登录但在该团队不具备 `describe`（200001）权限的用户请求该接口
- **THEN** 返回 HTTP 403、信封 `code=10402`、`msg_show=没有操作权限`，不返回团队概览

#### Scenario: 团队不存在

- **WHEN** `{team_name}` 在当前用户企业下不存在
- **THEN** 返回 `msg="team not found"`、`msg_show="团队不存在"` 的信封（HTTP 状态码以 7070 实测为准）
