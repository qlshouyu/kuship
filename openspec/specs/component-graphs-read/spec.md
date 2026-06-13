# component-graphs-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3q-component-graphs. Update Purpose after archive.
## Requirements
### Requirement: 组件监控图列表读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/graphs`（对齐 `ComponentGraphListView.get`）：返回 `list`=component_graphs(component_id 按 sequence).to_dict（6 字段），`msg_show=查询成功`。

#### Scenario: 有监控图
- **WHEN** 组件已配置监控图
- **THEN** list 按 sequence 排序，每项 6 字段与 7070 一致

#### Scenario: 无监控图
- **WHEN** 组件无监控图
- **THEN** list=[]

