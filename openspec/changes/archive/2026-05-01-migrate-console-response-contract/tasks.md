## 1. 配置项与文档约定

- [x] 1.1 在 `application.yaml` 增加 `kuship.security.jwt.*` 配置段：`secret-key: ${JWT_SECRET_KEY:}`、`auth-header-prefixes: GRJWT,jwt`、`algorithm: HS256`、`leeway-seconds: 0`（无 `expose-failure-reason` —— 失败原因始终暴露在 `msg` 字段，`msg_show` 永远是统一文案）
- [x] 1.2 在 `application-local.yaml` 与 `application-local.yaml.example` 增加 `JWT_SECRET_KEY` 占位（local 用 dev 默认值，example 用 `<your-secret-key>`）。注意：401 失败原因始终暴露（决议），不再使用 `expose-failure-reason` 配置项，无需此开关
- [x] 1.3 在 `application.yaml` 增加 `kuship.pagination.default-page-size: 10`、`max-page-size: 200`
- [x] 1.4 创建 `cn.kuship.console.common.security.JwtProperties`（`@ConfigurationProperties("kuship.security.jwt")`）映射上述配置
- [x] 1.5 创建 `cn.kuship.console.common.page.PaginationProperties`（`@ConfigurationProperties("kuship.pagination")`）

## 2. 响应自动包装

- [x] 2.1 创建 `cn.kuship.console.common.response.SkipResponseWrapper` 注解：`@Target({ElementType.METHOD, ElementType.TYPE}) @Retention(RUNTIME)`
- [x] 2.2 创建 `cn.kuship.console.common.response.GeneralMessageResponseBodyAdvice implements ResponseBodyAdvice<Object>`：
  - `supports()` 排除标注了 `@SkipResponseWrapper` 的方法/类、actuator package、`ResponseEntity<Resource>`、`StreamingResponseBody`
  - `beforeBodyWrite()` 处理：`ApiResult` 原样返回；`Page<T>` → `PageResponse` → `GeneralMessage.okWithExtras`；`List<T>` → `okList`；`String` → 包成 `{"value": str}` 后 `ok(Map)`；其他 POJO 用 Jackson 转 `Map<String,Object>` 后 `ok(Map)`
- [x] 2.3 在 `WebMvcConfig`（或新建 `JsonConfig`）注册 advice 让 Spring 扫到（`@ControllerAdvice` 注解通常即可）
- [x] 2.4 单元测试 `GeneralMessageResponseBodyAdviceTest`：覆盖 POJO / List / Page / ApiResult 幂等 / String / @SkipResponseWrapper 五条路径

## 3. 全局异常映射

- [x] 3.1 创建 `cn.kuship.console.common.exception.GlobalExceptionHandler`（`@RestControllerAdvice`），按 design 决策实现 6 类映射：
  - `@ExceptionHandler(ServiceHandleException.class)` → 透传 code/msg/msgShow，HTTP 200
  - `@ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})` → `code=400`、`msg_show=参数校验失败`、`data.bean.errors` 列出字段级错误
  - `@ExceptionHandler(HttpMessageNotReadableException.class)` → `code=400`、`msg_show=请求体解析失败`
  - `@ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})` → `code=400`，`msg` 含字段名
  - `@ExceptionHandler(Exception.class)` 兜底 → `code=500`、`msg_show=系统异常`、`data.bean.trace_id=<MDC traceId>`，输出 ERROR 日志
- [x] 3.2 单元测试 `GlobalExceptionHandlerTest`：每种异常类型一个用例，断言响应形状与 HTTP 状态码

## 4. JWT 认证

- [x] 4.1 创建 `cn.kuship.console.common.security.JwtClaims` record：`Long userId`、`String username`、`String email`、`Instant issuedAt`、`Instant expiresAt`、原始 `Map<String,Object>` 其他 claims
- [x] 4.2 创建 `cn.kuship.console.common.security.JwtTokenService`：
  - `decode(String token) -> JwtClaims`：用 jjwt 0.12.x，HS256，校验签名与 exp，leeway 来自配置
  - `encode(JwtClaims claims) -> String`：仅供测试与后续业务 change 使用（本 change 不暴露登录端点）
  - 启动时校验：非 local profile 下 `secret-key` 必须非空，否则 `IllegalStateException`
- [x] 4.3 创建 `cn.kuship.console.common.security.JwtAuthenticationFilter extends OncePerRequestFilter`：
  - 解析 `Authorization` header，遍历配置的前缀列表（不区分大小写）
  - 匹配到合法 token 后 `decode` → 写入 `SecurityContextHolder`（一个简单的 `UsernamePasswordAuthenticationToken(username, null, emptyList())`）→ 写入 `RequestContext.userId/username/email`
  - 解析失败：什么都不做，让请求继续；后续若该路径需要认证，由 SecurityFilterChain 抛 `AuthenticationException` 由 EntryPoint 处理
