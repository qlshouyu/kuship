## Context
AppDependencyReverseView.get→get_reverse_undependencies(team region services exclude self)排除已反向依赖→每 dep_info(6 字段,无 ports_list)；search_key/condition 过滤(group_name|service_name|both)；current/other(group_id==current_group_id)在前；分页 un_dep_list[(p-1)*size:p*size]；general_message(list=,total=)→data={bean:{},list,total}。
## Goals / Non-Goals
**Goals:** 可选反向依赖组件列表 + 过滤/分组/分页 对 7070(单组件空 + 单测过滤)。**Non-Goals:** 建/解除反向依赖。
## Decisions
### 决策 1：TenantServiceInfo.findByTenantIdAndServiceRegion + 排除自己/已反向依赖(findByTenantIdAndDepServiceId)
### 决策 2：dep_info 6 字段 + group 查找(未分组/-1)；matchSearch(condition group_name/service_name/both)；current/other 在前；分页
### 决策 3：controller GeneralMessage.list + putExtra("total")→data={bean,list,total}
## Risks / Trade-offs
- 单组件团队→空校准；多组件/过滤由单测覆盖。
## Migration Plan
无 schema 变更。校准：单组件 list=[]、total=0 逐字节一致。
## Open Questions
- 无。
