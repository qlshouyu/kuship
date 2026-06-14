## Why
region 组件域续：组件自动伸缩规则列表读 `teams/{team}/apps/{serviceAlias}/xparules`（`ListAppAutoscalerView.get`）——纯 DB，rule + 嵌套 metrics。
## What Changes
- 伸缩规则列表（对齐 `ListAppAutoscalerView.get` + list_autoscaler_rules）：`GET .../xparules?region_name=` → `list`=[rule.to_dict + "metrics":[metric.to_dict 按 rule_id 分组]]。rule 7 字段(ID,rule_id,service_id,enable,xpa_type,min_replicas,max_replicas)，metric 6 字段。`msg_show=查询成功`。
- **不包含**：规则增删改（POST/PUT/DELETE，依赖已部署组件 region）、xparecords(伸缩记录)。
## Capabilities
### New Capabilities
- `component-autoscaler-read`: 组件自动伸缩规则列表读（rule+嵌套 metrics，纯 DB）。
## Impact
- 代码：AutoscalerRules/AutoscalerRuleMetrics 实体+repo；ComponentAutoscalerService；ComponentAutoscalerController。
- 数据：只读 autoscaler_rules/autoscaler_rule_metrics/tenant_service；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：空态对 7070 逐字节一致；rule+metrics 嵌套单测覆盖。