- [x] 4.4 创建 `cn.kuship.console.common.security.GeneralMessageAuthenticationEntryPoint implements AuthenticationEntryPoint`：
  - 写 HTTP 401 + Content-Type=application/json + body 为 `general_message` 形状
  - `msg` 字段始终包含具体原因（`token expired`/`invalid signature`/`missing token`/`malformed token`）；`msg_show` 始终为统一文案 `"未认证或 token 失效"`（不分 profile）
- [x] 4.5 创建 `cn.kuship.console.common.security.GeneralMessageAccessDeniedHandler implements AccessDeniedHandler`：写 HTTP 403 + general_message
- [x] 4.6 升级 `SecurityConfig`：
  - `authorizeHttpRequests`：`requestMatchers("/actuator/**", "/console/login", "/console/oauth/**", "/console/healthz", "/console/healthz/").permitAll()`、`anyRequest().authenticated()`
  - `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
  - `exceptionHandling(e -> e.authenticationEntryPoint(...).accessDeniedHandler(...))`
- [x] 4.7 单元测试 `JwtAuthenticationFilterTest`：合法 token / 缺失 / 过期 / 篡改签名 / 错误前缀 五用例
- [x] 4.8 单元测试 `JwtTokenServiceStartupTest`：覆盖非 local profile + 空 SECRET_KEY 应启动失败

## 5. 请求上下文（RequestContext）

- [x] 5.1 创建 `cn.kuship.console.common.context.RequestContext`：`@Component @Scope(value="request", proxyMode=TARGET_CLASS)`，字段 `Long userId`、`String username`、`String email`、`String teamName`、`String regionName`、`Long enterpriseId`，提供 setter/getter
- [x] 5.2 创建 `cn.kuship.console.common.context.TenantContextInterceptor implements HandlerInterceptor`：
  - `preHandle()` 从 `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE` 取 path variables
  - 若有 `team_name` / `region_name` → 写入 `RequestContext`
- [x] 5.3 在 `WebMvcConfig` 通过 `addInterceptors` 注册 `TenantContextInterceptor`，`addPathPatterns("/console/**", "/openapi/**")`
- [x] 5.4 修改 `JwtAuthenticationFilter`（4.3）：解析成功时也写入 `RequestContext.userId/username/email`
- [x] 5.5 单元测试 `TenantContextInterceptorTest`：用 `MockMvc` + 临时测试 controller，断言 `team_name` 被注入

## 6. 分页适配

- [x] 6.1 已实测（决议）：rainbond-console 分页响应形状为 `data.list` + `data.bean.total`（见 `service_share.py:73`、`app_upgrade.py:67`、`app_config/app_env.py:131`、`app_config/app_domain.py:1117` 等），不输出 `page`/`page_size`；kuship-ui `HttpTable/index.js:95`、`TcpTable/index.js:88`、`EnvironmentVariable/index.js:742`、`ClusterMgtInfo/index.js:433` 全部按 `data.bean.total` 读取。本 change 严格按此形状落地
- [x] 6.2 创建 `cn.kuship.console.common.page.PageRequestAdapter`：静态方法 `toPageable(int page, int pageSize)` + 校验（page>=1、page_size>=1 且 <=max），失败抛 `IllegalArgumentException`（被 `@RestControllerAdvice` 映射为 400）；提供注解 `@PageableDefault` 风格的方法参数解析器（可选，初版可让 controller 显式 `@RequestParam`）
- [x] 6.3 不引入 `PageResponse<T>` 包装类型（决议）。响应输出由 `GeneralMessageResponseBodyAdvice` 在检测到 `Page<T>` 时直接构造 `bean={"total": page.totalElements}` + `list=page.content`；业务 controller 直接 `return page;` 即可
- [x] 6.4 在 `GeneralMessageResponseBodyAdvice`（2.2）中确认 `Page<T>` 分支输出 `data.list + data.bean.total`，**不输出顶层 `page` / `page_size`**；专门用例覆盖
- [x] 6.5 单元测试 `PaginationTest`：page=1/page=N/page=0/page=-1/page_size=201/page_size=10 六种边界（输入校验）+ Page<T> 输出形状（`data.list` 与 `data.bean.total` 严格匹配，不含 `data.page` / `data.page_size`）

## 7. TraceId

- [x] 7.1 创建 `cn.kuship.console.common.trace.TraceIdFilter extends OncePerRequestFilter`：每个请求 `UUID.randomUUID().toString()` 写入 MDC（key: `traceId`） + 响应头 `X-Trace-Id`；filter order 设为最高优先级（`@Order(Ordered.HIGHEST_PRECEDENCE)`）让 SecurityFilter 也能拿到
- [x] 7.2 在 `logback-spring.xml`（新建于 `src/main/resources`）配置 console pattern 包含 `%X{traceId:-}`
- [x] 7.3 修改 `GlobalExceptionHandler` 兜底分支（3.1）：从 MDC 取 traceId 注入 `data.bean.trace_id`
- [x] 7.4 单元测试 `TraceIdFilterTest`：两个连续请求 traceId 不同；响应头与 MDC 值一致

## 8. 测试用 ContractDemoController

- [x] 8.1 在 `src/test/java/cn/kuship/console/contract/` 新建 `ContractDemoController`：暴露多个 endpoint 覆盖 5 类返回（POJO/List/Page/ApiResult/String）+ 主动抛 5 类异常 + `@SkipResponseWrapper` 一例
- [x] 8.2 创建 `ContractIntegrationTest` 用 `@SpringBootTest(webEnvironment=RANDOM_PORT)` 启动完整应用（不连数据库可用 `@DynamicPropertySource` 关闭 datasource autoconfigure 或加 `@TestPropertySource` 用 H2/无 datasource）
- [x] 8.3 ContractIntegrationTest 用例覆盖 spec 中的所有 ADDED Scenario：自动包装、异常映射、JWT（合法/缺失/过期/篡改）、分页边界、traceId 透出
- [x] 8.4 回归测试：`/console/healthz` 与 `/console/healthz/` 在新 SecurityConfig 下仍 permitAll 且响应体形状不变

## 9. SecurityConfig / WebMvcConfig 升级

- [x] 9.1 修改 `SecurityConfig`：注入 `JwtAuthenticationFilter`、`AuthenticationEntryPoint`、`AccessDeniedHandler`，按 4.6 重新配置 `SecurityFilterChain`
- [x] 9.2 修改 `WebMvcConfig`：注册 `TenantContextInterceptor`（5.3），保留 trailing slash 注释
- [x] 9.3 验证 init change 的 `HealthzControllerTest` 仍能通过（`@WebMvcTest` 切片下不会引入新 advice/filter，但确认无回归）

## 10. 文档与示例

- [x] 10.1 更新 `kuship-console/CLAUDE.md`：把"响应/异常契约"段落从"占位"改为"已就绪"；新增 JWT、TenantContext、分页、TraceId 段落；补充 `JWT_SECRET_KEY` 必须与 rainbond-console 同源的运维提示
- [x] 10.2 更新 `kuship-console/README.md`：新增"JWT 工作流验证"段落，包含从 rainbond-console 取 token + curl kuship-console 的完整步骤；补充 `JWT_SECRET_KEY` 环境变量
- [x] 10.3 在仓库根 `CLAUDE.md` 的 kuship-console 描述中追加一行说明本 change 已落契约层

## 11. 验收

- [x] 11.1 `mvn -pl kuship-console clean package`：BUILD SUCCESS，所有新增测试通过，0 项目代码 warning
- [x] 11.2 启动应用（local profile），`curl http://localhost:8080/console/healthz` 返回原约定 general_message 形状
- [x] 11.3 `curl http://localhost:8080/console/teams`（无 token）返回 HTTP 401，响应体形状 `{"code":401,"msg":"...","msg_show":"未认证或 token 失效","data":{"bean":{},"list":[]}}`
- [x] 11.4 用 `JwtTokenService.encode(...)` 构造一个 `user_id=1, username="admin", exp=now+1h` 的 token，curl `Authorization: GRJWT <token>` 调任意 `/console/*` 路径，应不再返回 401（即使路径不存在，会返回 404 由 advice 包装为 general_message）
- [x] 11.5 用同一个 token 但前缀改为 `Authorization: jwt <token>`（小写）也应通过认证
- [x] 11.6 把 token 改成 `exp=now-1h`，应返回 401
- [x] 11.7 `curl -i http://localhost:8080/console/healthz`，响应头包含 `X-Trace-Id: <uuid>`
- [x] 11.8 调用一个故意抛 NPE 的测试端点（仅 local profile 暴露），ERROR 日志中可看到与响应头同值的 traceId
- [x] 11.9 验证分页响应形状：构造一个返回 `Page<T>` 的临时端点，`curl ?page=1&page_size=2` 返回 `{code,msg,msg_show,data:{bean:{total:N},list:[...]}}`，**响应 JSON 中不出现 `page` 或 `page_size` 顶层字段**
- [x] 11.10 `docker build` 仍成功（基础镜像不变，仅新增 Java 类）

## 12. 跨链路验证（手工，非自动化 —— 留待运维真正部署 rainbond-console 后做）

- [ ] 12.1 启动 rainbond-console（参考 `reference/rainbond-console/Dockerfile` 或本地运行），完成登录拿到 GRJWT token
- [ ] 12.2 用同一 token 直接调 kuship-console 的任意 `/console/*` 路径，应通过认证（路径不存在则返回 404 general_message）
- [ ] 12.3 验证两边读取同一份 user 数据时，token 解析出的 `user_id` 一致

> 说明：本 change 未启动 rainbond-console Django 进程做端到端验证（只验证了 kuship-console 自身契约）。集成测试 `ContractIntegrationTest` 用同样的 SECRET_KEY 与同样的 djangorestframework-jwt 1.11.0 兼容 payload schema 自签 token 全部通过（包括 `GRJWT`/`jwt` 双前缀），可视为契约层等价覆盖；真正跨链路只有等运维侧把两个进程同时部起来才能跑。
