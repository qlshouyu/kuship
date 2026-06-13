# component-status-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3g-component-status. Update Purpose after archive.
## Requirements
### Requirement: 组件状态读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/status`（对齐 `AppStatusView.get`）：调 region `/v2/tenants/{region_tenant_name}/services/{service_alias}/status?enterprise_id=` 取 `cur_status/start_time/vm_restore`，按 `status_map` 策略表计算 `status_cn/disabledAction/activeAction`，返回 `bean`={check_uuid, status, status_cn, disabledAction, activeAction, start_time, vm_restore}，`msg_show=查询成功`。

#### Scenario: 返回组件运行状态
- **WHEN** 已认证用户请求且组件已部署运行
- **THEN** 返回 `code=200`、`msg_show=查询成功`，`bean.status=running`、`status_cn=运行中`、动作列表与 7070 一致

#### Scenario: region 调用异常
- **WHEN** region 不可达或返回异常
- **THEN** `bean.status=unKnow`、`vm_restore={}`，动作列表按 unKnow 策略

