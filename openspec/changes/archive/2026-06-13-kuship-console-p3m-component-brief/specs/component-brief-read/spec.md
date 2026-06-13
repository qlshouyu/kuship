## ADDED Requirements

### Requirement: 组件概览读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/brief`（对齐 `AppBriefView.get` 非 market 路径）：返回 `bean`=service.to_dict()（全列），`msg_show=查询成功`。

#### Scenario: 返回组件概览
- **WHEN** 已认证用户请求非 market 组件
- **THEN** bean 为 service 全列 to_dict（63 字段，与 7070 逐字节一致）
