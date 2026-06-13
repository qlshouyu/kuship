# P3-r 组件 k8s 属性列表读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/components/{serviceAlias}/k8s-attributes?region_name=rainbond`（注意路径 `components` 非 `apps`）
- rainbond: `ComponentK8sAttributeListView.get` → `list_by_component_ids` —— 纯 DB。
- list 项 = component_k8s_attributes 8 列：ID, create_time, update_time(空格), tenant_id, component_id, name, save_type, attribute_value。
- **save_type=="json" 且非空**：JSON 解析——对象→`[{key,value}]`、数组/字符串透传、解析失败→原样字符串；非 json→原样。

## 校准结果（8000 vs 7070，team=default，组件 grd26aeb）
```
空: {"bean":{},"list":[]}  MATCH ✓
```
- 本集群组件无 k8s 属性 → list 空（结构逐字节一致）。json 对象→[{key,value}]/数组透传/非 json 原样 转换由 4 个单测覆盖。
- **坑**：添加属性(POST)会调 region create_component_k8s_attribute，未部署组件→404"数据中心资源不存在"且 @transaction.atomic 回滚 → populated 校准需已部署组件，本期空态校准 + 转换单测。
- 纯 DB；探针仅创建即可校准。校准毕清理恢复原状。
