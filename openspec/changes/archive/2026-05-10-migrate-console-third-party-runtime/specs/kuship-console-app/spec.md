## ADDED Requirements

### Requirement: 第三方组件 endpoint 与健康探针管理

kuship-console SHALL 提供与 rainbond `console/views/app_create/source_outer.py:ThirdPartyAppPodsView,ThirdPartyHealthzView` 等价的第三方组件运行时管理能力，覆盖 6 endpoint（其中 4 个挂在新建的 `ThirdPartyEndpointsController`，2 个挂在新建的 `ThirdPartyHealthController`），路径与 rainbond `console/urls/__init__.py:494,576` 严格对齐。所有 region 调用 MUST 走新接口 `ThirdPartyServiceOperations`（6 method），由 `ThirdPartyServiceOperationsImpl @Primary` 实现。

业务规则：

- 路径段 `tenant_name` MUST 替换为 `Tenants.namespace`（缺失回退 `tenant_name`）
- POST/PUT/DELETE endpoints 三个 region 调用 MUST 在 HTTP header 带 `Resource-Validation: true`（与 rainbond `_set_headers(token, resource_validation="true")` 一致）；GET endpoints / GET health / PUT health 不带该 header
- 所有 endpoint MUST 在 service 层先校验组件类型：取 `tenant_service` 行，`serviceSource` 非 `third_party` 时抛 `ServiceHandleException(400, "service is not a third-party service", "组件不是第三方组件")`，比 rainbond 严一档
- URL 段 `third_party`（下划线）与 `3rd-party`（连字符 + 数字简写）拼写不一致 MUST 保留（与 rainbond URL 一致），不在本 change 修复
- 读端点 `@RequirePerm("describe_team_app")`，写端点 `@RequirePerm("manage_team_app")` 或 fallback `app_create_perms`
- region 异常透传 `RegionApiException` + `GlobalExceptionHandler` 自动映射

#### Scenario: 查询 endpoint 列表

- **GIVEN** 第三方组件 svc1（serviceSource=third_party）
- **WHEN** `GET /console/teams/default/apps/svc1/third_party/pods`
- **THEN** kuship 调 region `GET /v2/tenants/<ns>/services/svc1/endpoints`（不带 Resource-Validation header）
- **AND** 响应 200 + `data.bean` 含 endpoint list

#### Scenario: 添加单条 endpoint

- **WHEN** `POST /console/teams/default/apps/svc1/third_party/pods` body=`{"address":"10.0.0.1:80","is_online":true}`
- **THEN** kuship 调 region `POST /v2/tenants/<ns>/services/svc1/endpoints` body 透传 + header `Resource-Validation: true`
- **AND** 响应 200/201 + `data.bean`

#### Scenario: 批量添加 endpoints

- **WHEN** `POST` body=`{"endpoints":[{"address":"10.0.0.1:80","is_online":true},{"address":"10.0.0.2:80","is_online":true}]}`
- **THEN** kuship 调 region 同 URL，body 含 `endpoints` 数组透传，header 带 Resource-Validation

#### Scenario: 更新 endpoint 在线状态

- **WHEN** `PUT /console/teams/default/apps/svc1/third_party/pods` body=`{"ep_id":"<id>","is_online":false}`
- **THEN** kuship 调 region `PUT` 同 URL，body 透传 + Resource-Validation header

#### Scenario: 删除 endpoint

- **WHEN** `DELETE /console/teams/default/apps/svc1/third_party/pods` body=`{"ep_id":"<id>"}`
- **THEN** kuship 调 region `DELETE` 同 URL with body + Resource-Validation header

#### Scenario: 内部组件调用第三方 endpoint API

- **GIVEN** 普通内部组件 svc2（serviceSource != "third_party"）
- **WHEN** 任一 `/third_party/pods` endpoint
- **THEN** 响应 400 + `msg_show=组件不是第三方组件`
- **AND** 不发起 region 调用

#### Scenario: 查询健康探针配置

- **GIVEN** 第三方组件 svc1
- **WHEN** `GET /console/teams/default/apps/svc1/3rd-party/health`
- **THEN** kuship 调 region `GET /v2/tenants/<ns>/services/svc1/3rd-party/probe`
- **AND** 响应 200 + `data.bean`（含 mode/scheme/path/port/period/timeout 等字段）

#### Scenario: 设置健康探针配置

- **WHEN** `PUT /console/teams/default/apps/svc1/3rd-party/health` body=`{"mode":"tcp","port":80,"period":30,"timeout":3}`
- **THEN** kuship 调 region `PUT /v2/tenants/<ns>/services/svc1/3rd-party/probe` body 透传
- **AND** 响应 200 + general_message

#### Scenario: 不存在的组件

- **WHEN** 任一 endpoint 路径变量 `service_alias=no-such-svc`
- **THEN** 响应 404 + `msg_show=组件不存在`
- **AND** 不发起 region 调用

#### Scenario: 写端点权限校验

- **GIVEN** 普通团队成员（无 `manage_team_app` 权限）
- **WHEN** `POST /third_party/pods` 或 `PUT /3rd-party/health`
- **THEN** 响应 403
- **AND** 同用户 GET 端点正常（`describe_team_app` 通过）
