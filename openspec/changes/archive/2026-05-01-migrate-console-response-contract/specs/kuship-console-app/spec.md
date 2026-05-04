## ADDED Requirements

### Requirement: 全局响应自动包装

kuship-console SHALL 通过 `ResponseBodyAdvice` 把所有 `@RestController` 方法的返回值自动包装成 `ApiResult` 形状（与 rainbond-console `general_message` 完全一致）；对已经是 `ApiResult` 类型的返回值 SHALL 保持幂等不重复包装；对标注了 `@SkipResponseWrapper` 注解的方法或类 SHALL 跳过包装；对 Spring Boot Actuator 路径、文件下载（`Resource`）以及 `String` 返回类型 SHALL 不包装（业务若需让 string-like 数据走 general_message，需显式 `return GeneralMessage.ok(Map.of("value", str))`）。

#### Scenario: 任意 POJO 返回值被自动包装

- **WHEN** controller 方法直接 `return userDto;`（任意非 `ApiResult` 类型的 POJO）
- **THEN** 客户端实际收到的 JSON 响应体顶层是 `{"code":200,"msg":"success","msg_show":"OK","data":{"bean":{...userDto...},"list":[]}}`
- **AND** controller 方法签名无需改动以适配响应包装

#### Scenario: List 返回值注入 data.list

- **WHEN** controller 方法返回 `List<T>`
- **THEN** 响应体 `data.list` 包含该列表，`data.bean` 为空对象

#### Scenario: Page 返回值注入分页响应

- **WHEN** controller 方法返回 Spring Data `Page<T>`
- **THEN** 响应体 `data.list` 包含 `page.getContent()`
- **AND** 响应体 `data.bean.total` 等于 `page.getTotalElements()`
- **AND** 响应体不输出顶层 `page` 与 `page_size` 字段（与 rainbond-console `general_message(bean={"total": total}, list=...)` 形状一致；kuship-ui 在 `HttpTable/TcpTable/EnvironmentVariable/ClusterMgtInfo` 等组件按 `data.bean.total` 读取）

#### Scenario: ApiResult 返回值不重复包装

- **WHEN** controller 方法直接返回 `GeneralMessage.ok(...)` 或显式构造的 `ApiResult`
- **THEN** 响应体保持原状，不被外层再包一遍

#### Scenario: @SkipResponseWrapper 转义阀

- **WHEN** controller 方法标注了 `@SkipResponseWrapper` 或所在类标注了该注解
- **THEN** 返回值原样写入响应体，不被任何包装

#### Scenario: Actuator 路径不被包装

- **WHEN** 客户端访问 `GET /actuator/health`
- **THEN** 响应体保持 Spring Actuator 原生格式 `{"status":"UP",...}`，不被包装为 general_message

### Requirement: 全局异常映射

kuship-console SHALL 通过 `@RestControllerAdvice` 把以下异常类型统一映射为 `general_message` 形状的响应；HTTP 状态码与业务 `code` 解耦：除认证（401）与授权（403）由 Spring Security 自身的 EntryPoint / Handler 写出对应 HTTP 状态码外，其他异常 SHALL 一律返回 HTTP 200，业务 `code` 走响应体 `code` 字段。

#### Scenario: ServiceHandleException 透传 code/msg/msgShow

- **WHEN** controller 抛出 `new ServiceHandleException(404, "team not found", "团队不存在")`
- **THEN** HTTP 状态码为 200
- **AND** 响应体形如 `{"code":404,"msg":"team not found","msg_show":"团队不存在","data":{"bean":{},"list":[]}}`

#### Scenario: 参数校验失败

- **WHEN** controller 接收的请求体或 query 参数触发 `MethodArgumentNotValidException` 或 `ConstraintViolationException`
- **THEN** 响应体 `code=400`、`msg_show="参数校验失败"`、`data.bean.errors` 包含字段级错误明细列表

#### Scenario: 反序列化失败

