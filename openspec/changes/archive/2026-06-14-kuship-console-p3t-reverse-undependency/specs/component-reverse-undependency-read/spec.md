## ADDED Requirements
### Requirement: 可被依赖但未依赖的组件列表读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/dependency-reverse`（对齐 `AppDependencyReverseView.get`）：返回 `list`=team region 组件(排除自己+已反向依赖)的 dep_info(6 字段)，支持 search_key+condition 过滤、本应用在前、分页，`total`=过滤后总数，`msg_show=查询成功`。

#### Scenario: 单组件
- **WHEN** 团队仅有该组件
- **THEN** list=[]、total=0

#### Scenario: search 过滤
- **WHEN** 提供 search_key+condition=service_name
- **THEN** 仅返回 service_cname 含关键字的组件
