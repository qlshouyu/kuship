## ADDED Requirements

### Requirement: 组件详情读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/detail`（对齐 `AppDetailView.get` 非 vm/非 market 路径）：返回 `bean`={service, event_websocket_url, is_third}，其中 service = 组件全列 to_dict（datetime 空格格式）+ group_name + group_id + disk_cap，namespace 用 tenant.namespace；`msg_show=查询成功`。

#### Scenario: 返回组件详情
- **WHEN** 已认证用户请求镜像类组件详情
- **THEN** `bean.service` 含全列字段（与 7070 逐字节一致），`bean.event_websocket_url` 与 region.wsurl 推导一致，`bean.is_third=false`

#### Scenario: 未分组组件
- **WHEN** 组件无 service_group_relation
- **THEN** group_name="未分组"、group_id=-1
