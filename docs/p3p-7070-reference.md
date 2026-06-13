# P3-p 组件标签读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/labels?region_name=rainbond`
- rainbond: `AppLabelView.get` → `get_service_labels` —— 纯 DB。
- used_labels = service_labels(service_id).label_id → labels.to_dict；unused_labels = node_labels(region_id) 排除已用 label_id → labels.to_dict。
- label.to_dict = ID/label_id/label_name/label_alias/category/create_time(空格格式)。bean={used_labels,unused_labels}。

## 校准结果（8000 vs 7070，team=default，组件 gr58834f）
```
7070/8000: {"bean":{"used_labels":[],"unused_labels":[]},"list":[]}  MATCH ✓
```
- 本集群 labels/service_labels/node_labels 主表均空 → used/unused 均空（结构逐字节一致）。label.to_dict + node 排除已用逻辑由单测覆盖。
- 纯 DB，探针仅创建即可校准。校准毕清理恢复原状。
- **defer**：标签增删(POST/DELETE)、labels/available。
