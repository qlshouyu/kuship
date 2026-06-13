# region-namespace-resource-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3d-namespace-resource. Update Purpose after archive.
## Requirements
### Requirement: 命名空间资源读
系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/regions/{region_id}/resource`（对齐 `EnterpriseNamespaceResource.get`）：调 region `/v2/cluster/resource?eid&content&namespace`，返回 `bean` = 响应 `bean`，并将 `unclassified` 键移到字典末尾，`msg_show=获取成功`。

#### Scenario: 返回命名空间资源（unclassified 末位）
- **WHEN** 已认证用户请求该接口且 region 可达
- **THEN** 返回 `code=200`、`msg_show=获取成功`，`data.bean` 为资源分类字典且 `unclassified` 为最后一个键，与 7070 一致

