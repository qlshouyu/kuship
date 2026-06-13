## Why
region 组件域续：组件依赖读 `dependency`(正向：本组件依赖的)/`dependency-list`(反向：依赖本组件的)（`AppDependencyView`/`AppDependencyViewList`）。纯 DB（依赖关系+端口+组），单组件为空但 port_list/bean 结构可校准。

## What Changes
- 正向 `GET .../apps/{serviceAlias}/dependency?page=&page_size=`：bean={port_list,total}、list=[dep_info]、total。
- 反向 `GET .../apps/{serviceAlias}/dependency-list?page=&page_size=`：bean={**service_id**,port_list,total}、list、total。
- dep_info={service_cname,service_id,service_type,service_alias,group_name,group_id,ports_list}；本应用组件(current_group_id)在前、其他在后；分页默认 25（含 start>=len 的 clamp）；port_list=本组件端口。`msg_show=查询成功`。
- **不包含**：dependency-reverse、not-dependency、增删改。

## Capabilities
### New Capabilities
- `component-dependency-read`: 组件依赖读（正向/反向，dep 分组+端口）。

## Impact
- 代码：TenantServiceRelation 实体+repo；TenantServiceInfo.findByServiceIdIn + getServiceCname/getServiceType；ComponentDependencyService；ComponentDependencyController。
- 数据：只读 tenant_service_relation/tenant_service/tenant_services_port/service_group_relation/service_group；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：单组件 list=[]、bean.port_list 对 7070 逐字节一致；dep_info 构建单测覆盖。
