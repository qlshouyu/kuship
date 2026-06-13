# P3-n 组件依赖读 — 7070 校准基线

## 接口
- 正向 `GET /console/teams/{tenantName}/apps/{serviceAlias}/dependency?page=&page_size=`（`AppDependencyView.get`）：本组件**依赖的**组件。bean={port_list, total}。
- 反向 `GET .../apps/{serviceAlias}/dependency-list?page=&page_size=`（`AppDependencyViewList.get`）：**依赖本组件的**组件。bean={**service_id**, port_list, total}。
- 依赖来源：tenant_service_relation（正向 where service_id=this→dep_service_id；反向 where dep_service_id=this→service_id）→ 载入 services。
- dep_info={service_cname, service_id, service_type, service_alias, group_name, group_id, ports_list}；本应用组件(group_id==current_group_id)在前、其他在后；分页默认 25（start>=len 时 clamp）。
- data 顺序：bean, list, total（list/total 由 putExtra）。

## 校准结果（8000 vs 7070，team=default，组件 gr5c7c26 + 端口 5000，无依赖）
```
[dependency]      data keys [bean,list,total] 顺序一致；bean={port_list:[5000],total:0} list=[] total=0  MATCH ✓
[dependency-list] bean={service_id,port_list:[5000],total:0} list=[] total=0  MATCH ✓
```
- 纯 DB；单组件无依赖→list 空，dep_info 构建由单测覆盖（多组件依赖拓扑 calibration defer）。校准毕清理恢复原状。
- **defer**：dependency-reverse / not-dependency 端点、增删改。
