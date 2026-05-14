## ADDED Requirements

### Requirement: K8s 资源中心与命名空间资源管理

kuship-console SHALL 提供与 rainbond `console/views/team_resources.py:164-330` + `console/urls/team_resources.py` 等价的 K8s 资源中心 + 命名空间级资源 (CRD / ConfigMap / Secret 等) 管理能力，覆盖 8 个 controller / 9 个 endpoint，路径前缀 `/console/teams/{team_name}/regions/{region_name}/...`，路径变量 snake_case。所有 region 调用 MUST 走两个新接口 `ResourceCenterOperations` (4 method) 与 `NsResourceOperations` (6 method)。

业务规则：

- `POST /ns-resources` 与 `PUT /ns-resources/{name}` MUST 透传原始 request body（不强制 JSON 反序列化）+ Content-Type header（支持 `application/yaml` / `application/json` 等）给 region API
- `POST /ns-resources` MUST 透传 region 响应的 HTTP 状态码（如 201 / 409 / 422），不强制走 general_message 包装；body 透传 region 原始内容，标 `@SkipResponseWrapper`
- `PUT /ns-resources/{name}` MUST 强制返 200 + general_message 形状（与 rainbond view 行为一致）
- `DELETE /ns-resources/{name}` MUST 强制返 200 + general_message + `msg_show=删除成功`（与 rainbond view 行为一致）
- `GET /resource-center/pods/{pod_name}/logs` MUST 走流式响应（`StreamingResponseBody`），标 `@SkipResponseWrapper`；不能用普通 `Map` 返回（log size 可能数 GB）
- `GET /resource-center/pods/{pod_name}/logs` 的底层 `ResourceCenterOperations.getPodLogs` MUST 用专用长超时 RestClient（30 分钟），与默认 5s 超时区分
- `GET /resource-center/ws-info` MUST 不调 region，由 console 端 `WebSocketTokenService` 用 `JwtIssuer` 签发 300s 短时效 JWT（claims 含 user_id / team_name / region_name），并把 `RegionInfo.url` 的 scheme 替换为 ws / wss 后拼 wsUrl
- 所有 query 参数 MUST 透传给 region（不做白名单过滤）
- 路径前缀 `/v2/tenants/{tenant_name}/...` MUST 用 console 端 team_name（与 rainbond `team_resources.py` 一致），不查 namespace
- 写操作（NsResource POST/PUT/DELETE）MUST 通过 `@RequirePerm("manage_team_resource")` 或 fallback `app_create_perms`；读操作 `@RequirePerm("describe_team_app")`

#### Scenario: 命名空间资源类型查询

- **WHEN** `GET /console/teams/default/regions/rainbond/ns-resource-types`
- **THEN** kuship 调 region `GET /v2/tenants/default/ns-resource-types`
- **AND** 响应 200 + `data.bean`（K8s 资源类型列表）

#### Scenario: 上传 YAML ConfigMap

- **GIVEN** 用户提交 `application/yaml` 内容的 ConfigMap 定义
- **WHEN** `POST /console/teams/default/regions/rainbond/ns-resources?kind=ConfigMap` Content-Type=`application/yaml` body=`apiVersion: v1\nkind: ConfigMap\n...`
- **THEN** kuship 调 region `POST /v2/tenants/default/ns-resources?kind=ConfigMap` Content-Type 与 body 透传
- **AND** region 返 201，kuship 响应 status 201（不强制 200）+ region 原始 body（不包 general_message）

#### Scenario: YAML 校验失败状态码透传

- **GIVEN** YAML 格式错误，region 返 422
- **WHEN** POST 同上
- **THEN** kuship 响应 status 422 + region 原始错误 body

#### Scenario: PUT 强制 200

- **GIVEN** PUT 操作 region 返 200
- **WHEN** `PUT /console/teams/default/regions/rainbond/ns-resources/my-config?kind=ConfigMap` body=YAML
- **THEN** kuship 响应 200 + general_message 形状（`code/msg/msg_show/data.bean`）
- **AND** `data.bean` 含 region 返回的资源详情

#### Scenario: 资源名含点号的 DELETE

