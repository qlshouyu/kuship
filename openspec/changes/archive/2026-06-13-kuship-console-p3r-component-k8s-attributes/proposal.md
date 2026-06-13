## Why
region 组件域续：组件 k8s 属性列表读 `teams/{team}/components/{serviceAlias}/k8s-attributes`（`ComponentK8sAttributeListView.get`）——纯 DB，含 json save_type 的对象→键值列表转换。
## What Changes
- k8s 属性列表（对齐 `ComponentK8sAttributeListView.get` + list_by_component_ids）：`GET .../components/{serviceAlias}/k8s-attributes?region_name=` → `list`=[attr.to_dict]（8 字段 ID/create_time/update_time/tenant_id/component_id/name/save_type/attribute_value）；save_type=="json" 且非空时：JSON 对象→`[{key,value}]`、数组/字符串透传、解析失败→原样字符串。`msg_show=查询成功`。
- **不包含**：属性增删改（POST/PUT/DELETE，依赖已部署组件 region）。
## Capabilities
### New Capabilities
- `component-k8s-attributes-read`: 组件 k8s 属性列表读（含 json 转换，纯 DB）。
## Impact
- 代码：ComponentK8sAttributes 实体+repo；ComponentK8sAttributeService；ComponentK8sAttributeController。
- 数据：只读 component_k8s_attributes/tenant_service；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：空态对 7070 逐字节一致；json 转换由单测覆盖（添加属性需已部署组件 region，本期空态校准）。
