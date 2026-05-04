## Context

第 12 阶段是 13 阶段路线倒数第二步——把 rainbond `openapi/v1/*` 共 ~50 endpoint 迁移到 kuship-console。这一层的存在意义：

- **grctl CLI** 通过 OpenAPI 操作集群（如 `grctl app list`、`grctl monitor query`）
- **第三方平台**（OA / DevOps 自动化）调用 console 触发部署
- **standalone 镜像**对接外部企业系统，前端不强求用 kuship-ui
- **GitOps 流水线** push 后自动 helm install / 灰度发布

OpenAPI 与 console UI 后端有 3 个本质差异：

1. **认证方式**：用 `X-Internal-Token` (内部服务) + `Authorization: <PAT>` (用户 token) 双模式，**不接受** console 的 `GRJWT` 前缀。
2. **响应格式**：直接返回业务对象 JSON，不包 `{code, msg, msg_show, data}`；错误用 `{detail: "...", code: 401}` 风格（HTTP 状态码也跟随）。
3. **权限**：仅 `sys_admin = true` 的用户可调用（rainbond 历史选择，开放 OpenAPI 默认对企业 admin）；无 `@RequirePerm` 复杂权限码。

涉及参考代码 `openapi/views/` 共 14 个 view 文件 ~2300 行 / 50 endpoint。

## Goals / Non-Goals

**Goals:**
- 落地 `/openapi/v1/**` 50 endpoint，认证 / 权限 / 响应格式 100% 与 rainbond 一致。
- 给 kuship-console 增加 **Swagger UI** + **OpenAPI 3 schema** 自动生成（前端 stub 客户端 / 第三方 SDK 生成更方便）。
- 复用既有 entity / repository（OpenAPI 是查询层别名，零新业务表）。
- 兼容 graalvm-native：所有反射 / 动态代理仅限 Spring 已知白名单；springdoc 必须验证可用，否则 fallback 静态 schema。

**Non-Goals:**
- 不实现 `/openapi/v2/**`（rainbond 自己用环境变量 `OPENAPI_V2=true` 才启用，留作 hardening）。
- 不实现 OAuth2 token 颁发流程（用现有 `UserAccessKey` PAT，第 4 阶段已落地）。
- 不重新设计 OpenAPI scope / quota / rate-limit（rainbond 原版无这些功能；prod 部署需在 nginx 层加）。
- 不实现 gray release 实际灰度（`apps/gray_release.py` 只占位，实际灰度需 region 端 mesh 支持，hardening 单独 change）。
- 不实现 `apps/apps.py` 的 chart 包目录解析（rainbond 用 yaml + zipfile 解析，依赖较重）。

## Decisions

### 决策 1：openapi 模块单独命名空间 + Filter 链分离

```
modules/openapi/
├── auth/        OpenApiAuthFilter + OpenApiAuthentication 注解
├── response/    OpenApiResult + OpenApiResponseBodyAdvice + @OpenApi 标记注解
├── exception/   OpenApiExceptionHandler（OpenAPI 风格 JSON 错误）
├── v1/          14 子域 controller
│   ├── enterprise/  EnterpriseConfigController + 14 endpoint
│   ├── team/        TeamController + 11 endpoint
│   ├── app/         AppController + 13 endpoint（含 gray release 4）
│   ├── user/        UserController + 7 endpoint
│   ├── admin/       AdminController + 2 endpoint
│   ├── region/      RegionController + 3 endpoint
│   ├── gateway/     GatewayController + 1 endpoint
│   ├── monitor/     MonitorController + 8 endpoint（独立子域，复用 console 第 8 阶段 MonitorOps）
│   └── 其他/         announcement / appstore / groupapp / upload / config / mcp
└── docs/        SpringDocConfig（@Bean OpenAPI customizer）
```

OpenAPI Filter 链：
```
TraceIdFilter → OpenApiAuthFilter（仅 matcher: /openapi/**）→ JwtAuthenticationFilter（其他路径）→ ...
```

