## ADDED Requirements

### Requirement: 命名空间资源转换读
系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/regions/{region_id}/convert-resource`（对齐 `EnterpriseConvertResource.get`）：调 region `/v2/cluster/convert-resource?eid&content&namespace`，返回 `bean` = 响应 `bean`，并将 `unclassified` 键移到字典末尾，`msg_show=获取成功`。

#### Scenario: 返回转换资源（unclassified 末位）
- **WHEN** 已认证用户请求该接口且 region 可达
- **THEN** 返回 `code=200`、`msg_show=获取成功`，`data.bean` 为转换资源字典且 `unclassified` 为最后一个键，与 7070 一致