- **WHEN** 客户端发送的请求体 JSON 不合法（触发 `HttpMessageNotReadableException`）
- **THEN** 响应体 `code=400`、`msg_show="请求体解析失败"`

#### Scenario: 缺失 Header / 类型不匹配

- **WHEN** 触发 `MissingRequestHeaderException` 或 `MethodArgumentTypeMismatchException`
- **THEN** 响应体 `code=400`，`msg` 字段包含具体字段名

#### Scenario: 兜底 Exception

- **WHEN** controller 抛出未在专用 handler 中处理的异常
- **THEN** HTTP 状态码 200、响应体 `code=500`、`msg_show="系统异常"`、`data.bean.trace_id` 包含本次请求的 traceId
- **AND** 服务端 ERROR 级别日志输出完整堆栈与同一 traceId

### Requirement: JWT 认证（兼容 djangorestframework-jwt 1.11.0）

kuship-console SHALL 实现 `JwtAuthenticationFilter`，能解析由 rainbond-console（Django + djangorestframework-jwt 1.11.0）签发的 token；支持 Authorization header 形如 `GRJWT <token>` 与 `jwt <token>` 两种前缀（不区分大小写）；使用 HS256 算法 + 与 Django 同源的 `SECRET_KEY`；解析 payload 时直接读取 Django 风格字段名（`user_id`、`username` 或 `nick_name`、`email`、`exp`、`orig_iat`），不做名字转换；过期校验启用，可配 `leeway-seconds`（默认 0）；解析成功后写入 Spring `SecurityContext` 与 `RequestContext`。

#### Scenario: GRJWT 前缀解析合法 token

- **WHEN** 客户端发送 `GET /console/anything` 携带 `Authorization: GRJWT <valid-token>`
- **THEN** Filter 解析成功，把 `user_id` 与 `username` 注入 `RequestContext`，请求继续向下分发

#### Scenario: jwt 前缀（小写）也兼容

- **WHEN** 客户端发送 `Authorization: jwt <valid-token>`（外部 portal 风格）
- **THEN** Filter 同样解析成功

#### Scenario: 缺失 Authorization header 触发 401

- **WHEN** 客户端访问需要认证的路径但未带 `Authorization` 头
- **THEN** 响应 HTTP 401、响应体 `{"code":401,"msg":"...","msg_show":"未认证或 token 失效","data":{"bean":{},"list":[]}}`

#### Scenario: 过期 token

- **WHEN** 客户端携带的 token 的 `exp` claim 已过去
- **THEN** 响应 HTTP 401，响应体 `code=401`、`msg_show="未认证或 token 失效"`
- **AND** 任何 profile 下 `msg` 字段都包含具体原因（如 `token expired`、`invalid signature`、`missing token`），用于联调与运维排查；`msg_show` 始终保持统一文案，不向最终用户泄露细节

#### Scenario: 篡改的 token

- **WHEN** 客户端携带的 token 签名校验失败
- **THEN** 响应 HTTP 401

#### Scenario: SECRET_KEY 通过环境变量注入

- **WHEN** 启动应用时未提供 `JWT_SECRET_KEY` 环境变量且 profile 不是 local
- **THEN** 应用启动失败，给出明确错误：`JWT_SECRET_KEY must be set in non-local profiles`

### Requirement: 请求上下文（RequestContext）

kuship-console SHALL 提供 `RequestContext`（`@RequestScope` Spring bean），暴露当前请求的 `user_id`、`username`、`team_name`、`region_name`、`enterprise_id` 五个字段；`JwtAuthenticationFilter` 在认证成功后写入 user 相关字段；`TenantContextInterceptor` 在 controller 执行前从 path variable 提取 `{team_name}` / `{region_name}` 写入；业务层通过 Spring 注入直接获取，不得再手动从 `HttpServletRequest` / `Authentication` 解析。

#### Scenario: path 中的 team_name 被自动注入

