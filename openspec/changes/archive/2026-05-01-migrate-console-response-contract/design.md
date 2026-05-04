## Context

`init-kuship-console` 已落地工程骨架与 `/console/healthz`，但留下了 5 个共享契约缺口：响应自动包装、异常映射、JWT 认证、租户上下文、分页适配。这些是横切关注点——后续 12 个业务迁移 change 中的每一个 controller 都会用到。

参考实现侧已确认的关键事实（来自 `reference/rainbond-console`）：

- `goodrain_web/settings.py:148-167` —— `JWT_AUTH_HEADER_PREFIX = 'GRJWT'`，HS256，10 年过期，`SECRET_KEY` 来自环境变量或 `get_hash_mac()`
- `console/views/base.py:55-90` —— 实际接受**两种** prefix（`GRJWT` 与 `jwt` 小写），还支持从 cookie 取 token（`JWT_AUTH_COOKIE`）
- `console/serializer.py` —— `username_field = 'nick_name'`，登录返回 `{token, user}`
- `www/utils/return_message.py` —— `general_message(code, msg, msg_show, bean=, list=, **kwargs)`：`code` 是业务码（与 HTTP 状态码解耦），`data.bean` / `data.list` / `**kwargs` 都注入 `data` 节点

约束：

- kuship-ui 期望 baseURL `/console/*`，token header 用什么前缀完全取决于 rainbond-console 的实际实现，因此 kuship-console 必须**接受 `GRJWT` 与 `jwt` 两种前缀**
- 双写并行模式下，rainbond-console（Django）和 kuship-console（Spring）共享同一个 SECRET_KEY，从 Django 端签发的 token 必须能在 Spring 端解析
- HTTP 状态码与业务 code 解耦：HTTP 一律 200（除 401/403），业务码走响应体 `code` 字段——这是 rainbond-console 的祖传约定，前端依赖

## Goals / Non-Goals

**Goals：**

- 任意 `@RestController` 方法 `return user;` 即可被自动包装成 `{code,msg,msg_show,data:{bean:{...}}}`
- 任意 controller 抛 `ServiceHandleException(404, "team not found", "团队不存在")` 自动产出对应响应
- 兼容 Django 端 djangorestframework-jwt 1.11.0 签发的 token（GRJWT/jwt 双前缀，HS256，payload schema 完全一致）
- 业务层通过 `RequestContext` 拿到 `user_id`/`username`/`team_name`/`region_name`，无需手动解析 path variable
- 业务层把 query `page`/`page_size` 转 `Pageable` 与 `Page<T>` 转响应，全部走工具类
- 每个请求一个 `X-Trace-Id`，error 日志带 traceId

**Non-Goals：**

- 业务 controller / entity / repository / service（这是后续每个业务 change 的工作）
- Region API client（独立 epic `migrate-console-region-client`）
- RBAC 权限矩阵（业务 change 在自己的范围内决策）
- CORS 配置（按之前讨论先不开；待 ui 反馈跨域问题再开 change）
- msg_show 国际化（中文写死即可，i18n 留作后续）
- `enterprise_id` 反查（待 account/team 模块落地）
- 登录/oauth/token 签发实现（仅做"接受 token"侧，签发由后续 `migrate-console-account-team` 处理）

## Decisions

### 决策 1：自动包装响应（ResponseBodyAdvice）+ `@SkipResponseWrapper` 转义阀

**选择：** `GeneralMessageResponseBodyAdvice implements ResponseBodyAdvice<Object>`，在 `beforeBodyWrite` 把任意非 `ApiResult` 返回值用 `GeneralMessage.ok(bean)` 包一层；已经是 `ApiResult` 的不重复包装（幂等）。

**`supports()` 排除规则：**
- 方法或类被 `@SkipResponseWrapper` 标注 → 不包装
- 方法返回类型是 `ResponseEntity<Resource>` 或 `StreamingResponseBody`（文件下载） → 不包装
- 方法所在类的 package 以 `org.springframework.boot.actuate.` 开头（actuator） → 不包装
- 方法返回 `ApiResult` 类型 → 不重复包装

