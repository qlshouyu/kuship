## Context
ListAppAutoscalerView.get→list_autoscaler_rules：rules=autoscaler_rules(service_id)；metrics=autoscaler_rule_metrics(rule_id in rule_ids)；r2m=按 rule_id 分组 metric.to_dict；每 rule.to_dict+metrics=r2m[rule_id] 或 []。rule.to_dict 7 列、metric.to_dict 6 列。
## Goals / Non-Goals
**Goals:** 伸缩规则列表(rule+嵌套 metrics) 对 7070(空态 + 嵌套单测)。**Non-Goals:** 增删改、xparecords。
## Decisions
### 决策 1：AutoscalerRules(7 列,enable bool)/AutoscalerRuleMetrics(6 列) 实体+repo(findByServiceId/findByRuleIdIn)
### 决策 2：service 分组嵌套(r2m)+rule.metrics
## Risks / Trade-offs
- 加规则触发 region(未部署→失败)，populated 校准需已部署组件；本期空态 + 嵌套单测。
## Migration Plan
无 schema 变更。校准：空 list=[] 逐字节一致。
## Open Questions
- 无。
