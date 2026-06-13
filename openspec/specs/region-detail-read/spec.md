# region-detail-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3c-region-detail. Update Purpose after archive.
## Requirements
### Requirement: 单集群详情读
系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/regions/{region_id}`（对齐 `EnterpriseRegionsRUDView.get`，check_status=False）：按 region_id 取 region_info，返回 open 级集群字典 `bean`（含 wsurl/httpdomain/tcpdomain/ssl_ca_cert/cert_file/key_file 等 open 专属字段，资源/健康用固定默认，不调 region 后端），`msg_show=获取成功`。

#### Scenario: 返回 open 级集群详情
- **WHEN** 已认证用户请求该接口
- **THEN** 返回 `code=200`、`msg_show=获取成功`，`data.bean` 为 open 级集群 28 字段字典，与 7070 逐字节一致

#### Scenario: region_id 不存在
- **WHEN** region_id 不存在
- **THEN** 对齐 rainbond（bean=null）