**特殊处理：**
- 返回值是 `Page<T>`（Spring Data 分页）→ 自动注入 `data.list = page.getContent()`、`data.bean = { "total": page.getTotalElements() }`，**不输出顶层 page / page_size 字段**（实测 kuship-ui `HttpTable/TcpTable/EnvironmentVariable/ClusterMgtInfo` 等组件均按 `data.list + data.bean.total` 读取，与 rainbond-console `general_message(... bean={'total': total}, list=data)` 形状一致；前端自身保留请求时传入的 page/pageSize 用于翻页 UI，不依赖响应回写）
- 返回值是 `List<T>` → 注入 `data.list`
- 返回值是 `Map<String,Object>` 或其他 POJO → 注入 `data.bean`
- 返回值类型是 `String` → **不自动包装**（实施期发现 Spring 对 String 返回类型有特殊 cast 逻辑，advice 把 String 转为 ApiResult 后会触发 `ClassCastException`）。业务若想让 string-like 数据走 general_message，需显式 `return GeneralMessage.ok(Map.of("value", str))` 或返回 `ApiResult`

**理由：** 500+ 业务路由，手动包装重复且易出错。

**风险与缓解：**
- 误包装系统端点 → `supports()` 多重排除（actuator package + 注解 + 返回类型）+ 单元测试每条排除路径
- 与 `@RestControllerAdvice` 异常处理交互 → 异常分支返回 `ApiResult` 时 advice 检测到不重复包装

### 决策 2：JWT 与 djangorestframework-jwt 1.11.0 完全互通

**选择：**
- 算法：HS256（与 Django 一致）
- SECRET_KEY：从 `kuship.security.jwt.secret-key` 读取，默认 `${JWT_SECRET_KEY:}`，运维侧必须与 Django 同源
- Authorization 前缀：**同时接受 `GRJWT`（主）与 `jwt`（外部 portal 兼容）**，配置项 `kuship.security.jwt.auth-header-prefixes=GRJWT,jwt`
- payload claims：直接读 `user_id`、`username`/`nick_name`、`email`、`exp`、`orig_iat`，不做名字转换
- 过期校验：`leeway-seconds` 配置（默认 0，与 Django `JWT_LEEWAY=0` 一致）
- Cookie 模式：本 change **不实现**（kuship-ui 是纯 SPA，统一走 Authorization header）；后续如要兼容 Rainbill portal cookie 路径再开 change

**理由：** 双写并行兼容是硬需求；用 jjwt 0.12.x 自己实现解析比硬接 Spring Security OAuth2 Resource Server 更直接（Resource Server 假设标准 JWT，Django 的 payload 字段名不标准）。

**风险与缓解：**
- SECRET_KEY 泄露 → 环境变量注入 + `application-local.yaml` 入 `.gitignore`（已生效）
- 算法误用（HS256 vs RS256） → 配置项显式 `algorithm: HS256`，filter 解析时强制校验 `alg` 头
- 时钟偏移导致 token 提前/延后过期 → `leeway-seconds` 可配；生产建议 0，仅 debug 时调大

### 决策 3：HTTP 状态码与业务 `code` 解耦

**选择：** 默认 HTTP 200，业务 `code` 走响应体；仅 401/403 由 Spring Security 自身的 `AuthenticationEntryPoint`/`AccessDeniedHandler` 触发对应 HTTP 状态码（同时响应体也是 general_message 形状）。

**理由：** rainbond-console 祖传约定，kuship-ui 全部以业务 `code` 判断成败；改成 HTTP 状态码与 `code` 同步会让 ui 多写大量分支。

**实现细节：**
- `GlobalExceptionHandler` 所有方法都 `return GeneralMessage.error(...)`（HTTP 200）
- `JwtAuthenticationFilter` 解析失败时不直接写响应，让 Spring Security 链路抛 `AuthenticationException`，由 `AuthEntryPoint` 写 `general_message` 形状到 401 响应
- 类似地 `AccessDeniedHandler` 写 403

### 决策 4：RequestContext 用 `@RequestScope` 而非 ThreadLocal

**选择：** `@Component @RequestScope class RequestContext { ... }`，业务层注入即可拿到。

