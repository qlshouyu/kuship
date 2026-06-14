## Why
region 组件域续：组件“可依赖但未依赖”的组件列表读 `teams/{team}/apps/{serviceAlias}/un_dependency`（`AppNotDependencyView.get`）——纯 DB，供选择正向依赖目标（须对方开放对内端口）。
## What Changes
- 可选正向依赖组件列表（对齐 `AppNotDependencyView.get` + get_undependencies）：`GET .../un_dependency?page&page_size&search_key&condition` → `list`=[dep_info 6 字段]、`total`、bean 默认 {}。来源=team region 组件(排除自己 + **须有 inner_service 端口** + 排除已正向依赖)；search_key+condition(group_name/service_name；**非法 condition 且有候选→400 condition参数错误**)；本应用在前/其他在后；分页默认 25。`msg_show=查询成功`。
- **不包含**：建立/解除依赖。
## Capabilities
### New Capabilities
- `component-undependency-read`: 可依赖但未依赖的组件列表读（须 inner 端口）。
## Impact
- 代码：ComponentUndependencyService；ComponentUndependencyController（复用 P3-t/P3-n 实体+repo）。
- 数据：只读 tenant_service/tenant_service_relation/tenant_services_port/service_group_relation/service_group/tenant_info；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：单组件 list=[]；inner 端口过滤/排除/condition 400 单测覆盖。
