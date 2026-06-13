## Why
region 组件域续：组件标签读 `teams/{team}/apps/{serviceAlias}/labels`（`AppLabelView.get`）——纯 DB。used_labels=组件已用标签、unused_labels=region 节点标签中未被使用的。
## What Changes
- 标签读（对齐 `AppLabelView.get` + get_service_labels）：`GET .../labels?region_name=` → `bean`={used_labels:[label.to_dict], unused_labels:[label.to_dict]}；used=service_labels(service_id)→labels；unused=node_labels(region_id)排除已用→labels。`msg_show=查询成功`。
- **不包含**：标签增删（POST/DELETE）、labels/available。
## Capabilities
### New Capabilities
- `component-labels-read`: 组件标签读（used/unused + label.to_dict）。
## Impact
- 代码：Labels/ServiceLabels/NodeLabels 实体+repo；ComponentLabelsService；ComponentLabelsController。
- 数据：只读 labels/service_labels/node_labels/tenant_service/region_info；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：bean={used_labels,unused_labels} 对 7070 逐字节一致（本集群标签主表空→均空；label.to_dict 单测覆盖）。
