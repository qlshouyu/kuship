# component-labels-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3p-component-labels. Update Purpose after archive.
## Requirements
### Requirement: 组件标签读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/labels`（对齐 `AppLabelView.get`）：返回 `bean`={used_labels, unused_labels}，used=组件 service_labels 对应 labels.to_dict，unused=region node_labels 排除已用后的 labels.to_dict，`msg_show=查询成功`。

#### Scenario: 无标签
- **WHEN** 组件无标签且区域无节点标签
- **THEN** bean={used_labels:[], unused_labels:[]}，与 7070 一致

