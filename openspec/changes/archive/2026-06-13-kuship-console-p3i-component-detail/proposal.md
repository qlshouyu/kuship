## Why
region 组件域续：组件详情读 `teams/{team}/apps/{serviceAlias}/detail`（`AppDetailView.get`）。对非 vm/非 market 组件是**纯 DB**（service.to_dict 全列 + 组名/命名空间/磁盘 + event_websocket_url），无 region 运行态调用 → 用可丢弃组件 DB 行即可对 7070 逐字节校准（无需部署）。

## What Changes
- 组件详情（对齐 `AppDetailView.get`，非 vm/非 market 路径）：`GET /console/teams/{tenantName}/apps/{serviceAlias}/detail?region_name=` → `bean`={service:{...service.to_dict 全列 + group_name + group_id + disk_cap}, event_websocket_url, is_third}；service.namespace 用 tenant.namespace 覆盖；disk_cap=vm?30:10（无 volume 时）；event_websocket_url 由 region.wsurl 算（非 auto → wsurl+"/event_log"）；is_third=(service_source=="third_party")。`msg_show=查询成功`。
- **不包含**：vm 分支(vm_url/vm_profile)、market 分支(rain_app_name/升级提示)、disk_cap 的 volume 覆盖（探针无 volume）、third_party 的 endpoints 字段。

## Capabilities
### New Capabilities
- `component-detail-read`: 组件详情读（service.to_dict 全列 + 组/命名空间/磁盘 + ws url，纯 DB）。

## Impact
- 代码：TenantServiceInfo 扩为全列 + toDict()；ServiceGroupRelation 实体+repo；ComponentDetailService；ComponentDetailController。
- 数据：只读 tenant_service/service_group_relation/service_group/tenant_info/region_info；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：纯 DB，bean 全字段对 7070 逐字节一致。dev 需 REGION_URL_OVERRIDE 无关（不调 region）。