`OpenApiAuthFilter` 内部：
1. 先尝试 `X-Internal-Token` 与 `INTERNAL_API_TOKEN` env 比对 → 注入虚拟 admin（user_id=0, sysAdmin=true）
2. 否则尝试 `Authorization` 头部 → 查询 `user_access_key` 表（`access_key = ?`）→ 拿到 user_id → 加载 UserInfo → sys_admin 必须为 true → 注入到 `RequestContext`
3. 都失败 → 401 + `{"detail": "Authentication failed", "code": 401}`

### 决策 2：响应格式按 controller 包分流

`@RestControllerAdvice GeneralMessageResponseBodyAdvice` 是全局的，会包装所有响应。OpenAPI controller 不应被它包装。两种解法：

- **方案 A（推荐）**：在 `GeneralMessageResponseBodyAdvice.supports()` 加包名过滤——`if (returnType.getDeclaringClass().getPackageName().startsWith("cn.kuship.console.modules.openapi"))` → 不包装
- **方案 B**：每个 OpenAPI controller 标 `@SkipResponseWrapper`（已有 misc 模块用），但 50 个端点逐一标繁琐

选 **方案 A**。同时新增 `OpenApiResponseBodyAdvice`（`@ConditionalOnPackage` 仅匹配 `cn.kuship.console.modules.openapi.v1`）做 OpenAPI 风格 String → byte 自动转换。

### 决策 3：错误响应风格 OpenApiExceptionHandler

新增 `@RestControllerAdvice(basePackages = "cn.kuship.console.modules.openapi")`：

```java
@ExceptionHandler(ServiceHandleException.class)
public ResponseEntity<Map<String, Object>> handle(ServiceHandleException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("detail", e.getMessage(), "code", e.getStatusCode()));
}
```

错误时 HTTP 状态码就是业务码（401/403/404/500），**不像 console 一律 200**。这是 rainbond OpenAPI 历史选择。

### 决策 4：Swagger UI 集成 springdoc

引入 `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0`。需验证：
- graalvm-native 兼容性（springdoc 在 native image 下能否生成 schema）
- 是否与 Spring Boot 4.0.6 / Jackson 3 兼容

如不兼容，fallback：
- 静态 `openapi.yaml` 文件（手写主要 endpoint），放在 `src/main/resources/static/openapi.yaml`
- Swagger UI 用 `swagger-ui-dist` CDN（前端 iframe 引入）

设 `kuship.openapi.docs.enabled=true` 仅 dev profile 默认启用，prod 默认关闭（不暴露 schema）。

### 决策 5：复用 console 业务逻辑，不重写

OpenAPI 与 console 调用同一批 entity / region API。例如 `ListAppsView` 与 console 的 `GroupController.list` 是同一份 SQL 查询，仅响应格式包装不同。OpenAPI controller 直接 `@Autowired` console 的 service：

```java
@RestController
@RequestMapping("/openapi/v1/teams/{team_id}/regions/{region_name}/apps")
public class OpenApiAppController {
    private final ServiceGroupRepository groupRepo;  // 复用 console 的 repo
    @GetMapping public List<Map<...>> list(...) { /* 直接读 + map → OpenAPI bean */ }
}
```

不重新实现业务逻辑。50 endpoint 中 80% 是这种"读 console entity → map 到不同字段名 / JSON 形状"的纯转换层。

### 决策 6：team_id 路径参数规则

OpenAPI 路径用 `team_id`（数据库内整型 ID 或 UUID 都接受），console 用 `team_name`（人可读）。两者映射：
- 路径 `/openapi/v1/teams/{team_id}` 接受 `team_id` 是 `Tenants.tenantId`（32-char UUID）
- 同时也兼容 `Tenants.id`（Integer 主键）—— 用 `findByTenantId().or(findById())` 双查询

### 决策 7：Monitor 端点透传 console 第 8 阶段 MonitorOperations

`/openapi/v1/monitor/{query,query_range,series}` 等 Prometheus 透传端点，直接用第 8 阶段已实现的 `MonitorOperations.query / queryRange`。零代码新增逻辑，仅 controller 层包装。

`/openapi/v1/monitor/{resource_over_view,service_overview,component_memory_overview,performance_overview}` 是聚合查询，需新写 `MonitorAggregator` service 调多个 `MonitorOperations.query` + 拼装。MVP 占位返回空数据，hardening 完成度。

