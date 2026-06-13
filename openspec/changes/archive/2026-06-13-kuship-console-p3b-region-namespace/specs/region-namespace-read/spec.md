## ADDED Requirements

### Requirement: 集群命名空间读
系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/regions/{region_id}/namespace`（对齐 `EnterpriseRegionNamespace`）：由 `region_id` 解析 region_name，调 region `/v2/cluster/namespace?eid=<eid>&content=<content>`（content 默认 all），返回 `bean` = 响应 `list`（命名空间名数组），`msg_show=获取成功`。

#### Scenario: 返回命名空间列表
- **WHEN** 已认证用户请求该接口且 region 可达
- **THEN** 返回 `code=200`、`msg_show=获取成功`，`data.bean` 为命名空间名数组，与 7070 一致
