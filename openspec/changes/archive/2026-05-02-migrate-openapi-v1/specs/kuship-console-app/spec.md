## ADDED Requirements

### Requirement: OpenAPI 双模式认证

kuship-console SHALL 实现 `OpenApiAuthFilter`，仅匹配 `/openapi/**` 路径，支持两种认证模式：
- **X-Internal-Token 头**：与环境变量 `INTERNAL_API_TOKEN` 比对，匹配后注入虚拟管理员 user（user_id=0, sysAdmin=true, nick_name="InternalAPI"）
- **Authorization 头**：作为 PAT 在 `user_access_key` 表查询匹配，加载对应 UserInfo 注入；要求该 user 的 `sys_admin = true` 才能通过

任一失败 SHALL 返回 401 + JSON `{"detail": "...", "code": 401}`（**与 console general_message 不同**）。

#### Scenario: X-Internal-Token 匹配通过

- **WHEN** 请求头含 `X-Internal-Token: <env value>`
- **THEN** kuship-console 注入虚拟 admin user 到 RequestContext
- **AND** 后续 endpoint 可读 `requestContext.getSysAdmin() == true`

#### Scenario: PAT 无效返回 401

- **WHEN** 请求头 `Authorization: invalid-token`
- **THEN** kuship-console 响应 401 状态码 + `{"detail": "Authentication failed", "code": 401}`
- **AND** 不进入 controller

#### Scenario: PAT 有效但用户非 sys_admin 返回 403

- **WHEN** PAT 有效但对应 user 的 `sys_admin = false`
- **THEN** 响应 403 + `{"detail": "Permission denied: requires sys_admin", "code": 403}`

### Requirement: OpenAPI 响应格式规范

kuship-console SHALL 让 `/openapi/**` 路径下的响应**绕过** `GeneralMessageResponseBodyAdvice` 包装：成功响应直接返回业务对象 JSON（不包 `{code, msg, msg_show, data}` 外壳），错误响应返回 `{"detail": "...", "code": <status>}` 格式 + HTTP 状态码与业务码一致。

#### Scenario: 成功响应直接返回业务对象

- **WHEN** 调 `GET /openapi/v1/regions`
- **THEN** 响应 body 是 `[{...region1}, {...region2}]` 数组
- **AND** 不包 console 风格的 `{code: 200, data: {list: [...]}}` 外壳

#### Scenario: 错误响应符合 OpenAPI 风格

- **WHEN** 调用任一 OpenAPI endpoint 抛出 `ServiceHandleException(404, "team not found", "团队不存在")`
- **THEN** HTTP 状态码 = 404
- **AND** body = `{"detail": "team not found", "code": 404}`

### Requirement: OpenAPI region 端点

kuship-console SHALL 实现 3 个 region 端点：`GET /openapi/v1/regions` 列表 / `GET /openapi/v1/regions/{region_id}` 详情 / `POST /openapi/v1/grctl/ip` 替换 region IP。复用第 5 阶段 `RegionInfo` entity。

#### Scenario: GET /openapi/v1/regions 列出 region

- **WHEN** 调 GET（带合法 Authorization）
- **THEN** 响应数组，每条含 region_id / region_name / region_alias / url / status

### Requirement: OpenAPI user 端点

kuship-console SHALL 实现 7 个 user 端点：`GET /openapi/v1/users` / `currentuser` / `users/{user_id}` GET/POST / `changepwd` POST / `users/{user_id}/{close,delete,changepwd}` POST。复用第 4 阶段 `UserInfo` entity。

#### Scenario: GET /openapi/v1/users 列表

- **WHEN** 调 GET 带 sys_admin token
- **THEN** 响应 list 含 user_id / nick_name / email / enterprise_id / is_active

#### Scenario: GET /openapi/v1/currentuser 当前用户

- **WHEN** 调 GET
- **THEN** 响应当前认证用户的 nick_name / email / enterprise_id / sys_admin 等字段

### Requirement: OpenAPI team 端点

kuship-console SHALL 实现 11 个 team 端点：teams 列表 / app_model / teams/resource / teams/{id}{,/regions,/certificates,/regions/{r}/{resource,overview,events/{eid}/logs}}。复用第 4 阶段 `Tenants` + 第 5 阶段 `RegionInfo`。

#### Scenario: GET /openapi/v1/teams 列出

- **WHEN** 调 GET
- **THEN** 响应 list 含 team_id / team_name / namespace / enterprise_id / limit_memory

#### Scenario: GET /openapi/v1/teams/{team_id} 详情

- **WHEN** 调 GET 带 team_id
- **THEN** 路径参数同时接受 `tenant_id` UUID 与 `id` 整型主键