**理由：**
- Java 21 虚拟线程下 ThreadLocal 行为复杂（每虚拟线程一个 Local），InheritableThreadLocal 更难
- Spring 原生作用域有清理逻辑，避免泄漏
- 强制只能在 web 请求线程访问，业务异步任务必须显式传递（这是好事，强制清晰）

**填充时机：**
1. `JwtAuthenticationFilter` 写入 `user_id`、`username`
2. `TenantContextInterceptor` 在 `preHandle` 从 path variable 写入 `team_name`、`region_name`
3. `enterprise_id` 留 null，待 account/team change 引入用户表后填充

### 决策 5：分页一基（page=1 起算）+ 输出形状对齐 rainbond-console

**输入侧：** `PageRequestAdapter.toPageable(int page, int pageSize)` 内部 `PageRequest.of(page-1, pageSize)`。

**输出侧（实测 kuship-ui 后修订）：** advice 看到 `Page<T>` 时直接构造 `data.list = page.content` + `data.bean = {"total": page.totalElements}`；**不输出** 顶层 `page` 与 `page_size` 字段，也不引入额外的 `PageResponse<T>` 包装类型。

**默认值与上限：**
- `page` 缺省 1；< 1 时抛 `MethodArgumentNotValidException`（→ 400）
- `page_size` 缺省 10；最大 200（超出 → 400）

**理由：**
- 输入侧 page=1 一基与 Django/rainbond-console 一致
- 输出侧实测：`reference/rainbond-console/console/views/service_share.py:73` / `app_upgrade.py:67` / `app_config/app_env.py:131` 等多处都用 `general_message(... bean={"total": total}, list=data)`；kuship-ui `HttpTable/index.js:95` / `TcpTable/index.js:88` / `EnvironmentVariable/index.js:742` / `ClusterMgtInfo/index.js:433` 全部按 `total: data.bean.total` 读取
- 不引入 `PageResponse<T>` POJO 是为了避免业务 controller 多一种返回类型选择；直接 `return page;` 由 advice 处理，符合"业务 controller 不关心契约"的目标

**风险与缓解：**
- off-by-one → 单元测试覆盖 `page=1`/`page=N`/`page=0`/`page=-1`/`page_size=201` 五种边界
- 个别 rainbond-console view（如 `account_fee.py:145`）把 `total` 直接挂在 `data` 顶层而非 `bean` 内 → 这是少数派写法，前端没有按这种形状读；本 change 统一以 `data.bean.total` 为契约；遇到这类异常 view 在对应业务 change 中显式 `return GeneralMessage.okWithExtras(...)` 手动构造

### 决策 6：traceId 用 UUID + 响应头透出

**选择：**
- `TraceIdFilter extends OncePerRequestFilter`：每个请求 `UUID.randomUUID().toString()` 写入 MDC（key: `traceId`）+ 响应头 `X-Trace-Id`
- 异常日志 logback pattern 包含 `%X{traceId}`，error 路径自动有 traceId
- `GlobalExceptionHandler` 在响应体的 ERROR 兜底分支带上 traceId（让用户能复制粘贴给后端）

**理由：** UUID 简单、无外部依赖，足够用；后续接入 OpenTelemetry 时再换 W3C TraceContext。

### 决策 7：SecurityFilterChain 升级路径

**选择：**
```
permitAll:
  - GET  /actuator/**
  - POST /console/login
  - POST /console/oauth/**
  - GET  /console/healthz
  - GET  /console/healthz/
  - 静态资源（如有）

authenticated（要求 JWT）:
  - 其他 /console/**
  - 其他 /openapi/**
```

**Filter 顺序：** `TraceIdFilter` → `JwtAuthenticationFilter` → `UsernamePasswordAuthenticationFilter`。

**Session 策略：** 仍然 `STATELESS`，与 init change 一致。

### 决策 8：Trailing slash 在 advice 下仍然兼容

`init-kuship-console` 选了「在 controller 注解里同时列出两种路径」的方案。本 change **保持不变**，不引入全局 trailing-slash 重写 filter——理由：
- 当前只有一个 controller（HealthzController），后续业务 change 也会按需用 `@GetMapping({"/console/foo", "/console/foo/"})`
- 全局 filter 重写需要一个透明的 wrapper（`HttpServletRequestWrapper`），增加 advice/JWT filter 的不确定性
- 影响范围：测试时业务 change 加一行注解参数即可，可接受

