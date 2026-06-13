## Context
AppLabelView.get→get_service_labels：service_label_ids=service_labels(service_id).label_id；node_label_ids=node_labels(region_id).exclude(label_id in service_label_ids).label_id；used=labels(label_id in service_label_ids).to_dict；unused=labels(label_id in node_label_ids).to_dict；bean={used_labels,unused_labels}。label.to_dict=ID/label_id/label_name/label_alias/category/create_time(空格)。

## Goals / Non-Goals
**Goals:** 标签 used/unused 读 对 7070(空态逐字节 + to_dict 单测)。
**Non-Goals:** 增删、labels/available。

## Decisions
### 决策 1：Labels(to_dict 6 列)/ServiceLabels/NodeLabels 实体+repo(findByServiceId/findByRegionId/findByLabelIdInOrderById)
### 决策 2：ComponentLabelsService：node_labels 排除 service_label_ids；used/unused 各 toDict；region_config 缺失→unused 空

## Risks / Trade-offs
- 本集群 labels 主表空→used/unused 均空(结构校准)；to_dict/排除逻辑由单测覆盖。

## Migration Plan
无 schema 变更。校准：bean={used_labels:[],unused_labels:[]} 逐字节一致。
## Open Questions
- 无。
