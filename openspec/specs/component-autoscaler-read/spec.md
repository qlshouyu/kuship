# component-autoscaler-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3s-component-autoscaler. Update Purpose after archive.
## Requirements
### Requirement: 组件自动伸缩规则列表读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/xparules`（对齐 `ListAppAutoscalerView.get`）：返回 `list`=[autoscaler_rules.to_dict + metrics(按 rule_id 分组的 autoscaler_rule_metrics.to_dict)]，`msg_show=查询成功`。

#### Scenario: 有伸缩规则
- **WHEN** 组件有伸缩规则
- **THEN** 每 rule 含 7 字段 + metrics 列表，与 7070 一致

#### Scenario: 无规则
- **WHEN** 组件无伸缩规则
- **THEN** list=[]