### Requirement: OpenAPI enterprise 端点

kuship-console SHALL 实现 14 个 enterprise 端点：overview / configs / monitor 系列（query / query_range / series）/ resource overview / service overview / component memory overview / app rank / performance / instances monitor / monitor message。

#### Scenario: GET /openapi/v1/overview 企业概览

- **WHEN** 调 GET
- **THEN** 响应 `{enterprise_id, team_count, app_count, component_count, region_count}`

#### Scenario: GET /openapi/v1/monitor/query 透传 Prometheus

- **WHEN** 调 GET `?query=...&time=...`
- **THEN** kuship-console 调第 8 阶段 `MonitorOperations.query(...)` 透传响应

### Requirement: OpenAPI app 端点

kuship-console SHALL 实现 13 个 app 端点：list apps / apps_port / app-model/deploy / smart-deploy / import / chart / delete / helm_chart + 4 个 gray-release（占位）。

#### Scenario: GET /openapi/v1/teams/{team_id}/regions/{region}/apps 列出应用

- **WHEN** 调 GET
- **THEN** 响应 list 含 app_id / group_name / region_name + 每个 app 的 component_count

#### Scenario: POST /openapi/v1/teams/{team_id}/regions/{region}/app-model/deploy 部署应用模板

- **WHEN** 调 POST body 含 chart_url + values
- **THEN** kuship-console 调第 9 阶段 `HelmOperations.checkHelmApp / getYamlByChart` 透传

#### Scenario: 4 个 gray-release endpoint 占位返回

- **WHEN** 调 `/gray-release` / `/gray-ratio` / `/gray-rollback` / `/v1/gray-releases`
- **THEN** 响应占位数据；实际灰度规则由 hardening change 实现

### Requirement: OpenAPI admin + gateway + 其他端点

kuship-console SHALL 实现剩余 OpenAPI 端点：
- 2 admin endpoint：`GET /administrators` 列表 / `GET/PUT/DELETE /administrators/{user_id}` 详情
- 1 gateway endpoint：`GET /httpdomains` 列出全企业 HTTP 路由
- 5 announcement / appstore / groupapp / upload / config endpoint
- MCP sub-route：`/openapi/v1/mcp/**` 透传至第 11 阶段 MCPQueryController

#### Scenario: GET /openapi/v1/administrators 列出系统管理员

- **WHEN** 调 GET
- **THEN** 响应 list 仅含 sys_admin = true 的 user

#### Scenario: GET /openapi/v1/httpdomains 列出 HTTP 路由

- **WHEN** 调 GET
- **THEN** 响应当前企业全部 region 的 HTTP 路由列表（占位返回空数组，hardening 待补）

### Requirement: OpenAPI Swagger UI

kuship-console SHALL 集成 springdoc-openapi 暴露 OpenAPI 3 schema：
- `GET /openapi/v3/api-docs` JSON schema
- `GET /openapi/swagger-ui` 交互式 UI

如 springdoc 与 GraalVM-native 不兼容则 fallback 静态 `openapi.yaml`。dev profile 默认启用；prod profile 由配置 `kuship.openapi.docs.enabled` 控制（默认 false）。

#### Scenario: GET /openapi/v3/api-docs 返回 schema

- **WHEN** dev profile 调 GET
- **THEN** 响应 OpenAPI 3 schema JSON 含全部 50 endpoint 描述

#### Scenario: prod profile 默认禁用 Swagger UI

- **WHEN** prod profile 调 `/openapi/swagger-ui`
- **THEN** 响应 404（除非显式 `kuship.openapi.docs.enabled=true`）

### Requirement: OpenAPI 测试覆盖

kuship-console SHALL 提供 1 类集成测试覆盖 OpenAPI 核心：
1. `OpenApiAuthIntegrationTest`：
   - X-Internal-Token 通过 → 调 `/openapi/v1/regions` 返回 200
   - X-Internal-Token 错误 → 401 + `{"detail":..., "code":401}`
   - PAT 不存在 → 401
   - PAT 存在但 user 非 sys_admin → 403
   - 完整 round-trip 验证响应格式（不含 console 风格 `{code, msg, ...}` 外壳）

#### Scenario: OpenApiAuthIntegrationTest 验证 4 种认证场景

- **WHEN** 跑 `mvn -Dtest=OpenApiAuthIntegrationTest test`
- **THEN** 4 个用例全部通过

#### Scenario: 全套 ≥ 97 用例

- **WHEN** 跑 `mvn test`
- **THEN** 总用例数 ≥ 97（96 老 + 1 新 OpenAPI auth）全部通过
