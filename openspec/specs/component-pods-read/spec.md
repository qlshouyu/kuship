# component-pods-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3h-component-pods. Update Purpose after archive.
## Requirements
### Requirement: 组件实例列表读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/pods`（对齐 `ListAppPodsView.get`）：调 region `/v2/tenants/{region_tenant_name}/services/{service_alias}/pods?enterprise_id=`，将 `bean.new_pods/old_pods` 转换为 `data.list`={new_pods,old_pods}（dict），每 pod 含 pod_name/pod_status/manage_name="manager"/container[]，容器内存 bytes→MB 保留 2 位、usage_rate=usage*100/limit、跳过 "POD" 容器、主容器置首；`msg_show=操作成功`。

#### Scenario: 返回实例列表
- **WHEN** 已认证用户请求且组件运行
- **THEN** `data.list.new_pods` 含运行实例，pod_name/pod_status/manage_name/container_name/memory_limit 与 7070 一致（memory_usage/usage_rate 为活体值不比对）

#### Scenario: 无实例
- **WHEN** region bean 为空
- **THEN** `data.list`={}（空 dict）