- **GIVEN** 资源名 `my.config.v1`
- **WHEN** `DELETE /console/teams/default/regions/rainbond/ns-resources/my.config.v1?kind=ConfigMap`
- **THEN** 路径变量正确解析含点号的 name
- **AND** kuship 调 region DELETE 透传
- **AND** 响应 200 + `msg_show=删除成功`

#### Scenario: Pod 详情查询

- **WHEN** `GET /console/teams/default/regions/rainbond/resource-center/pods/my-pod-abc123`
- **THEN** kuship 调 region `GET /v2/tenants/default/resource-center/pods/my-pod-abc123`
- **AND** 响应 200 + `data.bean`（Pod 详情）

#### Scenario: Workload 详情按 resource 类型

- **WHEN** `GET /console/teams/default/regions/rainbond/resource-center/workloads/Deployment/my-app?namespace=default`
- **THEN** kuship 调 region `GET /v2/tenants/default/resource-center/workloads/Deployment/my-app?namespace=default`
- **AND** 路径变量 `resource=Deployment` `name=my-app` 正确解析
- **AND** query `namespace` 透传

#### Scenario: 资源中心事件按 kind 过滤

- **WHEN** `GET /console/teams/default/regions/rainbond/resource-center/events?kind=Pod&type=warning`
- **THEN** kuship 调 region `GET /v2/tenants/default/resource-center/events?kind=Pod&type=warning`
- **AND** 响应 200 + `data.bean`（事件列表）

#### Scenario: Pod 日志流式输出

- **GIVEN** Pod `my-pod-abc123` 持续输出日志
- **WHEN** `GET /console/teams/default/regions/rainbond/resource-center/pods/my-pod-abc123/logs?follow=true&tail=100`
- **THEN** kuship 与 region 建立长连接，chunked transfer-encoding 流式回传
- **AND** 响应不走 general_message 包装（`@SkipResponseWrapper`）
- **AND** Content-Type=`text/plain`
- **AND** `?follow=true` 期间连接保持，直到 client 主动断开或 region 端关闭

#### Scenario: Pod 日志 region 5xx 透传

- **GIVEN** region 在流式开始前返 503
- **WHEN** 同上 endpoint
- **THEN** kuship 响应 503 + general_message 形状（流式未开始时仍走 RegionApiException 路径）

#### Scenario: WebSocket 连接信息签发

- **GIVEN** 用户 user_id=1 在 team `default` region `rainbond`，`RegionInfo.url=https://region-host:8443`
- **WHEN** `GET /console/teams/default/regions/rainbond/resource-center/ws-info`
- **THEN** 响应 200 + `data.bean = {ws_url:"wss://region-host:8443/v2/tenants/default/resource-center/pods/ws", token:"<JWT>", expires_in:300}`
- **AND** token 解码后 claims 含 `user_id=1` / `team_name=default` / `region_name=rainbond`
- **AND** 不调 region API（仅本地签发）

#### Scenario: WebSocket scheme 替换 http

- **GIVEN** `RegionInfo.url=http://region-dev:8443`
- **THEN** `ws_url=ws://region-dev:8443/v2/tenants/.../ws`（http→ws，非 wss）

#### Scenario: 与 cluster-extras / cluster-nodes 子 change 边界

- **GIVEN** 本 change 已落地
- **WHEN** 用户在集群层面查所有 namespace 事件
- **THEN** SHALL 走 `cluster-extras` 的 `ClusterEventsController`（`/cluster-events`），不走本 change 的 `/resource-center/events`
- **AND** 用户在团队层面查自己 namespace 的事件 SHALL 走本 change（`/resource-center/events`），不走 cluster-extras
- **AND** 节点级容器存储查询 SHALL 走 `cluster-nodes` 的 `/nodes/{name}/container`，不在本 change 范围

#### Scenario: 写操作权限校验

- **GIVEN** 普通团队成员（无 `manage_team_resource` 权限）
- **WHEN** `POST /ns-resources` 或 `PUT /ns-resources/{name}` 或 `DELETE /ns-resources/{name}`
- **THEN** 响应 403 + `msg_show` 含权限不足提示
- **AND** 同用户的 GET 请求正常（仅需 `describe_team_app`）
