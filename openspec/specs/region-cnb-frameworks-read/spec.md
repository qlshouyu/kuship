# region-cnb-frameworks-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3f-cnb-frameworks. Update Purpose after archive.
## Requirements
### Requirement: 集群 CNB 框架读
系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/regions/{region_id}/cnb/frameworks`（对齐 `EnterpriseRegionCNBFrameworks.get`）：调 region `/v2/cluster/cnb/frameworks?lang=`（lang 缺省 nodejs），返回 `list` = 响应 `list`，`msg_show=获取成功`；异常回退 `code=400 msg=failed msg_show=获取CNB框架列表失败`。

#### Scenario: 返回框架列表
- **WHEN** 已认证用户请求该接口且 region 可达
- **THEN** 返回 `code=200`、`msg_show=获取成功`，`data.list` 为框架定义数组，与 7070 逐字节一致

#### Scenario: region 异常回退
- **WHEN** region 不可达或解析失败
- **THEN** 返回 `code=400`、`msg=failed`、`msg_show=获取CNB框架列表失败`

