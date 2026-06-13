## ADDED Requirements

### Requirement: 组件持久化列表读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/volumes`（对齐 `AppVolumeView.get` 非 config 路径）：返回 `list`=[volume.to_dict + status + dep_services + first]，volume 排除 config-file，status 按 create_status(!=complete→not_bound；complete→region READY→bound)，首项 first=true，`msg_show=查询成功`。

#### Scenario: 未部署组件的持久化
- **WHEN** 已认证用户请求且组件未部署(create_status!=complete)且有一个持久化
- **THEN** list 含该 volume(15 字段+status=not_bound+dep_services=null+first=true)，与 7070 一致

#### Scenario: 无持久化
- **WHEN** 组件无 volume
- **THEN** list=[]
