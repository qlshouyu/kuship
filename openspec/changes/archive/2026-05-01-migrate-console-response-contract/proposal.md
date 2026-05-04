## Why

`init-kuship-console` 已经把 Spring Boot 4 工程骨架立起来，但留下 5 个共享契约缺口：响应自动包装、异常映射、JWT 认证、租户上下文、分页适配。这 5 项每一项都是**横切关注点**——后续 12 个业务迁移 change 里的每个 controller 都依赖它们。如果不在第二步集中落定，每个业务 change 都会反复重写这 5 项，且不可避免出现实现漂移（响应字段顺序不一致、异常映射 code 不一致、JWT 解析逻辑碎片化等）。本 change 的目的是让所有后续业务 controller 只关心业务逻辑，把契约层一次性钉死。

## What Changes

- 引入 `GeneralMessageResponseBodyAdvice`：所有 `@RestController` 返回值自动包装成 `{code,msg,msg_show,data:{bean,list,...}}`；提供 `@SkipResponseWrapper` 转义阀（用于 SSE / 文件下载）
- 引入 `GlobalExceptionHandler` (`@RestControllerAdvice`)：将 `ServiceHandleException` / 参数校验失败 / 反序列化失败 / 认证 / 授权 / 兜底 6 类异常映射为统一 `general_message` 形状；HTTP 状态码与业务 `code` 解耦（业务 code 走响应体，HTTP 一律 200，与 rainbond-console 保持一致）
- 引入 `JwtAuthenticationFilter` 与 `JwtTokenService`：兼容 `djangorestframework-jwt 1.11.0`，**接受 `GRJWT` 和 `jwt` 两种 Authorization 前缀**（与 Django 端 `views/base.py` 同行为），HS256 算法，payload schema 与 Django 完全一致（`user_id`、`username`/`nick_name`、`email`、`exp`、`orig_iat`），SECRET_KEY 通过环境变量与 Django 一侧同源
- 引入 `RequestContext` (`@RequestScope`) 与 `TenantContextInterceptor`：从 path variable 提取 `team_name` / `region_name`，写入请求上下文供下游业务层访问
- 引入 `PageRequestAdapter` / `PageResponse`：把 query 参数 `page`/`page_size`（一基！page=1 起算）转 Spring `Pageable`，把 `Page<T>` 转回 `{list,total,page,page_size}` 形状
- 引入 `TraceIdFilter`：每个请求生成 UUID traceId 写入 MDC + 响应头 `X-Trace-Id`；异常处理日志带 traceId 便于排查
- 升级 `SecurityConfig`：从 `permitAll` 升级为「除 `/console/login`、`/console/oauth/**`、`/console/healthz`、`/actuator/**` 外，所有 `/console/**` 与 `/openapi/**` 需要 JWT」；自定义 `AuthenticationEntryPoint` / `AccessDeniedHandler` 让 401/403 也走 `general_message` 形状
- 增加契约级测试：`ContractDemoController`（仅测试用）覆盖响应包装/异常映射/JWT/分页/traceId 五条主线，以及 trailing slash 在新 advice 下仍然兼容
- 更新 `kuship-console/CLAUDE.md` 与 `README.md`：响应/异常/JWT/分页契约从「占位」升级为「就绪」；README 增加 curl 验证 JWT 工作流的范例
- **明确不进入此 change**：业务 controller / entity / Region API client / RBAC 权限矩阵 / CORS / msg_show 国际化 / `enterprise_id` 反查（待 account/team 模块）

## Capabilities

### New Capabilities

无。本 change 不引入新 capability，所有变更都落在已存在的 `kuship-console-app` 之上。

### Modified Capabilities

- `kuship-console-app`：在 init change 留下的「Spring Security 占位」与「响应包装工具类」基础上，新增 6 项契约（自动响应包装、全局异常映射、JWT 认证、请求上下文、分页适配、TraceId）；同时把两条占位 requirement 升级为正式契约。

## Impact

- **代码新增**：`common/response/{ResponseBodyAdvice, SkipResponseWrapper}`、`common/exception/GlobalExceptionHandler`、`common/security/{JwtAuthenticationFilter, JwtTokenService, JwtClaims, AuthEntryPoint, ForbiddenHandler}`、`common/context/{RequestContext, TenantContextInterceptor}`、`common/page/{PageRequestAdapter, PageResponse}`、`common/trace/TraceIdFilter`
- **代码修改**：`config/SecurityConfig`（permitAll → JWT required）、`config/WebMvcConfig`（注册 TenantContextInterceptor + 路径 patterns）、`HealthzController`（保持 permitAll，验证 advice 自动包装也对它生效）
- **配置新增**：`application.yaml` 增加 `kuship.security.jwt.secret-key` / `auth-header-prefixes` / `expiration-leeway-seconds` 占位，`application-local.yaml` 给本地默认值
- **API 契约**：从此所有 `/console/**` 与 `/openapi/**` 请求必须带 `Authorization: GRJWT <token>`（或兼容 `jwt <token>`）；响应形状全局统一
- **依赖**：不新增 Maven 依赖（jjwt 已在 init change 引入；spring-security 已在）
- **数据库**：本 change 不读写任何业务表；user 表的反查留给 account/team 模块
- **后续 change 解锁**：`migrate-console-region-client`、`migrate-console-account-team` 及之后所有业务 change，都建立在本 change 提供的契约层之上
- **运维影响**：部署时必须设置 `JWT_SECRET_KEY` 环境变量（与 rainbond-console Django 进程同源）