- **WHEN** 客户端访问 `GET /console/teams/myteam/apps`
- **AND** 该路径定义为 `@GetMapping("/console/teams/{team_name}/apps")`
- **THEN** 在 controller 方法内注入的 `RequestContext.getTeamName()` 返回 `"myteam"`

#### Scenario: JWT user_id 写入上下文

- **WHEN** 一个携带合法 token 的请求成功通过认证
- **THEN** `RequestContext.getUserId()` 返回 token payload 中的 `user_id`（数值类型保持原样不做字符串化）

#### Scenario: 路径变量名严格 snake_case

- **WHEN** 业务 controller 添加新路径
- **THEN** path variable 名必须保留 `team_name`、`region_name`、`service_alias`、`app_id` 等 Django 原始命名
- **AND** RequestContext 字段命名同样使用 snake_case 暴露给响应（如序列化为 JSON 时）

### Requirement: 分页参数与响应适配

kuship-console SHALL 提供 `PageRequestAdapter` 工具类统一处理分页输入：query 参数 `page` / `page_size` 一基（page=1 表示第一页）；缺省 `page=1`、`page_size=10`；`page_size` 上限 200；非法输入（`page < 1`、`page_size < 1`、`page_size > 200`）触发参数校验失败 → 400 响应。响应输出侧不引入额外的 `PageResponse<T>` 包装类型；当 controller 返回 Spring Data `Page<T>` 时，响应包装 advice SHALL 把 `page.getContent()` 写入 `data.list`、把 `page.getTotalElements()` 写入 `data.bean.total`，且不输出顶层 `page` / `page_size` 字段（与 rainbond-console 实际响应形状一致）。

#### Scenario: 默认分页参数

- **WHEN** 请求未带 `page` 或 `page_size`
- **THEN** `PageRequestAdapter` 转出的 Pageable 等价于 `PageRequest.of(0, 10)`

#### Scenario: page=1 一基转换

- **WHEN** 客户端传 `?page=1&page_size=20`
- **THEN** `PageRequestAdapter` 转出的 Pageable 等价于 `PageRequest.of(0, 20)`（内部 0 基）

#### Scenario: page=0 触发 400

- **WHEN** 客户端传 `?page=0`
- **THEN** 响应 `code=400`、`msg_show="参数校验失败"`

#### Scenario: page_size 超过上限触发 400

- **WHEN** 客户端传 `?page_size=500`
- **THEN** 响应 `code=400`

#### Scenario: Page 响应注入 data.bean.total

- **WHEN** controller 返回 `Page<T>`（Spring Data 分页结果，假设当前为第 2 页、每页 20、共 53 条）
- **THEN** 响应体 `data.list` 含 20 条
- **AND** 响应体 `data.bean.total = 53`
- **AND** 响应体不含顶层 `page` 与 `page_size` 字段（kuship-ui 自身保留请求参数用于翻页 UI，不依赖响应回写）

### Requirement: TraceId 透传

kuship-console SHALL 实现 `TraceIdFilter`，为每一个 HTTP 请求生成 UUID 形式的 traceId，写入 SLF4J MDC（key: `traceId`）并以 `X-Trace-Id` 响应头返回给客户端；异常日志 pattern SHALL 包含 traceId 字段；兜底 Exception 响应体的 `data.bean.trace_id` SHALL 等于响应头的 `X-Trace-Id`。

#### Scenario: 每个请求一个 traceId

- **WHEN** 客户端发送任意请求
- **THEN** 响应头包含 `X-Trace-Id: <uuid>`；连续两个请求的 traceId 不相同

#### Scenario: 异常日志含 traceId

- **WHEN** controller 抛出未处理异常
- **THEN** 服务端 ERROR 级别日志中包含 `traceId=<uuid>`，且与响应头 `X-Trace-Id` 一致
- **AND** 响应体 `data.bean.trace_id` 等于该 traceId（便于用户报错时复制）

