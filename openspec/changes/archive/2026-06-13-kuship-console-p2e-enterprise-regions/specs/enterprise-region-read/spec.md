## ADDED Requirements

### Requirement: 企业集群列表（safe 级）
系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/regions`（对齐 `EnterpriseRegionsLCView.get`，check_status 默认空→不调 region 后端），返回该企业集群列表。每项 SHALL 含：`region_id`、`region_alias`、`region_name`、`status`、`region_type`（`region_type` 列 JSON 解析，空则 `[]`）、`enterprise_id`、`url`、`scope`、`provider`、`provider_cluster_id`、`desc`，资源默认 `total_memory/used_memory/total_cpu/used_cpu/total_disk/used_disk=0`、`rbd_version="unknown"`、`health_status="ok"`、`resource_proxy_status=false`，`create_time`（ISO），`enterprise_alias`（由 enterprise_id 查得）。safe 级不含 wsurl/httpdomain/tcpdomain/ssl/cert/key。`msg_show=获取成功`。

#### Scenario: 返回集群列表
- **WHEN** 已认证用户请求 `GET /console/enterprise/{enterprise_id}/regions`
- **THEN** 返回 `code=200`、`msg_show=获取成功`，`data.list` 每项为上述 22 字段，与 7070 一致

#### Scenario: status 过滤
- **WHEN** 带 `status` 参数
- **THEN** 仅返回该状态的集群
