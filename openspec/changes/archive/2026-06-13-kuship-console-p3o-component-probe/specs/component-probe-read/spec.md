## ADDED Requirements
### Requirement: 组件探针读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/probe`（对齐 `AppProbeView.get`）：third_party 或无 mode → 任意探针，否则按 mode；找到返回 `bean=probe.to_dict()`(15 字段) `msg_show=查询成功`，未找到返回 `code=404 msg=get probe error msg_show=探针不存在，您可能并未设置检测探针`。

#### Scenario: 有探针
- **WHEN** 组件已设置 readiness 探针且请求 mode=readiness
- **THEN** bean 为探针 15 字段 to_dict（is_used bool），与 7070 一致

#### Scenario: 无探针
- **WHEN** 组件无探针
- **THEN** body code=404、msg_show=探针不存在，您可能并未设置检测探针