### 决策 8：Gray Release 4 endpoint 占位

`/openapi/v1/teams/{}/regions/{}/apps/{app_id}/{gray-release,gray-ratio,gray-rollback}` + `/v1/gray-releases` 4 endpoint 全部占位返回。实际灰度需 region 端 service mesh + ingress 灰度规则，console 侧仅做 metadata 持久化（rainbond 也是这样）。本 change 不实现新 entity，留作 hardening change `add-gray-release`。

### 决策 9：app deploy 端点复用 helm install

`/openapi/v1/teams/{}/regions/{}/app-model/deploy` + `smart-deploy` —— 接受 chart_url + values，调用第 9 阶段 `HelmOperations.installChart`（已有）。OpenAPI controller 仅做参数转换。

### 决策 10：UserAccessKey 加 OpenAPI 标记

第 4 阶段 `user_access_key` 表已存 PAT。本 change 在 entity 上 **不新增列**，仅约定使用规则：
- access_key 长度 32-char 用于 OpenAPI 认证
- access_key 不带 `GRJWT` 前缀，直接当 Bearer token 用
- console 端 `UserAccessTokenController.create()` 已能创建；本 change 让 `OpenApiAuthFilter` 识别它

## Risks / Trade-offs

- **[Risk]** springdoc 与 GraalVM-native 不兼容 → Mitigation：先验证；不行则 fallback 静态 yaml。
- **[Risk]** 50 endpoint 体量大 → Mitigation：14 个子域并行实施；每个 controller 内部都是简单的 read-then-map 模式。
- **[Risk]** `RequestContext.userId/sysAdmin` 由 `JwtAuthenticationFilter` 写入；OpenAPI 走另一条 Filter，`RequestContext` 必须确保也注入 → 直接复用 `RequestContext` bean，`OpenApiAuthFilter` 写完之后 `RequestContextHolder.getRequest()` 仍可读。
- **[Risk]** `OpenApiAuthFilter` 与 `JwtAuthenticationFilter` 路径冲突 → Mitigation：filter `@WebFilter(urlPatterns = "/openapi/**")` 严格匹配，console 路径走 JWT filter。
- **[Risk]** `INTERNAL_API_TOKEN` env 未设时所有内部调用失败 → Mitigation：dev profile 默认 `INTERNAL_API_TOKEN=internal-dev-token-do-not-use-in-prod`；prod 必须显式注入。
- **[Risk]** Gray release 占位前端有空白页 → Mitigation：在前端文档登记 known-limitation；hardening change 实现。
- **[Trade-off]** Monitor 聚合端点占位空响应 → 影响监控大盘，但 99% 用户不通过 OpenAPI 看监控；console 直接看；hardening 单独写聚合逻辑。

## Migration Plan

阶段 A：openapi 模块基建——OpenApiAuthFilter / OpenApiResult / OpenApiResponseBodyAdvice / OpenApiExceptionHandler / @OpenApi 注解
阶段 B：6 高优先级子域 controller——region / user / team / app / enterprise / admin（28 endpoint）
阶段 C：4 中优先级子域——monitor / gateway / gray-release / app-deploy（13 endpoint）
阶段 D：4 低优先级子域——announcement / appstore / groupapp / upload / config / mcp（9 endpoint）
阶段 E：springdoc 集成 + Swagger UI 配置 + 验证（如失败 fallback 静态 yaml）
阶段 F：编译 + 1 类集成测试（OpenApiAuth + 1 endpoint roundtrip）+ 文档 + openspec validate

## Open Questions

- **(Q1)** `/openapi/swagger-ui` 路径是否要权限校验？默认 dev profile 公开，prod profile 用 `kuship.openapi.docs.enabled` 关闭。
- **(Q2)** `INTERNAL_API_TOKEN` 命名是否改为 `KUSHIP_INTERNAL_TOKEN`？保持向后兼容用原名。
- **(Q3)** OpenAPI v2 占位 controller 是否需要？rainbond 仅在 env=true 时启用；本阶段不实现 v2。
