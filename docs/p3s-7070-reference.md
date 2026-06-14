# P3-s 组件自动伸缩规则列表读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/xparules?region_name=rainbond`
- rainbond: `ListAppAutoscalerView.get` → `list_autoscaler_rules` —— 纯 DB。
- rules=autoscaler_rules(service_id)；metrics=autoscaler_rule_metrics(rule_id in rule_ids) 按 rule_id 分组；每 rule.to_dict + `metrics`=该 rule 的 metric.to_dict 列表(无则 [])。
- rule.to_dict 7 列：ID, rule_id, service_id, enable(bool), xpa_type, min_replicas, max_replicas。
- metric.to_dict 6 列：ID, rule_id, metric_type, metric_name, metric_target_type, metric_target_value。

## 校准结果（8000 vs 7070，team=default，组件 gre8467b）
```
空（无规则）: {"bean":{},"list":[]}  MATCH ✓
```
- 本集群组件无伸缩规则 → list 空（结构逐字节一致）。rule+嵌套 metrics 分组由单测覆盖。
- **坑**：加规则(POST)调 region，未部署组件失败 → populated 校准需已部署组件，本期空态 + 嵌套单测。
- 纯 DB；探针仅创建即可校准。校准毕清理恢复原状。
- **defer**：规则增删改、xparecords(伸缩记录)。
