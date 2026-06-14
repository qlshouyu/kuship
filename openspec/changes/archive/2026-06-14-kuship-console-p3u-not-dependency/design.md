## Context
AppNotDependencyView.get→get_undependencies：team region services(exclude self) 中有 is_inner_service 端口 且 不在本组件正向 dep_service_ids → 候选；dep_info 6 字段(无 ports_list)；search/condition 过滤(condition 非法→400，仅遍历到候选时触发)；current/other 在前；分页；general_message(list=,total=)→data={bean:{},list,total}。
## Goals / Non-Goals
**Goals:** 可选正向依赖组件(须 inner 端口) + 过滤/分组/分页 对 7070(单组件空 + 单测)。**Non-Goals:** 建/解除依赖。
## Decisions
### 决策 1：复用 findByTenantIdAndServiceRegion + 排除自己/正向依赖(findByTenantIdAndServiceId)+ inner 端口(TenantServicesPort.isInnerService)
### 决策 2：matchSearch 同 P3-t 但 condition 非法→抛 400(per-candidate，对齐 view 循环内 400)
### 决策 3：controller GeneralMessage.list + putExtra("total")
## Risks / Trade-offs
- 单组件→空校准；inner 过滤/排除/400 由单测覆盖。
## Migration Plan
无 schema 变更。校准：单组件 list=[]、total=0 逐字节一致。
## Open Questions
- 无。
