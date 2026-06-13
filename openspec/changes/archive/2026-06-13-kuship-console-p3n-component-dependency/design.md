## Context
AppDependencyView(正向)：deps=get_service_dependencies(tenant_service_relation where service_id=this→dep_service_id)→load services；bean={port_list,total}。AppDependencyViewList(反向)：deps=reverse(where dep_service_id=this→service_id)→load；bean={service_id,port_list,total}。每 dep→{service_cname,service_id,service_type,service_alias,group_name,group_id,ports_list}；按 group_id==current_group_id 分 current/other(current 在前)；分页(默认 25，start>=len→start=end=len-1)。data 顺序 bean,list,total。get_services_group_name 无关系→未分组/-1。

## Goals / Non-Goals
**Goals:** 正向/反向依赖读 对 7070(单组件 list=[]+port_list+bean)。
**Non-Goals:** dependency-reverse 端点、not-dependency、增删改。

## Decisions
### 决策 1：TenantServiceRelation 实体+repo(findByTenantIdAndServiceId 正向/findByTenantIdAndDepServiceId 反向)；TenantServiceInfo.findByServiceIdIn 载入 dep
### 决策 2：共享 build()：dep_info 构建 + current/other 分组 + 分页 clamp + port_list；正向 bean 无 service_id、反向有
### 决策 3：controller GeneralMessage.bean + putExtra("list")+putExtra("total")→data={bean,list,total}

## Risks / Trade-offs
- 单组件无依赖→list 空校准；dep_info 填充由单测覆盖(多组件依赖拓扑 calibration defer)。

## Migration Plan
无 schema 变更。校准：探针 list=[]、port_list=[5000]、bean 结构 两端点逐字节一致。

## Open Questions
- 无。
