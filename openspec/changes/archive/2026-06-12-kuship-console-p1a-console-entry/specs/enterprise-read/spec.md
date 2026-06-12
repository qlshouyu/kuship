## ADDED Requirements

### Requirement: 企业列表

系统 SHALL 提供 `GET /console/enterprises`，返回当前用户可见的企业（当前模型下为其所属 `enterprise_id` 对应的 `tenant_enterprise`）。返回形态（列表/单条）与字段以 7070 实测为准。

#### Scenario: 返回当前用户所属企业

- **WHEN** 已登录用户请求 `GET /console/enterprises`
- **THEN** 返回 `code=200`，`data.list` 含其企业，条目至少含 `enterprise_id`、`enterprise_name`、`enterprise_alias`

### Requirement: 企业概览

系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/overview`，返回该企业的概览信息。基础字段优先；跨域聚合统计（应用数/资源用量等）允许后续补充。`{enterprise_id}` 保持 snake_case 路径变量。

#### Scenario: 返回企业概览基础字段

- **WHEN** 已登录用户请求自身企业的 `GET /console/enterprise/{enterprise_id}/overview`
- **THEN** 返回 `code=200`，`data.bean` 含企业基础概览字段（与 7070 对照，至少企业标识与名称类字段）

#### Scenario: 越权访问他企业

- **WHEN** 请求的 `{enterprise_id}` 不属于当前用户
- **THEN** 返回与 rainbond-console 一致的拒绝（HTTP 状态码/msg_show 以 7070 实测为准）
