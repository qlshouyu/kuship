## Why
region 运行态续：组件实例列表读 `teams/{team}/apps/{serviceAlias}/pods`（`ListAppPodsView.get`），region 返回 new_pods/old_pods，console 转换（内存 bytes→MB、usage_rate、主容器置首），用可丢弃 running 探针组件对 7070 稳定字段 parity 校准。

## What Changes
- 组件实例（对齐 `ListAppPodsView.get` → `region_api.get_service_pods` → region `GET /v2/tenants/{region_tenant_name}/services/{service_alias}/pods?enterprise_id=`）：`GET /console/teams/{tenantName}/apps/{serviceAlias}/pods?region_name=` → `data.list` = **dict** `{new_pods:[...], old_pods:[...]}`（**注意 list 字段是 dict 非数组**，bean={}），每 pod={pod_name, pod_status, manage_name:"manager", container:[{container_name, memory_limit(MB,2位), memory_usage(MB,2位), usage_rate(2位)}]}；容器跳过 "POD" 键；**主容器(k8s_component_name 命中且非 default-tcpmesh)置首**；region bean 为空 → list={}。`msg_show=操作成功`。
- **不包含**：POST（进入实例 c_id/h_id）、pods 详情/日志。

## Capabilities
### New Capabilities
- `component-pods-read`: 组件实例列表读（region 运行态 + 内存换算 + 主容器置首）。

## Impact
- 代码：TenantServiceInfo 加 k8s_component_name；ComponentPodsService；ComponentPodsController。
- 数据：只读 tenant_service/tenant_region/tenant_info + 实时调 region；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：稳定字段 parity（pod_name/pod_status/manage_name/container_name/memory_limit/结构稳定；memory_usage/usage_rate 易变排除）。dev 需 REGION_URL_OVERRIDE + running 探针。
