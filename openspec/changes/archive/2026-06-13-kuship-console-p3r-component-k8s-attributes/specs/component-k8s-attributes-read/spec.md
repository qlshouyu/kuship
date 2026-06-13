## ADDED Requirements
### Requirement: 组件 k8s 属性列表读
系统 SHALL 提供 `GET /console/teams/{tenantName}/components/{serviceAlias}/k8s-attributes`（对齐 `ComponentK8sAttributeListView.get`）：返回 `list`=component_k8s_attributes.to_dict（8 字段），save_type=="json" 时 attribute_value 经 JSON 解析（对象→[{key,value}]、数组/字符串透传），`msg_show=查询成功`。

#### Scenario: json 对象属性
- **WHEN** 属性 save_type=json 且值为 JSON 对象
- **THEN** attribute_value 转为 [{key,value}] 列表

#### Scenario: 无属性
- **WHEN** 组件无 k8s 属性
- **THEN** list=[]