## Risks / Trade-offs

- **响应自动包装可能误伤** → 通过 `@SkipResponseWrapper` 注解 + `supports()` 多重排除 + 完整覆盖测试控制
- **JWT 兼容 Django payload 的非标准字段** → 自实现 jjwt 解析，不依赖 Spring Security 的 OAuth2 Resource Server；但同时意味着无法享受 OAuth2 的标准能力，将来如要换标准 JWT 需要再开 change
- **HTTP 200 + 业务 code 解耦的代价** → 监控（如 ALB/Nginx）按 HTTP 状态码统计错误率会失真；运维需要额外解析响应体 `code`。Mitigation：在文档中说明，并考虑后续给 actuator 加业务 metric
- **SECRET_KEY 必须与 Django 同源** → 本 change 不掌控 Django 端运维，只能假设运维侧正确同步；CLAUDE.md 必须明确说明
- **本 change 不实现 token 签发** → 用户从 rainbond-console 登录拿 token，再用 token 调 kuship-console。本机端到端验收需要先启动 rainbond-console 完成登录，操作链路较长。Mitigation：`tasks.md` 中提供 `JwtTokenService.encode(...)` 工具方法（仅供测试和后续业务 change 使用），手动构造合法 token 用于 curl 验证

## Migration Plan

本 change 是新增契约层，无在线迁移：

1. 实现所有 advice / filter / interceptor / 工具类
2. 升级 `SecurityConfig` 的 `permitAll` 为 JWT-required（保留 healthz/actuator/login/oauth permitAll）
3. 单元/集成测试覆盖 5 类契约 + trailing slash 回归
4. 启动验收：手动构造一个 GRJWT token 验证 `/console/healthz` 仍 permitAll、`/console/dummy`（任意未实现路径）返回 401 的 general_message 形状
5. 部署时必须设置 `JWT_SECRET_KEY` 环境变量

**回滚策略：** 删除新增的 advice/filter/interceptor 文件 + 把 `SecurityConfig` 改回 `permitAll` 即可；本 change 不写库、不改 schema。

## Resolved Decisions（原 Open Questions，propose 阶段已落定）

- **SECRET_KEY 同源策略 → 环境变量**
  - kuship-console 与 rainbond-console（Django 进程）通过同一个 `JWT_SECRET_KEY` 环境变量同源；不引入配置中心。运维侧负责保证两个进程用同一份值；CLAUDE.md / README.md 必须显式说明此约定
- **TraceId → UUID**
  - 简单、无外部依赖；后续接入 OpenTelemetry 时再换成 W3C `traceparent`，不在本 change 范围
- **401 失败原因 → 始终暴露**
  - 不再按 profile 区分；任何环境下 `msg` 字段都包含具体原因（如 `token expired`、`invalid signature`、`missing token`），便于联调与运维排查
  - 同时 `msg_show` 仍保持统一 `"未认证或 token 失效"`，避免在 ui 上把内部细节暴露给最终用户
- **`@SkipResponseWrapper` 作用域 → 同时支持方法级和类级**
  - 方法级优先于类级（方法上有 `@SkipResponseWrapper` 但类上没有 → 仍跳过；类上有但方法上没有 → 跳过；都没有 → 走自动包装）
- **分页响应字段名 → `data.list` + `data.bean.total`**
  - 实测 `reference/rainbond-console/console/views/service_share.py:73`、`app_upgrade.py:67`、`app_config/app_env.py:131`、`app_config/app_domain.py:1117` 等大量 view 用 `general_message(... bean={"total": total}, list=data)` 形状；kuship-ui `HttpTable/index.js:95`、`TcpTable/index.js:88`、`EnvironmentVariable/index.js:742`、`ClusterMgtInfo/index.js:433` 等组件全部按 `total: data.bean.total` 读取
  - 不输出顶层 `page` 与 `page_size`，也不引入 `PageResponse<T>` 包装类型；详见决策 1 与决策 5
