## Why
region 组件域续：组件“可被依赖但未依赖”的组件列表读 `teams/{team}/apps/{serviceAlias}/dependency-reverse`（`AppDependencyReverseView.get`）——纯 DB，供选择反向依赖目标。
## What Changes
- 可选反向依赖组件列表（对齐 `AppDependencyReverseView.get` + get_reverse_undependencies）：`GET .../dependency-reverse?page=&page_size=&search_key=&condition=` → `list`=[dep_info]、`total`、bean 默认 {}。dep_info=6 字段(service_cname,service_id,service_type,service_alias,group_name,group_id)；来源=team region 组件(排除自己+排除已反向依赖)；search_key+condition(group_name|service_name)模糊过滤；本应用在前/其他在后；分页默认 25。`msg_show=查询成功`。
- **不包含**：建立/解除反向依赖(POST/DELETE)。
## Capabilities
### New Capabilities
- `component-reverse-undependency-read`: 可被依赖但未依赖的组件列表读。
## Impact
- 代码：TenantServiceInfo.findByTenantIdAndServiceRegion；ComponentReverseUndependencyService；ComponentReverseUndependencyController。
- 数据：只读 tenant_service/tenant_service_relation/service_group_relation/service_group/tenant_info；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：单组件 list=[]；多组件/过滤逻辑单测覆盖。
