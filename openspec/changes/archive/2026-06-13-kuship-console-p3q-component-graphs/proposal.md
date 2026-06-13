## Why
region 组件域续：组件监控图列表读 `teams/{team}/apps/{serviceAlias}/graphs`（`ComponentGraphListView.get`）——纯 DB，按 sequence 排序。
## What Changes
- 监控图列表（对齐 `ComponentGraphListView.get` + list_component_graphs）：`GET .../graphs?region_name=` → `list`=[graph.to_dict]（component_graphs 按 sequence），每项 6 字段(ID,component_id,graph_id,title,promql,sequence)。`msg_show=查询成功`。
- **不包含**：监控图增删改、internal-graphs/exchange-graphs。
## Capabilities
### New Capabilities
- `component-graphs-read`: 组件监控图列表读（component_graphs.to_dict，纯 DB）。
## Impact
- 代码：ComponentGraph 实体+repo；ComponentGraphService；ComponentGraphController。
- 数据：只读 component_graphs/tenant_service；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：有/无监控图对 7070 逐字节一致。
