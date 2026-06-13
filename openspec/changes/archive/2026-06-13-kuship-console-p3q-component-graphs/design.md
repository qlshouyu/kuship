## Context
ComponentGraphListView.get→list_component_graphs→component_graph_repo.list=ComponentGraph.objects.filter(component_id).order_by("sequence")→[to_dict]；general_message(list=)。to_dict=component_graphs 6 列(ID,component_id,graph_id,title,promql,sequence)。
## Goals / Non-Goals
**Goals:** 监控图列表 对 7070(有/无)。**Non-Goals:** 增删改、internal/exchange-graphs。
## Decisions
### 决策 1：ComponentGraph(to_dict 6 列)+repo findByComponentIdOrderBySequence；service 装配 list
## Risks / Trade-offs
- 无。
## Migration Plan
无 schema 变更。校准：有/无监控图 8000 vs 7070 逐字节一致。
## Open Questions
- 无。
