## ADDED Requirements

### Requirement: Springdoc OpenAPI 集成

构建系统 SHALL 引入 `org.springdoc:springdoc-openapi-starter-webmvc-ui`（≥ 2.7.0），与 Spring Boot 4.0.6 / Jackson 3 兼容；该依赖 SHALL 仅作 main scope 不进 test scope。

#### Scenario: 启动时自动装配 Springdoc
- **WHEN** Spring Boot 应用以 dev 或 local profile 启动
- **AND** `kuship.openapi.docs.enabled=true`
- **THEN** Springdoc 上下文 SHALL 被注册
- **AND** 控制台日志 SHALL 输出 `Springdoc OpenAPI 3 endpoint registered at /openapi/v3/api-docs`

#### Scenario: 不影响 console UI 后端
- **WHEN** Springdoc 启用
- **AND** 客户端请求 `/console/teams/...` 等已有端点
- **THEN** 响应 shape SHALL 仍是 console 的 general_message `{code, msg, msg_show, data}`
- **AND** Springdoc 不应注册任何 `/console/**` 路径的 servlet handler

### Requirement: OpenAPI v1 文档自动生成

Springdoc SHALL 仅扫描 `cn.kuship.console.modules.openapi.v1.**` 包下的 controller，并暴露 OpenAPI 3 JSON 在 `/openapi/v3/api-docs`、Swagger UI 在 `/openapi/swagger-ui/index.html`。

#### Scenario: JSON 端点返回 47 个 v1 端点
- **WHEN** 客户端 GET `/openapi/v3/api-docs`
- **THEN** 响应 SHALL 是 `application/json`
- **AND** JSON `paths` 字段 SHALL 包含 `/openapi/v1/regions`、`/openapi/v1/users` 等已知 v1 端点
- **AND** JSON `paths` 字段 SHALL 不包含任何 `/console/**` 路径

#### Scenario: Swagger UI 渲染端点列表
- **WHEN** 客户端 GET `/openapi/swagger-ui/index.html`
- **THEN** 响应 SHALL 是 `text/html`
- **AND** HTML 体内 SHALL 包含 `swagger-ui` 关键字 + JavaScript bootstrap 引用 `/openapi/v3/api-docs`

### Requirement: 双鉴权 securityScheme

`SpringDocConfig` SHALL 在 OpenAPI 文档中显式声明两种 SecurityScheme：`InternalToken`（apiKey in header `X-Internal-Token`）与 `BearerAuth`（http bearer scheme）；用户 SHALL 能在 Swagger UI 内通过 Authorize 按钮输入凭据并 Try-It-Out。

#### Scenario: JSON 含两个 securityScheme
- **WHEN** 客户端 GET `/openapi/v3/api-docs`
- **THEN** JSON `components.securitySchemes` SHALL 包含 `InternalToken`
- **AND** `components.securitySchemes` SHALL 包含 `BearerAuth`
- **AND** `InternalToken.type` 等于 `apiKey`，`in` 等于 `header`，`name` 等于 `X-Internal-Token`
- **AND** `BearerAuth.type` 等于 `http`，`scheme` 等于 `bearer`

#### Scenario: Try-It-Out 调用 region 端点
- **WHEN** 用户在 Swagger UI 通过 Authorize 输入有效 PAT
- **AND** Try-It-Out 调用 `GET /openapi/v1/regions`
- **THEN** 浏览器 SHALL 携带 `Authorization: Bearer <pat>` 发请求
- **AND** 后端 SHALL 通过 OpenApiAuthFilter PAT 模认证返回 200

### Requirement: dev/prod 启用策略

OpenAPI 文档 SHALL 在 dev、local、contract-test profile 默认开启，prod profile 默认关闭；可通过 `kuship.openapi.docs.enabled` 配置项一键覆盖；该开关 SHALL 同时控制 `springdoc.api-docs.enabled` 与 `springdoc.swagger-ui.enabled`。

#### Scenario: prod profile 默认关闭
- **WHEN** 应用以 `--spring.profiles.active=prod` 启动且未显式设 `kuship.openapi.docs.enabled`
- **THEN** GET `/openapi/v3/api-docs` SHALL 返回 404 或 403
- **AND** GET `/openapi/swagger-ui/index.html` SHALL 返回 404 或 403

#### Scenario: 运维通过环境变量临时开启 prod 文档
- **WHEN** 应用以 prod profile 启动
- **AND** 环境变量 `KUSHIP_OPENAPI_DOCS_ENABLED=true`
- **THEN** GET `/openapi/v3/api-docs` SHALL 返回 200 + JSON

### Requirement: OpenApiAuthFilter skip 列表

`OpenApiAuthFilter` SHALL 在路径匹配前先检查内置 skip 列表（`/openapi/v3/api-docs/**`、`/openapi/swagger-ui/**`、`/openapi/swagger-config`），命中时直接放行，不进入 InternalToken / PAT 鉴权流程。

#### Scenario: 未鉴权用户可访问 JSON
- **WHEN** 客户端 GET `/openapi/v3/api-docs` 不带任何 Authorization 头 / X-Internal-Token 头
- **THEN** OpenApiAuthFilter SHALL 让请求通过
- **AND** 响应 SHALL 是 200 + JSON

#### Scenario: 未鉴权用户访问业务端点仍被拦截
- **WHEN** 客户端 GET `/openapi/v1/regions` 不带任何鉴权头
- **THEN** OpenApiAuthFilter SHALL 拦截
- **AND** 响应 SHALL 是 401 + `{"detail":"...","code":401}`

### Requirement: GraalVM Native 兼容

OpenAPI Swagger UI SHALL 在 GraalVM native binary 下可访问；构建配置 SHALL 显式注册 swagger-ui webjar 静态资源 + Springdoc 反射 hint；提供 `-Pnative-no-swagger` profile 兜底剥离 webjar。

#### Scenario: native binary dev profile 启动后 UI 可访问
- **WHEN** 用户 `bash scripts/native-build.sh` 产 binary
- **AND** 用 `./target/kuship-console -Dspring.profiles.active=dev` 启动
- **THEN** GET `http://localhost:8080/openapi/swagger-ui/index.html` SHALL 返回 200 + 含 swagger-ui 字串的 HTML
- **AND** GET `/openapi/v3/api-docs` SHALL 返回 200 + 有效 JSON

#### Scenario: native-no-swagger profile 体积更小
- **WHEN** 用户 `mvn -Pnative,native-no-swagger package`
- **THEN** 产出 binary SHALL 不包含 `META-INF/resources/webjars/swagger-ui/**` 资源
- **AND** binary 体积 SHALL 至少减少 5MB（vs. 含 swagger-ui 的 native binary）

### Requirement: 文档与运维指引

`kuship-console/CLAUDE.md` SHALL 新增"OpenAPI 文档"段落，覆盖：访问路径、启用条件、安全注释规范、Production Hardening 警示、`-Pnative-no-swagger` 使用方式、注释化推迟到 `enrich-openapi-annotations` change 的说明。

#### Scenario: 文档段落齐全
- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档 SHALL 包含 "OpenAPI 文档（add-openapi-swagger-ui）" 标题
- **AND** 段落 SHALL 包含 dev / prod 启用矩阵、安全方案、native 兼容说明
