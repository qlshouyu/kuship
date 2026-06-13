## Context
ComponentK8sAttributeListView.get→list_by_component_ids：filter(component_id)→每 attr：save_type=="json"&&value→json.loads；dict→attribute_value=[{key,value}]、else(array/str)→parsed；解析失败→to_dict 原样；非 json→to_dict 原样。to_dict=component_k8s_attributes 8 列(ID,create_time,update_time(空格),tenant_id,component_id,name,save_type,attribute_value)。
## Goals / Non-Goals
**Goals:** k8s 属性列表读 + json 转换 对 7070(空态 + 单测转换)。**Non-Goals:** 增删改(需已部署 region)。
## Decisions
### 决策 1：ComponentK8sAttributes(to_dict(valueOverride) 8 列)+repo findByComponentId
### 决策 2：service json 转换(Jackson Object：Map→[{key,value}]、List/标量透传、异常→原样)
## Risks / Trade-offs
- 添加属性触发 region(未部署→404 回滚)，故 populated 校准需已部署组件；本期空态校准 + 转换单测。
## Migration Plan
无 schema 变更。校准：空 list=[] 逐字节一致。
## Open Questions
- 无。
