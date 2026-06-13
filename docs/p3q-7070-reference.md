# P3-q 组件监控图列表读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/graphs?region_name=rainbond`
- rainbond: `ComponentGraphListView.get` → `list_component_graphs` → `ComponentGraph.objects.filter(component_id).order_by("sequence")` → [to_dict]。纯 DB。
- list 项 = component_graphs 6 列：ID, component_id, graph_id, title, promql, sequence。

## 校准结果（8000 vs 7070，team=default，组件 grcb423a）
```
空（无图）:  list=[]  MATCH ✓
有图（加“内存”监控图）: list=[{ID,component_id,graph_id,title:内存,promql(region 改写后含 service_id),sequence:0}]  字段顺序+值 MATCH ✓
```
- 纯 DB；POST .../graphs(title/promql) 添加（promql 会被后端补 service_id 过滤）。校准毕清理恢复原状(component_graphs=0)。
- **defer**：监控图增删改、internal-graphs/exchange-graphs。