## MODIFIED Requirements

### Requirement: Spring Security 配置（JWT 认证生效）

kuship-console SHALL 通过 `SecurityFilterChain` 强制 `/console/**` 与 `/openapi/**` 路径需要 JWT 认证；以下路径除外（保持 `permitAll`）：`/console/login`、`/console/oauth/**`、`/console/healthz`、`/console/healthz/`、`/actuator/**`、Spring 静态资源默认路径；`SessionCreationPolicy` 保持 `STATELESS`；CSRF / 默认 form login / httpBasic 全部关闭；`JwtAuthenticationFilter` SHALL 注册在 `UsernamePasswordAuthenticationFilter` 之前；`AuthenticationEntryPoint` 与 `AccessDeniedHandler` SHALL 输出 `general_message` 形状的响应体（HTTP 401/403 但响应体仍是 `{code,msg,msg_show,data}`）。

#### Scenario: healthz 与 actuator 仍可未授权访问

- **WHEN** 客户端不带任何认证 header 发送 `GET /console/healthz` 或 `GET /actuator/health`
- **THEN** 响应状态码为 200，不返回 401 或 403
- **AND** healthz 响应体仍是约定的 general_message 形状

#### Scenario: 无 token 访问业务路径触发 401

- **WHEN** 客户端不带 `Authorization` 发送 `GET /console/teams`（即使是不存在的路径）
- **THEN** 响应 HTTP 401
- **AND** 响应体形如 `{"code":401,"msg":"...","msg_show":"未认证或 token 失效","data":{"bean":{},"list":[]}}`

#### Scenario: 已认证但无权限的 403

- **WHEN** 业务 controller 抛出 `AccessDeniedException`
- **THEN** 响应 HTTP 403、响应体 `code=403`、`msg_show="权限不足"`

#### Scenario: SecurityConfig 不再 permitAll 全量

- **WHEN** 检查 `SecurityConfig` 的 `authorizeHttpRequests` 配置
- **THEN** 不存在 `.anyRequest().permitAll()`；存在显式的 `requestMatchers(...).permitAll()` 白名单与 `.anyRequest().authenticated()` 兜底

### Requirement: 响应包装契约（自动包装就绪）

kuship-console SHALL 在 `cn.kuship.console.common.response` 包内提供 `ApiResult` POJO 与 `GeneralMessage` 静态工厂工具类，输出 JSON 形状必须与 rainbond-console 的 `general_message(code, msg, msg_show, bean=, list=, **kwargs)` 完全一致；同时 SHALL 注册 `GeneralMessageResponseBodyAdvice` 全局自动包装所有 `@RestController` 返回值，业务 controller 可直接 `return user;` 而无需显式调用 `GeneralMessage.ok(...)`；提供 `@SkipResponseWrapper` 注解作为转义阀。

#### Scenario: ApiResult 字段顺序与命名

- **WHEN** 序列化 `GeneralMessage.ok()` 返回的对象为 JSON
- **THEN** 顶层字段必须依次包含 `code`、`msg`、`msg_show`、`data`，且 `data` 是嵌套对象，至少包含 `bean` 和 `list` 两个键
- **AND** 字段名使用 snake_case（`msg_show` 而非 `msgShow`），以兼容 kuship-ui 的解析逻辑

#### Scenario: 支持任意 kwargs

- **WHEN** 调用 `GeneralMessage.ok(Map.of("total", 100, "page", 1))`
- **THEN** `data` 节点中除 `bean` 和 `list` 外，还包含 `total: 100` 与 `page: 1` 字段

#### Scenario: Controller 直接返回 POJO 由 advice 自动包装

- **WHEN** controller 方法签名 `public User getUser(...)` 直接 `return user;`
- **THEN** 客户端实际收到 `{"code":200,"msg":"success","msg_show":"OK","data":{"bean":{...user...},"list":[]}}` 形状响应
