# kuship-console-app

## Purpose

Java/Spring Boot console backend for the kuship platform.

## Requirements

### Requirement: Maven 工程骨架

kuship-console 模块 SHALL 以 Maven 单模块工程的形式存在于 `kuship-console/` 目录，使用 Java 21 与 Spring Boot 4.0.6，并且能够通过 `mvn clean package` 在本机一次构建成功，产出可执行 jar。

#### Scenario: 本机干净构建

- **WHEN** 在仓库根执行 `mvn -pl kuship-console clean package`
- **THEN** 构建过程在本机环境无任何 ERROR 或可消除的 WARNING 而结束
- **AND** 在 `kuship-console/target/` 下产出一个可执行的 Spring Boot fat jar

#### Scenario: 工程包结构存在

- **WHEN** 检查 `kuship-console/src/main/java/cn/kuship/console/` 目录树
- **THEN** 必须存在以下顶层包：`config`、`common/response`、`common/exception`、`infrastructure/jpa`、`infrastructure/region`、`infrastructure/k8s`、`modules`、`healthz`
- **AND** Spring Boot 启动类 `KuShipConsoleApplication` 位于 `cn.kuship.console` 根包下

### Requirement: 与 rainbond-console 共享 MySQL `console` 数据库

kuship-console SHALL 通过 JPA 连接到 rainbond-console 已有的 MySQL `console` 数据库；启动时必须以 `hibernate.ddl-auto=validate` 的模式校验 schema 与 Hibernate 元数据一致；在任何环境下 SHALL NOT 通过 Hibernate 生成或修改任何表结构。

#### Scenario: 本地环境连接成功

- **WHEN** 本机已运行 rainbond-console 的 MySQL（`127.0.0.1:3306`，用户 `root`，密码 `123456`，库名 `console`）
- **AND** 以 `local` profile 启动 kuship-console
- **THEN** 应用成功启动，无 schema 校验失败异常

#### Scenario: 凭据不进 git

- **WHEN** 在仓库执行 `git status --ignored` 或检查 `.gitignore`
- **THEN** `kuship-console/src/main/resources/application-local.yaml` 处于被忽略状态
- **AND** 提交到仓库的 `application.yaml` 中数据库密码以环境变量占位（如 `${DB_PASSWORD:123456}`）形式存在，不直接写明文

#### Scenario: 禁止 schema 演进

- **WHEN** 检查 Spring 配置
- **THEN** `spring.jpa.hibernate.ddl-auto` 的值必须是 `validate`
- **AND** Flyway 配置 `baseline-on-migrate=true`、`baseline-version=0`
- **AND** `kuship-console/src/main/resources/db/migration/` 目录存在但不包含任何创建/修改业务表的 SQL 文件

### Requirement: `/console/healthz` 健康检查端点

kuship-console SHALL 暴露 `GET /console/healthz` 端点，返回 HTTP 200 与 rainbond-console 兼容的 `general_message` 响应包结构。

#### Scenario: 返回兼容响应格式

- **WHEN** 客户端发送 `GET /console/healthz`
- **THEN** 响应状态码为 200
- **AND** 响应 Content-Type 为 `application/json`
- **AND** 响应体严格符合形状 `{"code":200,"msg":"success","msg_show":"OK","data":{"bean":{},"list":[]}}`

#### Scenario: trailing slash 兼容

- **WHEN** 客户端发送 `GET /console/healthz/`（带尾部斜杠）
- **THEN** 响应状态码为 200，且响应体与不带尾斜杠的请求一致

### Requirement: Actuator 健康/指标端点

kuship-console SHALL 暴露 Spring Boot Actuator 的 `health`、`info`、`metrics` 端点，挂在 `/actuator/*` 路径下，并且不与 `/console/*` 业务路径冲突。

#### Scenario: actuator/health 可访问

- **WHEN** 客户端发送 `GET /actuator/health`
- **THEN** 响应状态码为 200
- **AND** 响应体的 `status` 字段为 `UP`

### Requirement: URL 路径策略与契约

kuship-console SHALL NOT 配置 `server.servlet.context-path`；每个 controller 必须显式声明完整路径前缀（如 `/console`、未来的 `/openapi`、`/app-server` 等），且路径变量名必须与 rainbond-console 原始 Django URL 中保持一致（如 `team_name`、`region_name`、`service_alias`、`app_id` 等不得重命名为驼峰）。

#### Scenario: 不使用 context-path

- **WHEN** 检查 `application.yaml` 与 `application-local.yaml`
- **THEN** 不存在 `server.servlet.context-path` 配置项（或其值为空字符串）

#### Scenario: HealthzController 路径前缀显式声明

- **WHEN** 检查 `HealthzController` 类
- **THEN** 类或方法上存在 `@RequestMapping("/console/healthz")` 或等价显式注解，路径前缀不依赖全局 context-path

### Requirement: 容器化部署支持

kuship-console SHALL 提供 JVM 模式的 `Dockerfile`，基于 `eclipse-temurin:21-jre`，可被 `docker build` 构建并运行，容器暴露 8080 端口。

#### Scenario: 镜像构建成功

- **WHEN** 在 `kuship-console/` 目录执行 `docker build -t kuship-console:dev .`
- **THEN** 构建过程成功结束，产出可运行镜像

#### Scenario: 容器内 healthz 可访问

- **WHEN** 启动镜像并将 8080 映射到主机（容器需能访问宿主机 MySQL 或通过环境变量配置数据库地址）
- **THEN** `curl http://localhost:8080/console/healthz` 返回 200 与约定 JSON 格式

### Requirement: 模块文档

kuship-console SHALL 在模块根目录提供 `README.md`（面向开发者的本地启动与构建指南）和 `CLAUDE.md`（面向 AI 助手的包结构、约束、与 rainbond-console 共享数据库的关键说明），两份文档相互不重复但保持一致性。

#### Scenario: README 包含本地启动步骤

- **WHEN** 阅读 `kuship-console/README.md`
- **THEN** 文档至少包含：环境前置要求（Java 21、Maven、本地 MySQL）、`mvn clean package`、`mvn spring-boot:run -Dspring-boot.run.profiles=local`、Docker 构建命令、healthz 验证命令

#### Scenario: CLAUDE.md 记录关键约束

- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档至少包含：包结构图、共享 rainbond-console 数据库的约束（schema 只读、不擅自加 `@Version`）、URL 路径策略（不用 context-path、保留 snake_case 路径变量）、响应格式契约（与 Django 版 `general_message` 完全一致）、技术栈版本（Spring Boot 4.0.6、Java 21、JPA + Hibernate + QueryDSL、Maven）、迁移路线图引用（指向本 change 的 design.md）

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

kuship-console SHALL 通过 `@RestControllerAdvice` 把以下异常类型统一映射为 `general_message` 形状的响应；HTTP 状态码与业务 `code` 解耦：除认证（401）与授权（403）由 Spring Security 自身的 EntryPoint / Handler 写出对应 HTTP 状态码外，其他异常 SHALL 一律返回 HTTP 200，业务 `code` 走响应体 `code` 字段。Region 异常族（`RegionApiException`、`RegionApiFrequentException`、`InvalidLicenseException`、`ClusterLackOfMemoryException`、`TenantLackOfMemoryException`、`TenantLackOfCpuException`、`TenantQuotaCpuLackException`、`TenantQuotaMemoryLackException`、`ClusterAuthLackOfMemoryException`、`ClusterAuthLackOfNodeException`、`ClusterAuthLackOfLicenseException`、`ClusterAuthLackOfLicenseExpireException`、`RegionApiSocketException`）也 SHALL 被映射为对应业务 code 的 general_message 响应。

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

#### Scenario: RegionApiException 透传业务码

- **WHEN** service 层调用 region API 抛出 `RegionApiException(code=409, msg="tenant already exists", msgShow="团队已存在")`
- **THEN** 响应体 `code=409`、`msg="tenant already exists"`、`msg_show="团队已存在"`，HTTP 200

#### Scenario: RegionApiFrequentException 映射为 429 业务码

- **WHEN** service 层抛出 `RegionApiFrequentException`
- **THEN** 响应体 `code=429`、`msg_show="操作过于频繁，请稍后再试"`，HTTP 200

#### Scenario: InvalidLicenseException 映射

- **WHEN** service 层抛出 `InvalidLicenseException`
- **THEN** 响应体 `code=10400`、`msg_show="集群授权失效或未授权"`，HTTP 200

#### Scenario: 资源不足异常族映射

- **WHEN** service 层抛出 `ClusterLackOfMemoryException` / `TenantLackOfMemoryException` / `TenantLackOfCpuException` 等
- **THEN** 响应体 `code` 为对应业务码（412 类），`msg` 为原始 region 错误码字面（`cluster_lack_of_memory` 等），`msg_show` 为对应中文文案（如 `"集群内存不足"`、`"团队内存配额不足"`）

#### Scenario: RegionApiSocketException 映射为 503

- **WHEN** service 层抛出 `RegionApiSocketException`（socket 重试后仍失败）
- **THEN** 响应体 `code=503`、`msg_show="集群网络不可达"`，HTTP 200

### Requirement: JWT 认证（兼容 djangorestframework-jwt 1.11.0）

kuship-console SHALL 实现 `JwtAuthenticationFilter`，能解析由 rainbond-console（Django + djangorestframework-jwt 1.11.0）签发的 token；支持 Authorization header 形如 `GRJWT <token>` 与 `jwt <token>` 两种前缀（不区分大小写）；使用 HS256 算法 + 与 Django 同源的 `SECRET_KEY`；解析 payload 时直接读取 Django 风格字段名（`user_id`、`username` 或 `nick_name`、`email`、`exp`、`orig_iat`），不做名字转换；过期校验启用，可配 `leeway-seconds`（默认 0）；解析成功后 SHALL 通过 `userRepository.findById(payload.user_id)` 真实加载 user 写入 Spring `SecurityContext` 与 `RequestContext`，user 不存在时 SHALL 拒绝请求（`code=401`、`msg_show="未认证或 token 失效"`、`msg="user not found"`）。

#### Scenario: GRJWT 前缀解析合法 token

- **WHEN** 客户端发送 `GET /console/anything` 携带 `Authorization: GRJWT <valid-token>`
- **THEN** Filter 解析成功，从 user_info 表加载该用户的完整记录，把 `userId`、`username`、`email`、`enterpriseId`、`isSysAdmin` 注入 `RequestContext`，请求继续向下分发

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

#### Scenario: token 中的 user_id 在数据库中已被删

- **WHEN** 客户端持有合法签名但 `user_id` 对应的用户已从 `user_info` 表删除
- **THEN** 响应 HTTP 401，`msg="user not found"`、`msg_show="未认证或 token 失效"`

### Requirement: 请求上下文（RequestContext）

kuship-console SHALL 提供 `RequestContext`（`@RequestScope` Spring bean），暴露当前请求的 `user_id`、`username`、`team_name`、`region_name`、`enterprise_id`、`is_sys_admin` 六个字段；`JwtAuthenticationFilter` 在认证成功后通过 `userRepository.findById` 加载真实用户，写入 `userId` / `username` / `email` / `enterpriseId` / `isSysAdmin` 字段；`TenantContextInterceptor` 在 controller 执行前从 path variable 提取 `{team_name}` / `{region_name}` 写入；业务层通过 Spring 注入直接获取，不得再手动从 `HttpServletRequest` / `Authentication` 解析。

#### Scenario: path 中的 team_name 被自动注入

- **WHEN** 客户端访问 `GET /console/teams/myteam/apps`
- **AND** 该路径定义为 `@GetMapping("/console/teams/{team_name}/apps")`
- **THEN** 在 controller 方法内注入的 `RequestContext.getTeamName()` 返回 `"myteam"`

#### Scenario: JWT user_id 写入上下文

- **WHEN** 一个携带合法 token 的请求成功通过认证
- **THEN** `RequestContext.getUserId()` 返回 token payload 中的 `user_id`（数值类型保持原样不做字符串化）
- **AND** `RequestContext.getEnterpriseId()` 返回从 `user_info.enterprise_id` 字段加载的真实值
- **AND** `RequestContext.isSysAdmin()` 返回 `user_info.sys_admin` 字段（boolean）

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

### Requirement: Spring Security 配置（JWT 认证生效）

kuship-console SHALL 配置 Spring Security 启用 `JwtAuthenticationFilter`，requireAll 默认要求 JWT 认证；并保留以下端点 permitAll 白名单：

1. `/console/healthz`、`/actuator/**`（运维探针）
2. `POST /console/users/login`、`POST /console/users/register`（登录注册）
3. `GET /console/enterprise/info`（登录页平台信息）
4. `GET /console/perms`（权限元数据公开供前端权限树渲染）
5. `POST /console/init/perms`（仅 `kuship.security.allow-public-init=true` 时；默认 false）

未授权访问受保护路径 SHALL 返回 HTTP 401 + `general_message` 形状；CSRF 关闭（与 rainbond-console 一致）；session 关闭（stateless）。

#### Scenario: 未登录访问 healthz

- **WHEN** 客户端不带 Authorization 调 `GET /console/healthz`
- **THEN** 响应 HTTP 200，正常返回健康信息

#### Scenario: 未登录访问 enterprise/info

- **WHEN** 客户端不带 Authorization 调 `GET /console/enterprise/info`
- **THEN** 响应 HTTP 200，返回脱敏的 enterprise 基本信息

#### Scenario: 未登录访问 users/details

- **WHEN** 客户端不带 Authorization 调 `GET /console/users/details`
- **THEN** 响应 HTTP 401，`msg_show="未认证或 token 失效"`

#### Scenario: allow-public-init 默认关闭

- **WHEN** `kuship.security.allow-public-init` 未设或 `false`
- **AND** 客户端不带 Authorization 调 `POST /console/init/perms`
- **THEN** 响应 HTTP 401

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

### Requirement: Region API 客户端基础设施

kuship-console SHALL 提供 `cn.kuship.console.infrastructure.region` 模块作为 Region API 客户端基础设施：包含 `RegionClientFactory`（按 enterpriseId+regionName 缓存 RestClient）、`RegionInfoRepository`（JdbcTemplate 只读 DTO）、`KeyStoreFactory` / `SslContextFactory`（PEM 内联构造 PKCS12 KeyStore）、`RegionApiResponseProcessor`（13 个 RegionApiException 族映射）、`RegionErrorMsgEnricher`（Helm 冲突 + 域名冲突 + 频繁操作短语汉化）。

`RegionClientFactory.evict(enterpriseId, regionName)` SHALL 是 public method，业务层删除 region 后 SHALL 显式调用使缓存失效。

14 个域接口（`TenantOperations` / `ClusterOperations` / `ServiceOperations` 等）按业务 change 渐进补完业务实现：
- `migrate-console-region-client`：完成 `TenantOperations` 5 method
- `migrate-console-region-cluster`：完成 `ClusterOperations` 8 method
- `migrate-console-application-core`：完成 `ServicePortOperations` 5 + `ServiceVolumeOperations` 3 + `ServiceDependencyOperations` 2 + `ServiceProbeOperations` 3 + `ServiceOperations.getServiceInfo`
- **`migrate-console-app-create`**（本 change）：完成 `ServiceOperations` 剩余 6 method（createService / updateService / deleteService / buildService / codeCheck / getServiceLanguage）
- 其他业务 change（`migrate-console-app-runtime` / `migrate-console-app-market` 等）补完各自所需的 method。

#### Scenario: 按 (enterpriseId, regionName) 缓存 RestClient

- **WHEN** 业务 service 多次调 `regionClientFactory.getClient("ent-1", "r1")`
- **THEN** 仅首次构造 RestClient + KeyStore；后续调用从内存缓存返回同一实例

#### Scenario: evict 失效缓存

- **WHEN** 调用 `regionClientFactory.evict("ent-1", "r1")`
- **THEN** 缓存中该 key 的 RestClient 被移除；下次 `getClient` 重新构造（重新读 region_info、装配 mTLS）

#### Scenario: ServiceOperations 6 method 落地

- **WHEN** application-create change 落实 `ServiceOperations.createService(...)` / `deleteService(...)` / `buildService(...)` 等
- **THEN** 在 `cn.kuship.console.modules.appcreate.api.ServiceOperationsImpl`（@Primary @Service）中实现，覆盖原 application-core change 的 partial 实现 bean

### Requirement: mTLS 与多 region 客户端装配

kuship-console SHALL 支持「PEM 内联文本」与「文件路径」两种 mTLS 证书形式（与 rainbond-console Python 端 `Configuration` 类完全一致）：若 `region_info.ssl_ca_cert`/`cert_file`/`key_file` 字段值以 `/` 开头，视为文件路径直接读取；否则视为 PEM 内联文本，**在内存构造 `KeyStore`，不落盘**。HTTPS 校验由 `kuship.region.ssl-verify` 配置开关控制（默认 `false`，对应 Python `REGION_SSL_VERIFY=false`）。

#### Scenario: 内联 PEM 证书装配

- **WHEN** `region_info` 行的 `ssl_ca_cert` 是多行 PEM 文本（以 `-----BEGIN CERTIFICATE-----` 开头）
- **THEN** factory 在内存解析为 `X509Certificate`，构造 `KeyStore`（PKCS12），喂给 `SSLContext`，**不在文件系统创建任何 ssl 临时目录**

#### Scenario: 文件路径证书装配

- **WHEN** `region_info` 行的 `ssl_ca_cert` 值是绝对路径（如 `/etc/kuship/ca.pem`）
- **THEN** factory 直接从该路径读取证书文件加载

#### Scenario: ssl-verify 关闭

- **WHEN** `kuship.region.ssl-verify=false`（默认）
- **THEN** 装配的 `SSLContext` 使用 trust-all `TrustManager`，不验证服务端证书域名/链

#### Scenario: ssl-verify 开启

- **WHEN** `kuship.region.ssl-verify=true`（生产推荐）
- **THEN** 装配的 `SSLContext` 启用标准证书校验，使用 `region_info.ssl_ca_cert` 作为 trust store

### Requirement: Region API 错误映射

kuship-console SHALL 通过 `RegionApiResponseProcessor` 把 Go 后端响应映射为强类型 DTO 或对应异常；映射规则 100% 对齐 rainbond-console `regionapibaseclient.py:_check_status` 的行为；HTTP 状态码与业务 `code` 解耦：异常对象内部保留 `httpStatus` 仅供调试，对外响应体的 `code` 字段使用 region 响应 body 的 `code` 字段。

#### Scenario: 标准成功响应

- **WHEN** Go 后端返回 HTTP 200 + body `{"code":200,"msg":"success","msg_show":"OK","data":{"bean":{...}}}`
- **THEN** `RegionApiResponseProcessor` 反序列化 `data.bean` 为指定 DTO 类型并返回

#### Scenario: HTTP 200 + 空 body

- **WHEN** Go 后端返回 HTTP 200 但 body 为空或非合法 JSON
- **THEN** 抛 `RegionApiException`，`msg="request region api body is nil"`，`msg_show="集群请求网络异常"`

#### Scenario: 4xx-5xx + body 含 code

- **WHEN** Go 后端返回 HTTP 400/500 + body `{"code":404,"msg":"team not found","msg_show":"团队不存在"}`
- **THEN** 抛 `RegionApiException`，`code=404`，`msg="team not found"`，`msg_show="团队不存在"`

#### Scenario: HTTP 401 + bean.code=10400 → InvalidLicense

- **WHEN** Go 后端返回 HTTP 401 + body `{"data":{"bean":{"code":10400,"msg":"license expired"}}}`
- **THEN** 抛 `InvalidLicenseException`

#### Scenario: HTTP 409 + 频繁操作短语 → RegionApiFrequent

- **WHEN** Go 后端返回 HTTP 409 + body.msg 等于 `"操作过于频繁，请稍后再试"` 或 `"wait a moment please"` 或 `"just wait a moment"`（来自 `kuship.region.frequent-operation-messages` 配置，不区分大小写）
- **THEN** 抛 `RegionApiFrequentException`

#### Scenario: HTTP 409 + 非频繁操作短语 → RegionApiException

- **WHEN** Go 后端返回 HTTP 409 + body.msg 是其他业务消息
- **THEN** 抛 `RegionApiException`，`code=409`，`msg/msgShow` 透传 body

#### Scenario: HTTP 412 + 字面错误码

- **WHEN** Go 后端返回 HTTP 412 + body.msg 等于 `"cluster_lack_of_memory"`
- **THEN** 抛 `ClusterLackOfMemoryException`
- **AND** 同样匹配 `tenant_lack_of_memory`/`tenant_lack_of_cpu`/`tenant_quota_cpu_lack`/`tenant_quota_memory_lack`/`authorize_cluster_lack_of_memory`/`authorize_cluster_lack_of_node`/`authorize_cluster_lack_of_license`/`authorize_expiration_of_authorization` 共 9 种字面错误码，分别抛对应专门异常类

#### Scenario: socket 错误重试一次

- **WHEN** RestClient 调用过程中抛出 socket 类异常（IOException/SocketException/SocketTimeoutException）
- **THEN** factory 重试一次；若再次失败则抛 `RegionApiSocketException`

### Requirement: Region API 错误消息中文化

kuship-console SHALL 提供 `RegionErrorMsgEnricher` 把 Go 后端原始英文错误消息映射为用户友好的中文 `msg_show`；至少覆盖 rainbond-console Python 端 `build_region_error_msg_show` 当前已实现的三种模式：Helm 接管冲突、域名冲突、频繁操作短语。其他模式 `msg_show` 默认等于原始 `msg`。

#### Scenario: Helm 接管冲突中文化

- **WHEN** Go 后端返回错误 body.msg 含 `"...exists and cannot be imported into the current release: invalid ownership metadata;... meta.helm.sh/release-name\": must be set to \"my-release\";... meta.helm.sh/release-namespace\": must be set to \"my-ns\""`
- **THEN** 经 enricher 处理后 `msg_show` 含 `"命名空间 my-ns 中已存在资源 ...，且缺少 Helm 接管元数据，Release my-release 无法继续安装"`

#### Scenario: Helm 接管冲突无具体捕获组

- **WHEN** body.msg 含关键词 `"cannot be imported into the current release"` + `"invalid ownership metadata"` 但正则不匹配捕获组
- **THEN** `msg_show` 是兜底文案 `"命名空间中已存在同名资源，且缺少 Helm 接管元数据，请先删除冲突资源或补齐 Helm 元数据后重试"`

#### Scenario: 域名冲突中文化

- **WHEN** body.msg 形如 `"domain conflict: domain 'a.com' conflicts with existing domain 'b.com' in namespace 'ns' (resource: ingress/foo)"`
- **THEN** `msg_show` 包含中文域名冲突说明，含原始 domain/namespace/resource

#### Scenario: 其他消息原样透传

- **WHEN** body.msg 是任何不匹配上述两种模式的英文消息
- **THEN** `msg_show` 等于原始 `msg` 字符串（与 Python `build_region_error_msg_show` 默认行为一致）

### Requirement: 14 个资源域接口骨架

kuship-console SHALL 在 `cn.kuship.console.infrastructure.region.api` 包下提供 14 个资源域接口（`TenantOperations`、`ServiceOperations`、`ServiceDependencyOperations`、`ServiceEnvOperations`、`ServicePortOperations`、`ServiceVolumeOperations`、`ServiceProbeOperations`、`ServiceLifecycleOperations`、`ServiceStatusOperations`、`ServiceLogOperations`、`EventOperations`、`HelmOperations`、`GatewayOperations`、`ClusterOperations`），每个接口声明该资源域的全部 method 签名（约 25 个）+ JavaDoc 标注预期实现 change 名；每个未实现的 method 实现类方法体 SHALL 仅 `throw new UnsupportedOperationException("not yet implemented; will be filled in by migrate-console-* change")`。

#### Scenario: 接口存在且签名稳定

- **WHEN** 检查 `cn.kuship.console.infrastructure.region.api` 包
- **THEN** 14 个接口均存在；每个接口的 method 签名包括 `regionName` / `tenantName` 等 path/query 参数与对应的请求/响应类型

#### Scenario: 未实现 method 抛 UnsupportedOperationException

- **WHEN** 调用 `serviceOperations.createService("region-1", "tenant-x", body)`（本 change 不实现）
- **THEN** 抛 `UnsupportedOperationException`，message 包含 `"not yet implemented"` 与对应 change 名（`"migrate-console-app-create"`）

#### Scenario: JavaDoc 标注预期实现 change

- **WHEN** 阅读 `ServiceLifecycleOperations.startService` 等未实现 method 的 JavaDoc
- **THEN** 文档明确指出该 method 由哪个后续 change（如 `migrate-console-app-runtime`）落地

### Requirement: TenantOperations 完整能力（示范）

kuship-console SHALL 完整实现 `TenantOperations` 接口的 5 个 method 作为基础设施可用性示范：`createTenant`、`deleteTenant`、`getTenantResources`、`getRegionPublickey`、`getRegionLabels`；每个 method 配套强类型 DTO（`CreateTenantReq` / `TenantResourcesResp` / `RegionPublickeyResp` / `RegionLabelsResp`）；URL 路径与 HTTP method 严格对齐 rainbond-console `regionapi.py` 中对应 method 的实现。

#### Scenario: createTenant POST 调用

- **WHEN** 调用 `tenantOperations.createTenant("region-1", new CreateTenantReq("name","id","ent-id","ns",false))`
- **THEN** 向 `/v2/tenants` 发起 POST，body 含 tenant 名/id/enterpriseId/namespace/bind_existing
- **AND** 解析 `data.bean` 为 `TenantResourcesResp`（或对应类型）返回

#### Scenario: deleteTenant DELETE 调用

- **WHEN** 调用 `tenantOperations.deleteTenant("region-1", "tenant-x")`
- **THEN** 向 `/v2/tenants/tenant-x` 发起 DELETE，无 body

#### Scenario: getTenantResources GET 调用

- **WHEN** 调用 `tenantOperations.getTenantResources("region-1", "tenant-x", "ent-id")`
- **THEN** 向 `/v2/tenants/tenant-x/res?enterprise_id=ent-id` 发起 GET，反序列化为 `TenantResourcesResp`

#### Scenario: 4xx 错误自动映射

- **WHEN** Go 后端对 `createTenant` 返回 HTTP 409 + body `{"code":409,"msg":"tenant already exists","msg_show":"团队已存在"}`
- **THEN** 抛 `RegionApiException(code=409, msg="tenant already exists", msgShow="团队已存在")`
- **AND** 由 `GlobalExceptionHandler` 映射为对外响应体 `{"code":409,"msg":"tenant already exists","msg_show":"团队已存在","data":{"bean":{},"list":[]}}`

### Requirement: region_info 表只读访问

kuship-console SHALL 通过双层访问 `region_info` 表：

1. **infrastructure 层只读**：`infrastructure/region/repository/RegionInfoRepository`（基于 `JdbcTemplate`）保留只读路径；用 `RegionInfoDto` 输出。该路径专供 `RegionClientFactory` 装配 mTLS RestClient 使用，**不引入 hibernate session 副作用**（避免 region client 调用时触发 lazy-loading）。
2. **业务层读写**：`migrate-console-region-cluster` 起新增 `modules/region/entity/RegionInfo` JPA `@Entity` + `RegionInfoEntityRepository`，承载 enterprise/regions CRUD 端点的写入路径。

两者共享同一张 `region_info` 表；schema 演进权仍归 rainbond-console。

#### Scenario: 按 region_name 查询（infrastructure 层）

- **WHEN** 调用 `regionInfoRepository.findByName("region-1")`
- **THEN** 返回 `Optional<RegionInfoDto>` 含 `regionId/regionName/url/wsurl/sslCaCert/certFile/keyFile/enterpriseId` 等字段（snake_case 列名映射到 camelCase Java 字段）

#### Scenario: 不存在返回空（infrastructure 层）

- **WHEN** 调用 `regionInfoRepository.findByName("not-exist")`
- **THEN** 返回 `Optional.empty()`

#### Scenario: 业务层 JPA entity 写入

- **WHEN** controller `regionInfoEntityRepo.save(new RegionInfo(...))`
- **THEN** Hibernate INSERT/UPDATE 该行，符合 `region_info` schema（21 列含 region_id/region_name/url/wsurl/cert/...）；同 connection pool 视图下 JdbcTemplate 立刻可见

#### Scenario: 删除 region 时强制 evict client cache

- **WHEN** 业务层 `regionInfoEntityRepo.delete(region)` 后
- **THEN** 服务层 SHALL 显式调 `RegionClientFactory.evict(enterpriseId, regionName)`，否则缓存里的旧 RestClient 仍可用于已删 region

### Requirement: 用户认证端点（兼容 rainbond-console 登录链路）

kuship-console SHALL 提供 `/console/users/login`、`/console/users/logout`、`/console/users/register`、`/console/users/changepwd` 四个公开/半公开端点，请求/响应 JSON 形状与 rainbond-console 一致，使前端 `kuship-ui` 现有 `services/user.js` 中的调用代码不需改动即可工作。

`/console/users/login` 与 `/console/users/register` SHALL 加入 SecurityConfig 的 permitAll 白名单；`/console/users/logout` 与 `/console/users/changepwd` 要求 JWT 认证。

#### Scenario: 用户名+密码登录成功签发 token

- **WHEN** 客户端发送 `POST /console/users/login`，body `{"nick_name":"admin","password":"goodrain"}`
- **THEN** 响应 `code=200`、`data.bean.token` 为 HS256 签名的 JWT，payload 含 `user_id`、`username`、`email`、`exp`（now+3650 天）、`orig_iat`
- **AND** 响应同时返回 `data.bean.user` 字段，含 `user_id` / `nick_name` / `email` / `enterprise_id`

#### Scenario: 错误密码登录失败

- **WHEN** 客户端发送 `POST /console/users/login` 带错误 password
- **THEN** 响应 `code=400`、`msg_show="用户名或密码错误"`，HTTP 200（业务码与 HTTP 解耦）

#### Scenario: 注册新用户

- **WHEN** 客户端发送 `POST /console/users/register`，body 含 `nick_name`、`email`、`password`（≥8 字符）、`real_name`、`phone`（可选）
- **THEN** 响应 `code=200`，新用户写入 `user_info` 表，密码字段经 `LegacyPasswordEncoder.encode(email + password)` 计算
- **AND** 自动给该用户绑定到默认 enterprise（首个 `tenant_enterprise.is_active=1` 的）；自动建一个默认 team（namespace 用 `nick_name + "-default"`）并把用户作为 owner

#### Scenario: 登出

- **WHEN** 已登录用户发送 `POST /console/users/logout`
- **THEN** 响应 `code=200`、`msg="logout success"`；服务端不维护 session，仅是占位响应

#### Scenario: 改密码

- **WHEN** 已登录用户发送 `POST /console/users/changepwd`，body `{"old_password":"x","new_password":"y"}`
- **THEN** 旧密码经 `LegacyPasswordEncoder.matches` 校验，校验通过则更新 `user_info.password`；新密码长度 < 8 触发 400

### Requirement: 密码哈希算法兼容 rainbond-console（自定义 SHA-224 截断）

kuship-console SHALL 提供 `LegacyPasswordEncoder`（实现 Spring Security 的 `PasswordEncoder` 接口），完全复刻 rainbond-console `www/utils/crypt.py::encrypt_passwd` 的算法：

```
input  = email + rawPassword          // length >= 8 强制
word   = ord(input[7]) + input + ord(input[5]) + "goodrain" + (ord(input[2]) / 7)
hash   = SHA-224(word.utf8).hex
result = hash[:16]                    // 截断 16 字符 hex
```

`encode(rawPassword)` 与 `matches(rawPassword, encoded)` 必须与 rainbond-console 输出二进制一致；针对 `input.length < 8`（如 email 为空、或调用方传 raw 而非 email+raw）SHALL 抛 `IllegalArgumentException("password material too short")`。

#### Scenario: 与 rainbond-console 输出一致

- **WHEN** 给定 `email="alice@example.com"`、`rawPassword="goodrain"`
- **THEN** `encode("alice@example.comgoodrain")` 输出与 rainbond-console Python `encrypt_passwd("alice@example.comgoodrain")` 完全相同（写一组 fixture 用例覆盖至少 5 组真实样本）

#### Scenario: matches 通过 hash 比对

- **WHEN** 数据库已存 `user_info.password="abcd1234efgh5678"`（rainbond 写入的样本）
- **AND** 调用 `matches("rawPwd", "abcd1234efgh5678")`
- **THEN** 返回 true 当且仅当 `encode("rawPwd")` 等于 `"abcd1234efgh5678"`

### Requirement: 跨服务 JWT 互认（rainbond-console 与 kuship-console 共用 SECRET_KEY）

kuship-console 的 `JwtIssuer` SHALL 使用与 rainbond-console 相同的 SECRET_KEY、相同的 HS256 算法、相同的 payload claim 命名（`user_id`、`username`、`email`、`exp`、`orig_iat`），且 `exp` 默认 `iat + 3650 天`。当 rainbond-console 与 kuship-console 配置同一个 `JWT_SECRET_KEY` 环境变量时：rainbond 签发的 token 在 kuship 端可解析，反之亦然，无需 token 转换层。

#### Scenario: rainbond token 在 kuship 解析成功

- **WHEN** 一个由 rainbond-console 签发的 token（payload `{"user_id":1,"username":"admin","email":"x@y","exp":...,"orig_iat":...}`）作为 `Authorization: GRJWT <token>` 发送到 kuship-console 任意需认证端点
- **THEN** 鉴权通过，`RequestContext.userId=1` 且实际从 `user_info` 表加载用户进 RequestContext

#### Scenario: kuship 签发的 token 反向兼容 rainbond

- **WHEN** kuship-console 通过 `/users/login` 签发 token
- **AND** 该 token 被 rainbond-console（同 SECRET_KEY 部署）的 `rest_framework_jwt` 解析
- **THEN** rainbond 鉴权通过（payload 字段一致）

### Requirement: 用户自我端点

kuship-console SHALL 提供 `/console/users/details`、`/console/users/team_details`、`/console/users/query`、`/console/users/custom_configs` 四个端点，返回结构与 rainbond-console 兼容。

#### Scenario: GET /console/users/details

- **WHEN** 已登录用户访问
- **THEN** 响应 `code=200`、`data.bean` 含 `user_id`、`nick_name`、`email`、`phone`、`real_name`、`is_user_enable`（=`is_active`）、`enterprise_id`、`origin`（默认 `"register"`）、`logo`

#### Scenario: GET /console/users/team_details

- **WHEN** 已登录用户访问
- **THEN** 响应 `data.list` 为该用户所有 team 的列表，每项含 `team_name`、`team_alias`、`tenant_id`、`region_list`（关联 `team_region` 联表查得）、`role_infos`（该用户在该 team 中的所有 role）

#### Scenario: 用户模糊搜索

- **WHEN** 客户端访问 `GET /console/users/query?query=abc`
- **THEN** 响应 `data.list` 为 `nick_name` / `email` / `phone` 任一字段 like `%abc%` 的用户分页结果

#### Scenario: 自定义配置读写

- **WHEN** `PUT /console/users/custom_configs` body `{"key":"theme","value":"dark"}`
- **THEN** 写入 `user_info.custom_configs` 字段（JSON 列）；后续 `GET` 同端点能读到

### Requirement: 用户 PAT (UserAccessToken) 管理

kuship-console SHALL 提供 `/console/users/access-token` 端点，让用户管理 Personal Access Token。本 change 仅落 PAT 的生成与列表/删除；PAT 在 `/openapi/v1/*` 上的鉴权由 `migrate-openapi-v1` change 实现。

#### Scenario: 生成 PAT

- **WHEN** `POST /console/users/access-token`，body `{"note":"my-cli","expire":"30d"}`
- **THEN** 写入 `user_access_key` 表（`user_id` + `note` + `access_key` + `expire_time`）；返回 `data.bean.access_key`（明文，仅此一次）

#### Scenario: 列表与删除

- **WHEN** `GET /console/users/access-token`
- **THEN** 列出当前用户所有 PAT（不含明文，仅 `access_key` 前 6 + 后 4 字符 mask）
- **AND** `DELETE /console/users/access-token/{id}` 删除该 PAT

### Requirement: Team 基础 CRUD

kuship-console SHALL 提供 team（=tenant）的核心增删改查端点：`POST /console/teams/init`、`PUT /console/teams/{team_name}`、`DELETE /console/teams/{team_name}`、`POST /console/teams/{team_name}/exit`。Team 的 `tenant_name`、`tenant_id`、`namespace`、`enterprise_id` 字段语义与 rainbond-console 完全一致。

#### Scenario: 创建 team

- **WHEN** `POST /console/teams/init`，body `{"team_name":"alpha","team_alias":"Alpha 团队","useable_regions":"region-1","enterprise_id":"ent-x","namespace":"alpha"}`
- **THEN** 在 `tenant_info` 表插入新行，当前用户作为 owner（`creator` 字段写入 `user_id`），并在 `team_region` 表写入团队-集群绑定记录

#### Scenario: 改 team 名

- **WHEN** owner 用户 `PUT /console/teams/alpha`，body `{"team_alias":"Alpha-V2"}`
- **THEN** `tenant_info.tenant_alias` 更新为 `"Alpha-V2"`

#### Scenario: 普通成员无权修改 team

- **WHEN** 非 owner 用户 `PUT /console/teams/alpha`
- **THEN** 响应 `code=403`、`msg_show="无该团队管理权限"`

#### Scenario: 退出 team

- **WHEN** 普通成员 `POST /console/teams/alpha/exit`
- **THEN** 从 `user_role` 删除该用户在该 team 的所有角色绑定；如该用户是该 team 唯一 owner 则拒绝退出（`code=400`、`msg_show="团队仅剩一位 owner，无法退出"`）

### Requirement: Team 成员管理

kuship-console SHALL 提供 `/console/teams/{team_name}/users`、`/console/teams/{team_name}/notjoinusers`、`/console/teams/{team_name}/users/batch/delete`、`/console/teams/{team_name}/pemtransfer` 四个端点。

#### Scenario: 列出成员

- **WHEN** `GET /console/teams/alpha/users?page=1&page_size=10`
- **THEN** 响应 `data.list` 为该 team 所有成员，每项含 `user_id`、`nick_name`、`email`、`role_infos[]`（该成员在 team 中的所有 role）；`data.bean.total` 为总数

#### Scenario: 添加成员

- **WHEN** 管理员 `POST /console/teams/alpha/users`，body `{"user_ids":[10,11],"role_ids":[1]}`
- **THEN** 在 `user_role` 表为每个 user_id × role_id 写入关联记录

#### Scenario: 批量删除成员

- **WHEN** 管理员 `DELETE /console/teams/alpha/users/batch/delete`，body `{"user_ids":[10,11]}`
- **THEN** 删除 `user_role` 中这些用户在该 team 的所有角色绑定

#### Scenario: 转让 owner

- **WHEN** 当前 owner `POST /console/teams/alpha/pemtransfer`，body `{"user_id":11}`
- **THEN** `tenant_info.creator` 改为 11；原 owner 仍保留普通成员身份

### Requirement: Team 角色与权限管理

kuship-console SHALL 提供 team 角色 CRUD 与 role-perm / user-role 关联管理：

- `GET/POST /console/teams/{team_name}/roles`
- `GET/PUT/DELETE /console/teams/{team_name}/roles/{role_id}`
- `GET /console/teams/{team_name}/roles/perms`（所有 role 的 perms 矩阵）
- `GET/PUT /console/teams/{team_name}/roles/{role_id}/perms`
- `GET /console/teams/{team_name}/users/roles`（user-role 矩阵）
- `PUT/DELETE /console/teams/{team_name}/users/{user_id}/roles`

#### Scenario: 创建自定义角色

- **WHEN** 管理员 `POST /console/teams/alpha/roles`，body `{"name":"developer","perm_codes":["app_overview","app_create"]}`
- **THEN** 在 `role_info` 写入新角色，并在 `role_perms` 写入对应权限码关联

#### Scenario: 修改角色权限

- **WHEN** 管理员 `PUT /console/teams/alpha/roles/5/perms`，body `{"perm_codes":["app_overview"]}`
- **THEN** 删除该 role 在 `role_perms` 的所有旧关联，写入新关联；同时 evict 缓存 key=`tenant:alpha:role:5`

#### Scenario: 修改用户角色后权限缓存失效

- **WHEN** 管理员 `PUT /console/teams/alpha/users/10/roles`，body `{"role_ids":[5,6]}`
- **THEN** 删除该用户在该 team 的所有 user_role 行，写入新行
- **AND** evict `user-team-perms` 缓存 key=`10:alpha`，下次该用户调任意 team-scoped 端点会重查

### Requirement: 权限码注解（@RequirePerm）与 AOP 拦截

kuship-console SHALL 提供 `@RequirePerm("perm_code")` 与 `@RequireEnterpriseAdmin` 两个方法级注解 + 对应 Spring AOP `@Aspect` 切面，自动从 `RequestContext.userId` + `RequestContext.tenantName` 查权限。

#### Scenario: 拥有权限的用户通过

- **WHEN** controller 方法标注 `@RequirePerm("app_create")`
- **AND** 调用方在 path `/teams/alpha/...`，且该用户在 team alpha 拥有 `app_create` 权限
- **THEN** 切面校验通过，方法正常执行

#### Scenario: 缺少权限触发 403

- **WHEN** 同一方法被无权限的用户调用
- **THEN** 切面抛 `ServiceHandleException(403, "no permission", "您无操作此功能的权限")`，由 `GlobalExceptionHandler` 包装为 `general_message` 响应

#### Scenario: enterprise admin 注解

- **WHEN** controller 方法标注 `@RequireEnterpriseAdmin`
- **AND** `RequestContext.enterpriseId` 与 `enterprise_user_perm` 表中该用户的 `identity='admin'` 行匹配
- **THEN** 校验通过

#### Scenario: 缓存读权限矩阵

- **WHEN** 同一用户 + 同一 team 在 60 秒内连续调多个 `@RequirePerm` 方法
- **THEN** 仅第一次查询数据库，后续从 Spring Cache `user-team-perms` 直接读

### Requirement: Enterprise 基本信息端点（含未授权可访问）

kuship-console SHALL 提供 `/console/enterprise/info`（公开，登录页用）与 `/console/enterprises`、`/console/enterprise/{enterprise_id}`、`/console/enterprise/{enterprise_id}/teams`、`/console/enterprise/{enterprise_id}/myteams` 等端点。

`/console/enterprise/info` SHALL 加入 SecurityConfig 的 permitAll 白名单。

#### Scenario: 未登录访问 enterprise/info

- **WHEN** 客户端不带 Authorization 调 `GET /console/enterprise/info`
- **THEN** 响应 `code=200`、`data.bean` 含 `enterprise_id`、`enterprise_alias`、`logo`、`is_active`（仅平台默认 enterprise 的脱敏信息，不含 token / 用户列表 / 权限）

#### Scenario: 当前用户的所有 enterprise

- **WHEN** 已登录用户调 `GET /console/enterprises`
- **THEN** 响应 `data.list` 为该用户所属所有 enterprise（通过 `enterprise_user_perm` 关联）

#### Scenario: enterprise 内 team 列表

- **WHEN** 已登录用户调 `GET /console/enterprise/ent-x/teams?page=1&page_size=10`
- **THEN** 响应 `data.list` 为该 enterprise 下所有 team；`data.bean.total` 为总数

### Requirement: Enterprise 用户管理与跨 team 角色

kuship-console SHALL 提供 enterprise 内用户管理与 admin 管理端点：

- `GET/POST /console/enterprise/{enterprise_id}/users`
- `PUT/DELETE /console/enterprise/{enterprise_id}/user/{user_id}`
- `GET /console/enterprise/{enterprise_id}/user/{user_id}/teams`
- `GET/POST/DELETE /console/enterprise/{enterprise_id}/admin/user[/{user_id}]`
- `GET /console/enterprise/{enterprise_id}/admin/roles`
- `GET/PUT /console/enterprise/{enterprise_id}/users/{user_id}/teams/{tenant_name}/roles`
- `POST /console/enterprise/admin/add-user`
- `POST /console/enterprise/admin/join-team`

#### Scenario: 企业管理员创建用户

- **WHEN** enterprise admin `POST /console/enterprise/ent-x/users`，body `{"user_name":"bob","email":"bob@example.com","password":"abcd1234"}`
- **THEN** 写入 `user_info` 表，`enterprise_id="ent-x"`；密码经 `LegacyPasswordEncoder.encode` 写入

#### Scenario: 普通用户无权创建

- **WHEN** 非 admin 用户调用同端点
- **THEN** 响应 `code=403`、`msg_show="您无操作此功能的权限"`

#### Scenario: 跨 team 修改用户角色

- **WHEN** enterprise admin `PUT /console/enterprise/ent-x/users/10/teams/alpha/roles`，body `{"role_ids":[5]}`
- **THEN** 重写该用户在该 team 的角色绑定（即使调用方不是该 team 成员，凭 enterprise admin 身份也可操作）

### Requirement: 权限元数据端点

kuship-console SHALL 提供 `GET /console/perms`（公开，前端权限树渲染用）与 `POST /console/init/perms`（启动时确保 `perm_info` / `role_info` 默认数据存在）。

`GET /console/perms` SHALL 加入 SecurityConfig 的 permitAll 白名单。

#### Scenario: 列出所有权限码

- **WHEN** `GET /console/perms`
- **THEN** 响应 `data.bean` 形如 rainbond-console 输出的嵌套结构 `{"enterprise":{"admin":{"perms":[...]}},"team":{"owner":{"perms":[...]},...},"app":{...},...}`，至少含 170+ 权限码

#### Scenario: 权限码与 rainbond 一致

- **WHEN** 启动时 `PermsInitService` 执行
- **THEN** `perm_info` 表中所有 rainbond `console.utils.perms.py` 中定义的权限码均存在（按 `code` 主键 upsert）

### Requirement: 12 张账户/团队/权限表的 JPA Entity

kuship-console SHALL 为以下 12 张表提供 `@Entity` + `Repository`，与 `console` 数据库（rainbond-console 拥有 schema 演进权）共享：`user_info`、`tenant_info`、`tenant_enterprise`、`enterprise_user_perm`、`console_sys_perm_group`、`console_sys_perms_info`、`role_info`、`role_perms`、`user_role`、`tenant_user_perm`、`user_access_key`、`team_region`。

`hibernate.ddl-auto` 保持 `validate`；启动时 schema 不一致 SHALL 立即抛错并阻止启动。

#### Scenario: 启动时 schema 校验

- **WHEN** 启动时 `tenant_info` 表少了 rainbond 必备字段（如 `enterprise_id`）
- **THEN** Hibernate 抛 `SchemaManagementException`，应用启动失败

#### Scenario: 不发任何 DDL

- **WHEN** kuship-console 启动连上 console DB
- **THEN** 整个启动期间数据库 binlog 没有 `CREATE TABLE` / `ALTER TABLE` 语句

### Requirement: 集群（region）生命周期管理

kuship-console SHALL 提供 enterprise 级别的集群增删改查端点，路径完全沿用 rainbond `/console/enterprise/{enterprise_id}/regions`：

- `GET /` —— 列出 enterprise 内所有 region；可选 query 参数 `status` 与 `check_status`
- `POST /` —— 添加新集群：解析 kubectl-format YAML token，落 `region_info` 表
- `GET /{region_id}` —— 集群详情
- `PUT /{region_id}` —— 修改集群（alias / desc / url / cert）
- `DELETE /{region_id}` —— 删除集群（前提：无 team 在该 region 上有 namespace 绑定）

写入端点 SHALL 要求 `@RequireEnterpriseAdmin`；GET 列表/详情对所有已认证用户开放。

#### Scenario: 添加集群解析 YAML token

- **WHEN** `POST /console/enterprise/ent-1/regions`，body `{"region_name":"r1","region_alias":"R1","desc":"测试","token":"<yaml>","region_type":["public"]}`
- **AND** YAML 内容包含 `ca.pem`、`client.pem`、`client.key.pem`、`apiAddress`、`websocketAddress`、`defaultDomainSuffix`、`defaultTCPHost` 全部 7 个字段
- **THEN** 写入 `region_info` 表新行，自动生成 `region_id`（UUID 36 字符）；响应 `code=200`、`data.bean` 含完整 region 详情

#### Scenario: 添加集群 token 缺字段触发 400

- **WHEN** YAML 缺 `ca.pem` 字段
- **THEN** 响应 `code=400`、`msg_show="CA 证书不存在"`、`msg="ca.pem not found"`

#### Scenario: 列出 enterprise 集群

- **WHEN** 已登录用户 `GET /console/enterprise/ent-1/regions`
- **THEN** 响应 `data.list` 为该 enterprise 下所有 region；不含敏感字段（cert_file/key_file 在 level=safe 下被脱敏）

#### Scenario: 删除有 team 在用的集群拒绝

- **WHEN** 删除 `region_id=r1`，但 `tenant_region` 表中存在 `region_name="r1"` 的关联
- **THEN** 响应 `code=400`、`msg_show="该集群仍有团队在使用，请先关闭团队的集群关联"`

#### Scenario: 删除集群后 evict region client cache

- **WHEN** 成功删除某 region
- **THEN** 服务端 SHALL 调用 `RegionClientFactory.evict(enterpriseId, regionName)` 立即失效缓存的 RestClient

### Requirement: 集群 License 端点

kuship-console SHALL 提供集群授权管理端点（路径 `/console/enterprise/{enterprise_id}` 下）：

- `GET /licenses` —— enterprise 维度的 license summary
- `GET /regions/{region_name}/license/cluster-id` —— 拿当前集群的 cluster-id
- `POST /regions/{region_name}/license/activate` —— 提交 license 激活
- `GET /regions/{region_name}/license/status` —— 当前授权状态

实际授权数据由 region API 转发；console 仅做 pass-through + 错误码透传。激活端点 SHALL 要求 `@RequireEnterpriseAdmin`。

#### Scenario: 拿 cluster-id

- **WHEN** `GET /console/enterprise/ent-1/regions/r1/license/cluster-id`
- **THEN** kuship-console 调用 region API（通过 `ClusterOperations.getClusterId`）；响应 `data.bean.cluster_id` 为 region 返回的字符串

#### Scenario: License 激活成功

- **WHEN** admin `POST /.../license/activate` body `{"license":"<base64-license>"}`
- **THEN** kuship-console 转发给 region API，region 验证通过返回 `code=200`，kuship 透传

#### Scenario: License 激活失败的业务码透传

- **WHEN** region 返回 `code=10400`（InvalidLicense）
- **THEN** `RegionApiResponseProcessor` 抛 `InvalidLicenseException`，由 `GlobalExceptionHandler` 包装为 general_message 响应（`code=10400`、`msg_show="授权码无效"`）

### Requirement: 团队-集群关联（开通/查询）

kuship-console SHALL 提供 team-region 关联管理端点（路径 `/console/teams/{team_name}/region`）：

- `GET /query` —— 当前 team 已开通的集群（`tenant_region` 表中该 team 的所有行）
- `GET /unopen` —— 当前 team 未开通的集群（enterprise 全集 - 已开通集合）
- `POST /` —— 开通集群

开通集群 SHALL 写 `tenant_region` 表 + 调 `TenantOperations.createTenant` 在 region 侧建 namespace；任一步失败均 rollback（事务包裹）。开通端点 SHALL 要求 `@RequirePerm("team_region_install")`，查询端点 SHALL 要求 `@RequirePerm("team_region_describe")`。

#### Scenario: 开通集群

- **WHEN** team admin `POST /console/teams/alpha/region` body `{"region_name":"r1"}`
- **THEN** 写入 `tenant_region` 行（`tenant_id=alpha.tenantId`、`region_name=r1`、`is_active=1`）；调用 `TenantOperations.createTenant(r1, ent-1, ...)` 在 region 侧建 namespace；响应 `code=200`

#### Scenario: 重复开通幂等

- **WHEN** team 已开通某 region，再次 POST 开通
- **THEN** 不重复写 row，响应 `code=200` + 提示 `msg_show="已开通该集群"`

#### Scenario: region 创建 namespace 失败 rollback

- **WHEN** 开通过程中 `TenantOperations.createTenant` 抛 RegionApiException
- **THEN** `tenant_region` 行不写入；异常透传给上层

### Requirement: Region 元信息查询端点

kuship-console SHALL 提供以下 region 元数据查询端点：

- `GET /console/regions` —— 全局 region 列表（简版，不含 cert）
- `GET /console/teams/{team_name}/regions/{region_name}/publickey` —— 集群公钥
- `GET /console/teams/{team_name}/regions/{region_name}/features` —— 集群 feature flag 列表
- `GET /console/teams/{tenant_name}/protocols` —— 协议枚举（HTTP/TCP/gRPC 等）

`features` / `publickey` 路径 SHALL 转发给 region API（通过 `ClusterOperations.getRegionFeatures` / `TenantOperations.getRegionPublickey`）。

#### Scenario: 查询集群 feature

- **WHEN** `GET /console/teams/alpha/regions/r1/features`
- **THEN** kuship-console 调 region 取得 feature 列表（如 `["TCP-LB","KUBEBLOCKS-ENABLED"]`），透传 `data.list`

#### Scenario: 查询集群公钥

- **WHEN** `GET /console/teams/alpha/regions/r1/publickey`
- **THEN** 响应 `data.bean.public_key` 为 region 返回的 PEM 公钥字符串

### Requirement: 命名空间与资源查询端点

kuship-console SHALL 提供集群 namespace / resource 查询端点：

- `GET /console/teams/cluster/namespaces` —— 当前用户上下文集群的所有 namespace（用于"绑定已有 namespace"场景）
- `GET /console/enterprise/{enterprise_id}/regions/{region_id}/namespace` —— enterprise 维度的 namespace 列表（含 content 过滤）
- `GET /console/enterprise/{enterprise_id}/regions/{region_id}/resource` —— region 资源汇总（CPU/内存使用率）
- `GET /console/enterprise/{enterprise_id}/regions/{region_id}/tenants` —— 集群里所有 tenant 的资源占用
- `POST /console/enterprise/{enterprise_id}/regions/{region_id}/tenants/{tenant_name}/limit` —— 设置 tenant 资源上限

所有端点 SHALL 转发给 region API（通过 `ClusterOperations`）。set-limit 端点 SHALL 要求 `@RequireEnterpriseAdmin`。

#### Scenario: 列出集群 namespaces

- **WHEN** `GET /console/teams/cluster/namespaces`
- **THEN** kuship-console 调 region API `GET /v2/cluster/namespaces`，透传 `data.bean` 为 namespace 数组

### Requirement: 平台级镜像仓库凭据（Hub Registry，复用 team_registry_auths 表）

kuship-console SHALL 提供平台级镜像仓库凭据管理端点（路径 `/console/hub/registry`）。

**实现注意**：rainbond `HubRegistryView` 实际复用 `team_registry_auths` 表（通过 `tenant_id=''` + `region_name=''` 区分平台级），不是独立表。kuship 端 SHALL 同样复用单一 `TeamRegistryAuth` entity，平台级写入时强制 `tenantId=""` + `regionName=""`。

- `GET /` —— 列表
- `POST /` —— 新增
- `PUT /` —— 修改（body 含 secret_id 定位）
- `DELETE /?secret_id={id}` —— 删除
- `GET /image` —— 列出仓库镜像

写入端点 SHALL 要求 sys_admin（`RequestContext.sysAdmin=true`）；查询端点对所有认证用户开放。

#### Scenario: sys_admin 添加平台 registry

- **WHEN** sys_admin `POST /console/hub/registry`，body `{"domain":"docker.io","username":"u","password":"p","hub_type":"docker","secret_id":"<uuid>"}`
- **THEN** 写入 `team_registry_auths` 表，`tenant_id=""` 且 `region_name=""` 标识为平台级

#### Scenario: 普通用户无权修改

- **WHEN** 非 sys_admin `POST /console/hub/registry`
- **THEN** 响应 `code=403`、`msg_show="您无操作此功能的权限"`

#### Scenario: GET /image 调 registry HTTP API

- **WHEN** `GET /console/hub/registry/image?secret_id=x`
- **THEN** kuship-console 用该 secret 凭据调 registry 的 `/v2/_catalog` 端点；响应 `data.list` 为镜像名数组；调用失败时返回 `data.list=[]` + warning 日志

### Requirement: 团队级镜像仓库凭据（Team Registry Auth）

kuship-console SHALL 提供团队级镜像仓库凭据管理端点（路径 `/console/teams/{team_name}/registry/auth`）：

- `GET /` `POST /` —— 列表 / 新增
- `GET /{secret_id}` `PUT /{secret_id}` `DELETE /{secret_id}` —— 单条 RUD

所有端点 SHALL 要求 `@RequirePerm("team_registry_auth")`。表名为 `team_registry_auths`（注意末尾 `s`，rainbond 历史拼写）。

#### Scenario: team admin 添加 registry auth

- **WHEN** team admin `POST /console/teams/alpha/registry/auth`
- **AND** body `{"hub_type":"docker","domain":"private.io","username":"u","password":"p","region_name":"r1"}`
- **THEN** 写入 `team_registry_auths` 表，`secret_id` 自动生成（UUID 32 字符）

### Requirement: ClusterOperations 6 method 完整实现

kuship-console SHALL 在已有的 `ClusterOperations` 接口骨架（migrate-console-region-client）上完整实现以下 6 method：

- `getClusterId(regionName, enterpriseId)` → `GET /v2/cluster/cluster-id`
- `activateLicense(regionName, enterpriseId, licenseBody)` → `POST /v2/cluster/license-activate`
- `getLicenseStatus(regionName, enterpriseId)` → `GET /v2/cluster/license-status`
- `getRegionFeatures(regionName, tenantName)` → `GET /v2/cluster/features`
- `getRegionNamespaces(regionName, enterpriseId, content)` → `GET /v2/cluster/namespaces`
- `getRegionResources(regionName, enterpriseId)` → `GET /v2/cluster/resource`

实现 SHALL 用 `RegionClientFactory.getClient(regionName, enterpriseId)` 拿 RestClient，遵循 `TenantOperationsImpl` 的 `exchange + RegionApiResponseProcessor` 模式，错误自动映射为 RegionApiException 族。

#### Scenario: getClusterId 转发成功

- **WHEN** `clusterOperations.getClusterId("r1", "ent-1")`，region 返回 `{"code":200,"data":{"bean":{"cluster_id":"abc"}}}`
- **THEN** 返回 `"abc"`

#### Scenario: 网络异常透传

- **WHEN** region 不可达（连接被拒）
- **THEN** 抛 `RegionApiSocketException`，由 `GlobalExceptionHandler` 包装为 503 + `msg_show="集群网络异常"`

### Requirement: RegionInfo entity 与现有 RegionInfoDto 共存

kuship-console SHALL 引入 `cn.kuship.console.modules.region.entity.RegionInfo`（JPA `@Entity`）+ `RegionInfoEntityRepository`（继承 `JpaRepository`）用于业务层 CRUD；同时保留 `cn.kuship.console.infrastructure.region.repository.RegionInfoRepository`（`JdbcTemplate` 只读）服务于 `RegionClientFactory`，两者共享同一张 `region_info` 表。

#### Scenario: 业务层用 JPA entity

- **WHEN** controller 注入 `RegionInfoEntityRepository.findByRegionName("r1")`
- **THEN** 返回 `Optional<RegionInfo>` JPA entity

#### Scenario: infrastructure 层用 JdbcTemplate

- **WHEN** `RegionClientFactory` 内部装配 RestClient
- **THEN** 仍调用 `RegionInfoRepository.findByEnterpriseAndName(eid, name)` 拿 `RegionInfoDto`，不进入 hibernate session

#### Scenario: 写入路径仅走 JPA

- **WHEN** controller 通过 `regionInfoEntityRepo.save(region)` 写入或更新
- **THEN** Hibernate 写入数据库；JdbcTemplate 端立刻可见（同 connection pool）

### Requirement: 应用（Group/Application）主体管理

kuship-console SHALL 提供应用主体的 CRUD 端点（路径 `/console/teams/{team_name}/groups`）。应用对应 rainbond `service_group` 表；一个 application 含 N 个组件（通过 `service_group_relation` 关联）。

- `GET /` —— 当前 team 下所有 application（不含组件详情，仅元数据）
- `POST /` —— 创建空 application
- `GET /{app_id}` —— 详情
- `PUT /{app_id}` —— 修改 group_name / note / governance_mode / k8s_app
- `DELETE /{app_id}` —— 删除（前提：无组件归属）
- `GET /{app_id}/status` —— 整体状态（聚合所有组件状态）
- `GET /{app_id}/component_names` —— 该 app 下所有组件简版列表
- `GET /{app_id}/governancemode` `PUT /{app_id}/governancemode` —— 治理模式

读取端点 SHALL 要求 `@RequirePerm("app_overview_describe")`；创建/修改/删除 SHALL 要求 `@RequirePerm("app_create_perms")`。

#### Scenario: 列出团队应用

- **WHEN** 已登录用户 `GET /console/teams/alpha/groups`
- **THEN** 响应 `data.list` 为该 team 下所有 application；每项含 `app_id` / `group_name` / `note` / `governance_mode` / `k8s_app` / `create_time`

#### Scenario: 创建空应用

- **WHEN** team admin `POST /console/teams/alpha/groups` body `{"group_name":"my-app","note":"test","region_name":"r1","k8s_app":"my-k8s-app"}`
- **THEN** 写入 `service_group` 表，`tenant_id=team.tenantId`，`is_default=false`，`order_index=0`，`app_type="rainbond"`；响应 `data.bean.app_id` 为新建主键

#### Scenario: 删除有组件的应用拒绝

- **WHEN** 删除某 application，`service_group_relation` 表中存在该 group_id 的关联
- **THEN** 响应 `code=400`、`msg_show="该应用下仍有组件，请先迁移或删除组件"`

### Requirement: 组件（Service/Component）查询与基础信息

kuship-console SHALL 提供组件查询端点（路径 `/console/teams/{team_name}/apps/{service_alias}`）：

- `GET /detail` —— 完整字段（tenant_service 全字段，敏感字段如 secret 脱敏）
- `GET /brief` —— 简版（service_id / service_alias / service_cname / k8s_component_name / image / version / state）
- `GET /status` —— 运行状态（转发 region API）
- `GET /group` —— 当前所属 application（通过 service_group_relation 反查）
- `PUT /group` —— 迁移到另一 application
- `GET /keyword` —— 组件 keyword

#### Scenario: 组件详情脱敏

- **WHEN** `GET /console/teams/alpha/apps/svc-1/detail`
- **THEN** 响应 `data.bean` 含组件全字段，但 `secret` 字段输出占位（如 `***16 chars***`），不暴露明文

#### Scenario: 迁移组件到另一应用

- **WHEN** `PUT /console/teams/alpha/apps/svc-1/group` body `{"app_id":99}`
- **THEN** `service_group_relation` 表中 service_id=svc-1 的行被更新，group_id 改为 99；事务包裹

### Requirement: 环境变量（envs）管理

kuship-console SHALL 提供环境变量 CRUD 端点（路径 `/console/teams/{team_name}/apps/{service_alias}/envs`）：

- `GET /` —— 列表（query 参数 `scope=inner|outer` 过滤）
- `POST /` —— 新增
- `PUT /{env_id}` —— 修改
- `DELETE /{env_id}` —— 删除

环境变量写入**仅写本地表**（不调 region API）—— 与 rainbond 一致：env 在组件下次启动 / 重新部署时由 region 端读 console DB 拉取。

读取要求 `@RequirePerm("app_overview_env")`；写入同。

#### Scenario: 新增 env

- **WHEN** `POST .../envs` body `{"name":"DB_HOST","attr_name":"DB_HOST","attr_value":"172.20.0.10","is_change":true,"scope":"inner"}`
- **THEN** 写入 `tenant_service_env_var`，响应 `data.bean.id` 为新建 ID

#### Scenario: 唯一性校验

- **WHEN** 新增的 attr_name 在同 service_id+scope 下已存在
- **THEN** 响应 `code=400`、`msg_show="环境变量名已存在"`

### Requirement: 端口（ports）管理

kuship-console SHALL 提供端口 CRUD 端点（路径 `/console/teams/{team_name}/apps/{service_alias}/ports`）：

- `GET /` —— 列表
- `POST /` —— 新增（先调 region API 同步 → 再写本地）
- `DELETE /{port}` —— 删除（同上）
- `PUT /{port}` —— 修改 alias / inner-service / outer-service / k8s_service_name

#### Scenario: 新增端口同步 region

- **WHEN** `POST .../ports` body `{"port":8080,"protocol":"http","port_alias":"WEB","is_inner_service":false,"is_outer_service":true}`
- **THEN** 先调 `ServicePortOperations.addPort(regionName, tenantName, serviceAlias, ...)` 同步 K8s Service；region 成功后写 `tenant_services_port` 表

#### Scenario: region 同步失败本地不写

- **WHEN** region API 失败抛 `RegionApiException`
- **THEN** 本地 `tenant_services_port` 表不写入；异常透传给前端（`code=region 业务码`，`msg_show=region 返回的中文消息）

#### Scenario: 启用 outer-service 调用 inner→outer 切换

- **WHEN** `PUT .../ports/8080` body `{"is_outer_service":true}` 且原值为 false
- **THEN** 调用 `ServicePortOperations.openOuter(...)` 并更新本地行

### Requirement: 存储卷（volumes）管理

kuship-console SHALL 提供存储卷 CRUD 端点（路径 `/console/teams/{team_name}/apps/{service_alias}/volumes`）：

- `GET /` —— 列表
- `POST /` —— 新增
- `DELETE /{volume_id}` —— 删除
- `PUT /{volume_id}` —— 修改 capacity / access-mode

写入策略：先 region API 后本地。

#### Scenario: 新增 volume

- **WHEN** `POST .../volumes` body `{"volume_name":"data","volume_type":"share-file","volume_path":"/data","volume_capacity":10,"access_mode":"RWX"}`
- **THEN** 调 `ServiceVolumeOperations.addVolume(...)` 同步 region，成功后写 `tenant_service_volume` 表

### Requirement: 依赖（dependency）管理

kuship-console SHALL 提供依赖管理端点（路径 `/console/teams/{team_name}/apps/{service_alias}`）：

- `GET /dependency-list` —— 当前组件依赖的服务
- `GET /dependency-reverse` —— 依赖当前组件的服务
- `POST /dependency` —— 新增依赖
- `DELETE /dependency/{dep_service_id}` —— 删除依赖

通过 `tenant_service_relation` 表（service_id ↔ dep_service_id）维护。

#### Scenario: 新增依赖

- **WHEN** `POST .../dependency` body `{"dep_service_id":"svc-2","dep_order":0}`
- **THEN** 调 `ServiceDependencyOperations.addDependency(...)` 同步；写 `tenant_service_relation` 行

#### Scenario: 反向查询

- **WHEN** `GET /console/teams/alpha/apps/svc-2/dependency-reverse`
- **THEN** 响应 `data.list` 为所有 `tenant_service_relation.dep_service_id="svc-2"` 的 service_id 集合

### Requirement: 探针（probe）管理

kuship-console SHALL 提供探针 CRUD 端点（路径 `/console/teams/{team_name}/apps/{service_alias}/probe`）：

- `GET /` —— 列表（mode = liveness / readiness / startup）
- `POST /` —— 新增 / 修改（同 mode 软去重：先 delete 同 service_id+mode → 再 insert）
- `DELETE /{probe_id}` —— 删除

写入：先 region API 后本地。

#### Scenario: 同 mode 软去重

- **WHEN** `POST .../probe` body `{"mode":"liveness","scheme":"http","path":"/","port":8080,...}`
- **AND** 该组件已存在 mode=liveness 的探针
- **THEN** 旧探针被删除（本地+region）；新探针写入；最终保持 mode=liveness 仅一条

### Requirement: 应用核心 7 张表的 JPA Entity

kuship-console SHALL 引入以下 7 个 `@Entity`：`ServiceGroup`（service_group）、`ServiceGroupRelation`（service_group_relation）、`TenantService`（tenant_service ~50 列）、`TenantServiceEnvVar`（tenant_service_env_var）、`TenantServicesPort`（tenant_services_port）、`TenantServiceVolume`（tenant_service_volume）、`TenantServiceRelation`（tenant_service_relation）、`ServiceProbe`（service_probe）。

`TenantService` entity 一次性映射全部 50+ 列（含 build/code/dockerfile 等不在本 change scope 的字段），避免后续 change 反复扩 entity；hibernate validate 启动时 schema 一次性通过。

#### Scenario: 启动 schema 校验通过

- **WHEN** kuship-console 启动连真实 console DB
- **THEN** `hibernate.ddl-auto=validate` 对 8 张新增 entity（service_group / service_group_relation / tenant_service / tenant_service_env_var / tenant_services_port / tenant_service_volume / tenant_service_relation / service_probe）全部通过

#### Scenario: TenantService 主键策略

- **WHEN** TenantService entity 主键映射 `ID` 列
- **THEN** Java 字段 `id` 是 `Integer`（INT 4 字节，对齐 Django INT），`@GeneratedValue(strategy=IDENTITY)`

### Requirement: ServiceOperations 与 5 子资源 Operations 实现

kuship-console SHALL 在已有的 6 个域接口（ServiceOperations / ServiceEnvOperations / ServicePortOperations / ServiceVolumeOperations / ServiceDependencyOperations / ServiceProbeOperations）上完整实现以下 method：

- `ServiceOperations`：`getServiceStatus(regionName, tenantName, serviceAlias)`、`getServiceDetail(...)`、`batchGetServicesStatus(...)`
- `ServiceEnvOperations`：env 同步通常本地优先（rainbond 行为），仅在 controller 提交"重启 / build"时 region 自取；本 change 无需实现 env 端 API（保持 default unsupported），controller 仅写本地
- `ServicePortOperations`：`addPort` / `deletePort` / `openOuter` / `closeOuter` / `openInner` / `closeInner` / `updatePort`
- `ServiceVolumeOperations`：`addVolume` / `deleteVolume` / `updateVolume`
- `ServiceDependencyOperations`：`addDependency` / `deleteDependency`
- `ServiceProbeOperations`：`addProbe` / `deleteProbe` / `updateProbe`

每个 Impl 类用 `@Primary @Service` 覆盖 default 占位 bean，沿用 `TenantOperationsImpl` 的 `RegionClientFactory + exchange + RegionApiResponseProcessor` 模式。

#### Scenario: ServicePortOperations.addPort 转发

- **WHEN** controller 调 `servicePortOperations.addPort("r1", "alpha", "svc-1", req)`
- **THEN** 实现走 `POST /v2/tenants/{tenantName}/services/{serviceAlias}/ports` region API；成功后返回 void；失败抛 RegionApiException

#### Scenario: 14 接口骨架进度更新

- **WHEN** 检查 `cn.kuship.console.modules` 包
- **THEN** 至少 ServiceOperations / ServicePortOperations / ServiceVolumeOperations / ServiceDependencyOperations / ServiceProbeOperations 5 个 Impl 类存在（@Primary @Service），每个含 ~5 个核心 method

### Requirement: 应用创建端点（image / source_code / third_party 3 种来源）

kuship-console SHALL 提供 3 种来源的组件创建端点（路径 `/console/teams/{team_name}/apps`）：

- `POST /docker_run` —— 基于镜像创建（最简）
- `POST /source_code` —— 基于 Git 仓库创建
- `POST /third_party` —— 创建第三方组件（外部 endpoint）

所有创建端点 SHALL 要求 `@RequirePerm("app_overview_create")`；service_id 由 console 用 `UuidGenerator.makeUuid()` 生成 32 字符 UUID；service_alias 默认 `"gr" + service_id[:6]`，前端可显式提供覆盖；写入策略：先 console DB 后 region API（事务包裹，region 失败本地回滚）。

#### Scenario: 基于镜像创建

- **WHEN** team admin `POST /console/teams/alpha/apps/docker_run` body `{"image":"nginx:latest","port":80,"cmd":"nginx -g 'daemon off;'","group_id":1,"region_name":"r1","service_cname":"my-nginx"}`
- **THEN** 写入 `tenant_service` 行（service_id 自动生成、service_origin="assistant"、service_source="docker_run"）+ `service_source` 行（image/cmd 留底）+ `service_group_relation` 行（关联 application）
- **AND** 调 `ServiceOperations.createService` 在 region 侧建 K8s deployment / service / configmap
- **AND** 响应 `data.bean.service_id` / `service_alias` / `k8s_component_name` 完整字段

#### Scenario: region 创建失败回滚

- **WHEN** console 写入成功但 region API 抛 `RegionApiException`
- **THEN** 事务 rollback —— `tenant_service` / `service_source` / `service_group_relation` 全部不写入；异常透传给前端

#### Scenario: 基于 Git 仓库创建

- **WHEN** `POST /console/teams/alpha/apps/source_code` body `{"git_url":"https://github.com/x/y.git","code_version":"main","build_strategy":"buildpack","language":"java","group_id":1,...}`
- **THEN** 写入 tenant_service + service_source（含 git_url / code_version / build_strategy / language）+ service_group_relation；调 region createService

#### Scenario: 第三方组件创建

- **WHEN** `POST /console/teams/alpha/apps/third_party` body `{"endpoints":[{"address":"172.20.0.99","port":3306}],"service_cname":"external-mysql","group_id":1}`
- **THEN** 写入 tenant_service（service_source="third_party"）+ service_source（extend_info 含 endpoints JSON）；不调 region createService（第三方组件不需要 K8s deployment）

#### Scenario: service_alias 冲突

- **WHEN** 用户显式传与已存在组件相同的 service_alias
- **THEN** unique constraint 触发 → Spring 转 `ServiceHandleException(400, msg_show="组件别名已存在")`

### Requirement: 创建前/后检查（check 异步链路）

kuship-console SHALL 提供组件检查端点（路径 `/console/teams/{team_name}/apps/{service_alias}`）：

- `POST /check` —— 触发对组件的代码检查（异步），返回 `check_uuid`
- `GET /get_check_uuid` —— 查询当前 `check_uuid`
- `PUT /check_update` —— 用 region 返回的推荐配置更新组件（语言 / 端口 / env / build_strategy）

#### Scenario: 触发检查

- **WHEN** `POST /console/teams/alpha/apps/gr123456/check`
- **THEN** 调 region API `POST /v2/tenants/{tenant}/code-check`，region 返回 check_uuid + event_id；console 把 `check_uuid` 写入 `tenant_service.check_uuid` 字段；响应 `data.bean.check_uuid`

#### Scenario: 查询检查 UUID

- **WHEN** `GET /console/teams/alpha/apps/gr123456/get_check_uuid`
- **THEN** 响应 `data.bean.check_uuid` 为 `tenant_service.check_uuid` 字段值（前端基于此轮询 region event 状态）

#### Scenario: 应用检查推荐配置

- **WHEN** `PUT /console/teams/alpha/apps/gr123456/check_update` body `{"language":"java","ports":[{"port":8080,"protocol":"http"}],"envs":[...]}`
- **THEN** 把 language / ports / envs 持久化到 service_source / tenant_services_port / tenant_service_env_var 表（事务包裹）

### Requirement: 应用构建与编译参数

kuship-console SHALL 提供构建相关端点：

- `POST /console/teams/{team_name}/apps/{service_alias}/build` —— 触发组件构建
- `GET /console/teams/{team_name}/apps/{service_alias}/code/branch` —— 列出 git 仓库可用分支
- `GET /console/teams/{team_name}/apps/{service_alias}/compile_env` —— 查询编译环境变量
- `PUT /console/teams/{team_name}/apps/{service_alias}/compile_env` —— 修改编译环境变量

build 端点 SHALL 调用 `ServiceOperations.buildService(...)` 触发 region 构建任务，返回 region 给的 `event_id`；前端轮询 event 状态由 app-runtime change 落地。

#### Scenario: 触发构建

- **WHEN** `POST /console/teams/alpha/apps/gr123456/build` body `{"event_id":"<可选>","kind":"build_from_source_code"}`
- **THEN** 调 region buildService；响应 `data.bean.event_id` 为 region 给的事件 ID
- **AND** `tenant_service.deploy_version` 写入新版本号；`update_time` 刷新

#### Scenario: 修改 compile_env

- **WHEN** `PUT .../compile_env` body `{"BUILD_OPTS":"-Xmx2g","JAVA_OPTS":"-server"}`
- **THEN** compile_env 持久化到 `tenant_service_env_var` 表（scope="build"）

### Requirement: 应用删除（软删除归档）

kuship-console SHALL 提供组件删除端点：

- `POST /console/teams/{team_name}/apps/{service_alias}/delete` —— 删除组件

删除策略：
1. 调 `ServiceOperations.deleteService(...)` 释放 region 端 K8s 资源
2. 写 `tenant_service_delete` 软删除归档行（保留所有历史字段 + delete_time + 操作人 user_id）
3. 删除 `tenant_service` / `service_source` / `service_group_relation` / 相关 envs / ports / volumes / dependency / probe 行（事务包裹）

#### Scenario: 正常删除

- **WHEN** team admin `POST /console/teams/alpha/apps/gr123456/delete`
- **THEN** region 释放成功 → 软删除归档 + 本地表清理 → 响应 `code=200`

#### Scenario: region 删除失败本地不动

- **WHEN** region 抛 RegionApiException
- **THEN** 本地未做任何修改；异常透传

### Requirement: 应用创建相关 2 张新 Entity

kuship-console SHALL 引入 2 个 `@Entity`：`ServiceSourceInfo`（对应 `service_source` 表，存放创建参数 git/image/dockerfile/build_strategy 等）+ `TenantServiceInfoDelete`（对应 `tenant_service_delete` 表，组件软删除归档）。

`tenant_service_delete` schema 与 `tenant_service` 类似 + 多 `delete_time` / `app_name` / `app_id` 字段；本 change 一次性映射所有列。

#### Scenario: 创建组件时同步写 service_source

- **WHEN** 任一 3 种创建端点写入新组件
- **THEN** `service_source` 表也写入对应行（service_id 关联，user_name/password 仅 source_code git 鉴权场景填）

#### Scenario: 启动 schema 校验通过

- **WHEN** kuship-console 启动连真实 console DB
- **THEN** hibernate validate 对 2 张新增 entity 全部通过

### Requirement: 组件生命周期动作端点

kuship-console SHALL 暴露与 rainbond-console 100% 兼容的组件生命周期端点，覆盖启动 / 停止 / 暂停 / 取消暂停 / 重启 / 部署 / 回滚 / 升级 共 8 个动作；每次调用 SHALL 先经 `@RequirePerm` 校验对应权限码（如 `APP_OVERVIEW_START` / `APP_OVERVIEW_STOP` 等），再委托 region API 触发异步任务，本地仅更新 `tenant_service.update_time` 与 `update_version`，不存储运行态。

#### Scenario: POST /console/teams/{team}/apps/{alias}/start 启动组件

- **WHEN** 用户对状态为 closed 的组件调 POST `/console/teams/team1/apps/grabc123/start`
- **THEN** kuship-console 校验 `APP_OVERVIEW_START` 权限通过
- **AND** 调用 `ServiceOperations.startService(region, tenantName, serviceAlias, userId)`
- **AND** region 返回 `event_id`
- **AND** kuship-console 把 `tenant_service.update_version += 1` 并保存
- **AND** 响应 `{"code":200,"data":{"bean":{"event_id":"<id>"}}}`

#### Scenario: POST /stop /restart /pause /unpause 等 7 兄弟端点

- **WHEN** 调用 `/stop`、`/pause`、`/unpause`、`/vm_web`（unpause 别名）、`/restart`、`/deploy`、`/rollback`、`/upgrade` 任一端点
- **THEN** 流程同 start，区别仅在 region 子 path 与权限码（stop=APP_OVERVIEW_STOP / restart=APP_OVERVIEW_RESTART / deploy=APP_OVERVIEW_DEPLOY / rollback=APP_UPGRADE / upgrade=APP_UPGRADE）
- **AND** 任一端点 region 4xx/5xx 时直接抛 `ServiceHandleException` 给前端

### Requirement: 组件扩缩容端点

kuship-console SHALL 实现 4 类扩缩容端点：垂直伸缩（vertical）/ 水平伸缩（horizontal）/ 通用 scaling / 部署类型切换（deploytype），事务内同时更新本地 `tenant_service` 配置字段并通知 region 重算 deployment spec。

#### Scenario: POST /vertical 修改 CPU/内存

- **WHEN** 调 `/console/teams/team1/apps/grabc123/vertical` body `{"new_memory":2048,"new_gpu":0,"new_cpu":1000}`
- **THEN** kuship-console 在事务内更新 `tenant_service.min_memory=2048, min_cpu=1000, container_gpu=0`
- **AND** 同事务调 region `/v2/tenants/team1/services/grabc123/vertical`
- **AND** region 失败时事务回滚，旧值保留

#### Scenario: POST /horizontal 修改副本数

- **WHEN** 调 `/console/teams/team1/apps/grabc123/horizontal` body `{"new_node":3}`
- **THEN** 更新 `tenant_service.min_node=3`
- **AND** 通知 region

#### Scenario: POST /scaling 一次同时改 CPU/内存/副本

- **WHEN** 调 `/scaling` body 同时含 `new_cpu`/`new_memory`/`new_node`/`new_gpu`
- **THEN** 一次事务内全部写本地 + 一次 region 调用

#### Scenario: PUT /deploytype 修改部署类型

- **WHEN** 调 `PUT /apps/grabc123/deploytype` body `{"extend_method":"stateful_singleton"}`
- **THEN** kuship-console 更新 `tenant_service.extend_method` 并通知 region

### Requirement: 批量动作端点

kuship-console SHALL 实现 `POST /console/teams/{team}/batch_actions` 端点，支持对一组 service_id（或 service_alias）一次性执行 start / stop / restart / deploy 动作，部分失败时返回 207 风格的 success/failed 列表。

#### Scenario: 全部成功

- **WHEN** 调 `/batch_actions` body `{"action":"start","service_ids":["s1","s2","s3"]}`
- **THEN** 按 region_name 分组分别调 region `/v2/tenants/{tenantName}/batchactions`
- **AND** 全部 region 调用成功时返回 `{"code":200,"data":{"bean":{"success":["s1","s2","s3"],"failed":[]}}}`

#### Scenario: 部分失败

- **WHEN** 其中一组 region 调用失败
- **THEN** 返回 `{"code":200,"data":{"bean":{"success":[...],"failed":[{"service_id":"s2","msg":"region error"}]}}}`
- **AND** 不抛异常，前端可逐条渲染状态

### Requirement: 组件删除增强端点

kuship-console SHALL 实现 3 个删除增强端点：批量软删除（batch_delete）/ 强制本地清理（again_delete）/ 应用整组删除（groupapp/{group_id}/delete），全部复用 `AppDeleteService` 的归档与本地清理逻辑。

#### Scenario: DELETE /batch_delete 批量软删除

- **WHEN** 调 `DELETE /console/teams/team1/batch_delete` body `{"service_ids":["s1","s2"]}`
- **THEN** 对每个 service_id 调用 `AppDeleteService.delete(...)`
- **AND** 每个组件先调 region 释放 K8s 资源，再归档 `tenant_service_delete`
- **AND** 任一失败时返回失败 service_id 列表，已成功的不回滚

#### Scenario: DELETE /again_delete 强制本地清理

- **WHEN** region 已不存在某 service，调 `/again_delete` body `{"service_ids":["s1"]}`
- **THEN** 跳过 region 调用，直接归档 + 清理本地表

#### Scenario: DELETE /groupapp/{group_id}/delete 整组删除

- **WHEN** 调 `DELETE /console/teams/team1/groupapp/42/delete`
- **THEN** kuship-console 列出 group_id=42 下所有 service_id，按批量软删除流程逐个处理
- **AND** 同时删除 `service_group` (group_id=42) 自身与所有 `service_group_relation`

### Requirement: 组件属性变更端点

kuship-console SHALL 实现 `PUT /apps/{alias}/change/service_name`、`PUT /apps/{alias}/set/is_upgrade`、`PUT /apps/{alias}/extend_method` 三个属性变更端点，每次仅修改本地一个标量字段，不调 region。

#### Scenario: PUT /change/service_name 修改组件名

- **WHEN** 调 body `{"service_name":"my-mysql","k8s_component_name":"my-mysql"}`
- **THEN** kuship-console 更新 `tenant_service.service_name` 与 `k8s_component_name`，不调 region

#### Scenario: PUT /set/is_upgrade 切换是否随 region 升级

- **WHEN** 调 body `{"is_upgrade":true}`
- **THEN** 更新 `tenant_service.build_upgrade=true`

#### Scenario: PUT /extend_method 修改伸缩模式

- **WHEN** 调 body `{"extend_method":"stateful_multiple"}`
- **THEN** 更新 `tenant_service.extend_method` 并校验值在 `{stateless_multiple, stateful_singleton, stateful_multiple, job, cronjob}` 集合内，否则 400

### Requirement: 组件状态与 Pod 查询端点

kuship-console SHALL 实现 `GET /apps/{alias}/status`、`GET /apps/{alias}/pods`、`POST /apps/{alias}/pods/detail`、`GET /apps/{alias}/pods/{pod_name}`、`GET /groups/{tenant_name}/{app_id}/pods/{pod_name}` 共 5 个状态/Pod 端点，全部由 region GET 透传，本地不持久化运行态。

#### Scenario: GET /apps/{alias}/status 返回单组件状态

- **WHEN** 调 `/console/teams/team1/apps/grabc123/status`
- **THEN** kuship-console 调 region `/v2/tenants/team1/services/grabc123/status` 透传响应
- **AND** 响应包含 `status`、`update_time`、`pod_num` 等字段

#### Scenario: GET /apps/{alias}/pods 返回 Pod 列表

- **WHEN** 调 `/apps/grabc123/pods`
- **THEN** kuship-console 调 region `/v2/tenants/team1/services/grabc123/pods` 拿到 Pod 列表
- **AND** 每个 Pod 含 `pod_name`、`pod_ip`、`status`、`container_image`、`start_time` 字段

#### Scenario: GET /pods/{pod_name} 返回 Pod 详情

- **WHEN** 调 `/apps/grabc123/pods/podname-xxx-yyy`
- **THEN** kuship-console 透传 region 详情，含 `containers[*]`、`events[*]`、`resource{cpu_limit,memory_limit}` 三段

### Requirement: 组件事件与日志端点

kuship-console SHALL 实现 `GET /apps/{alias}/events`、`GET /apps/{alias}/event_log`、`GET /apps/{alias}/log`、`GET /apps/{alias}/log_instance`、`GET /apps/{alias}/history_log`、`GET /apps/{alias}/logs`、`GET /teams/{team}/events`、`GET /teams/{team}/events/{eventId}/log`、`POST /log_proxy` 共 9 个端点，全部走 region HTTP 同步轮询，不引入 SSE / WebSocket。

#### Scenario: GET /apps/{alias}/events 列出事件分页

- **WHEN** 调 `/apps/grabc123/events?page=1&page_size=10`
- **THEN** 透传 region `/v2/tenants/team1/services/grabc123/events` 返回 `{list:[...], total:N}`

#### Scenario: GET /apps/{alias}/event_log 单事件详情

- **WHEN** 调 `/apps/grabc123/event_log?event_id=eid1234`
- **THEN** kuship-console 调 region `/v2/event_log` 透传 stdout 数组

#### Scenario: GET /apps/{alias}/log_instance 拿 WebSocket 直连凭证

- **WHEN** 调 `/apps/grabc123/log_instance`
- **THEN** kuship-console 调 region 拿到 `{host, path, token}` 三元组直接透传给前端
- **AND** 前端拿 token 自行 WebSocket 直连 region，不经过 console

#### Scenario: POST /log_proxy 通用日志代理

- **WHEN** 用户上传 region path body 调 `/log_proxy`
- **THEN** kuship-console 用 JWT 校验通过后透传到对应 region 同名 path

### Requirement: 组件监控查询端点

kuship-console SHALL 实现 `GET /apps/{alias}/monitor/query`、`GET /apps/{alias}/monitor/query_range`、`GET /groups/{group_id}/monitor/batch_query`、`GET /apps/{alias}/resource`、`GET /apps/{alias}/trace`、`POST /apps/{alias}/trace`、`DELETE /apps/{alias}/trace` 共 7 个监控端点，全部透传到 region 的 Prometheus 代理路径。

#### Scenario: GET /monitor/query 透传 PromQL

- **WHEN** 调 `/apps/grabc123/monitor/query?query=cpu_usage{service_id="x"}&time=1715000000`
- **THEN** kuship-console 透传到 region `/v2/tenants/team1/monitor/query` 同样的 query string
- **AND** 响应原样返回 region 响应（不重新包 ApiResult，保持 Prometheus JSON）

#### Scenario: GET /monitor/query_range 时间区间监控

- **WHEN** 调 `/monitor/query_range?query=...&start=...&end=...&step=15s`
- **THEN** 透传 region `/v2/tenants/team1/monitor/query_range`

#### Scenario: GET /groups/{group_id}/monitor/batch_query 批量

- **WHEN** 调 `/groups/42/monitor/batch_query?query=cpu`
- **THEN** kuship-console 列出 group_id=42 下所有 service_id，构造批量 query 调 region

#### Scenario: GET /apps/{alias}/resource 资源占用

- **WHEN** 调 `/apps/grabc123/resource`
- **THEN** kuship-console 调 region 拿到 `{cpu_used, memory_used, cpu_limit, memory_limit}` 透传

### Requirement: 弹性伸缩规则 CRUD 端点

kuship-console SHALL 实现 `GET/POST /apps/{alias}/xparules`、`GET/PUT /apps/{alias}/xparules/{rule_id}`、`GET /apps/{alias}/xparecords` 共 5 个 autoscaler 端点；规则数据 SHALL 双写本地 `autoscaler_rules` + `autoscaler_rule_metrics` 两表，并同步至 region；伸缩历史（xparecords）SHALL 由 region 持有，console 仅做 GET 透传。

#### Scenario: POST /xparules 创建自动伸缩规则

- **WHEN** 调 `/apps/grabc123/xparules` body `{"min_replicas":1,"max_replicas":5,"metrics":[{"metric_type":"resource_metrics","metric_name":"cpu","metric_target_type":"average_value","metric_target_value":500}]}`
- **THEN** kuship-console 在事务内 INSERT `autoscaler_rules` 1 行 + `autoscaler_rule_metrics` 1 行
- **AND** 同事务 POST region `/v2/tenants/team1/services/grabc123/xparules`
- **AND** region 失败时事务回滚

#### Scenario: GET /xparules 列出本组件全部规则

- **WHEN** 调 `/apps/grabc123/xparules`
- **THEN** kuship-console 仅查本地 `autoscaler_rules` 与 `autoscaler_rule_metrics` 关联返回，不调 region

#### Scenario: PUT /xparules/{rule_id} 修改规则

- **WHEN** 调 PUT body 同创建
- **THEN** kuship-console 更新本地 → PUT region；region 失败回滚

#### Scenario: GET /xparules/{rule_id} 单个规则详情

- **WHEN** 调 GET
- **THEN** 仅查本地，返回规则基础信息 + metrics 数组

#### Scenario: GET /xparecords 历史伸缩记录

- **WHEN** 调 `/apps/grabc123/xparecords`
- **THEN** 透传 region 返回的伸缩事件历史，console 不存

### Requirement: 应用拓扑与访问入口端点

kuship-console SHALL 实现 `GET /groups/{group_id}/topological`、`GET /groups/{group_id}/topological/internet`、`GET /apps/{alias}/visit`、`GET /groups/{group_id}/visit`、`GET /service_alarm` 共 5 个查询端点，全部 region 透传 + 本地 group/component 元数据 enrich。

#### Scenario: GET /groups/{group_id}/topological 返回拓扑图

- **WHEN** 调 `/console/teams/team1/groups/42/topological`
- **THEN** kuship-console 调 region 拿到 service 节点+依赖边，本地补 service_cname / icon / extend_method 三个字段

#### Scenario: GET /apps/{alias}/visit 返回单组件访问入口

- **WHEN** 调 `/apps/grabc123/visit`
- **THEN** 透传 region 返回 `{access_urls:[...], domain_urls:[...]}`

### Requirement: 运行时模块新增 4 张表的 JPA Entity 与 Repository

kuship-console SHALL 新增 `AutoscalerRule`（autoscaler_rules）、`AutoscalerRuleMetric`（autoscaler_rule_metrics）两个 JPA Entity 与对应 Repository，主键类型统一使用 `Integer`（与 Django INT 4 字节对齐），存放于 `cn.kuship.console.modules.appruntime.entity` 与 `repository` 包；与 region 表名/列名严格对齐，不引入新 schema。

#### Scenario: AutoscalerRule Entity 映射 autoscaler_rules 表

- **WHEN** Hibernate 启动加载 entity
- **THEN** `AutoscalerRule.@Table(name="autoscaler_rules")` 含 `rule_id`(32 char UUID)、`service_id`、`enable`、`xpa_type`、`min_replicas`、`max_replicas`、`create_time`
- **AND** PK `id` 为 Integer 自增

#### Scenario: AutoscalerRuleMetric Entity 映射 autoscaler_rule_metrics 表

- **WHEN** Hibernate 启动加载 entity
- **THEN** `AutoscalerRuleMetric.@Table(name="autoscaler_rule_metrics")` 含 `rule_id`、`metric_type`、`metric_name`、`metric_target_type`、`metric_target_value`
- **AND** 通过 `rule_id`（非 FK，逻辑关联）与 AutoscalerRule 关联

#### Scenario: ddl-auto=validate 启动通过

- **WHEN** 应用启动连真实 MySQL（rainbond docker compose 已建表）
- **THEN** Hibernate ddl-auto=validate 不报缺列错误

### Requirement: 14 接口骨架的 4 个运行时子接口实现

kuship-console SHALL 实现 14 接口骨架中已声明的 4 个运行时子接口共 22 method：
- `ServiceLifecycleOperations` 10 method（startService/stopService/restartService/upgradeService/rollback/horizontalUpgrade/verticalUpgrade/changeMemory/pauseService/unpauseService）
- `ServiceStatusOperations` 6 method（serviceStatus/checkServiceStatus/getServicePods/podDetail/getDynamicServicesPods/getUserServiceAbnormalStatus）
- `ServiceLogOperations` 3 method（getServiceLogs/getServiceLogFiles/getDockerLogInstance）
- `EventOperations` 3 method（getEventLog/getTargetEventsList/getMyteamsEventsList）

实现位置 `cn.kuship.console.modules.appruntime.api.*Impl`，每个 Impl 标注 `@Service @Primary` 替换 14 接口骨架的默认 unsupported 实现。

#### Scenario: 10 个生命周期 method 覆盖

- **WHEN** 调用 `ServiceLifecycleOperations.startService` / `stopService` / `restartService` / `upgradeService` / `rollback` / `horizontalUpgrade` / `verticalUpgrade` / `changeMemory` / `pauseService` / `unpauseService`
- **THEN** 每个 method 用 `RegionApiSupport.exchange(lambda)` 模板包装 RestClient 调用
- **AND** path 模式统一为 `/v2/tenants/{teamName}/services/{serviceAlias}/<action>`

#### Scenario: 6 个状态/Pod method 覆盖

- **WHEN** 调用 `ServiceStatusOperations.serviceStatus` / `checkServiceStatus` / `getServicePods` / `podDetail` / `getDynamicServicesPods` / `getUserServiceAbnormalStatus`
- **THEN** 全部使用 `RestClient.GET` 或 `RestClient.POST` 透传响应

#### Scenario: 6 个日志/事件 method 覆盖

- **WHEN** 调用 `ServiceLogOperations.getServiceLogs` / `getServiceLogFiles` / `getDockerLogInstance` 与 `EventOperations.getEventLog` / `getTargetEventsList` / `getMyteamsEventsList`
- **THEN** 全部使用 `RestClient.GET` 透传响应；query string 由 Map<String,Object> body 序列化拼接

### Requirement: 新增 MonitorOperations 与 AutoscalerOperations 两接口

kuship-console SHALL 在 `infrastructure.region.api` 包下新增 `MonitorOperations`（4 method：query / queryRange / batchQuery / getServiceResources）与 `AutoscalerOperations`（4 method：createRule / updateRule / deleteRule / listScalingRecords）两个接口及对应 `@Primary @Service` 实现类，共享 `RegionApiSupport` helper。

#### Scenario: MonitorOperations 4 method 全部透传

- **WHEN** controller 调用 `monitorOperations.query(region, teamName, queryString)`
- **THEN** Impl 使用 `RestClient.get().uri(...).retrieve()` 透传 query string，并把响应反序列化为 `Map<String,Object>`

#### Scenario: AutoscalerOperations 4 method 全部对应 region xparules CRUD

- **WHEN** controller 调用 `autoscalerOperations.createRule(region, teamName, serviceAlias, body)`
- **THEN** Impl 调 region `/v2/tenants/{tenantName}/services/{serviceAlias}/xparules` POST
- **AND** 同样模式覆盖 PUT / DELETE / GET records

### Requirement: 运行时模块测试覆盖

kuship-console SHALL 提供至少 4 类集成测试覆盖运行时核心：
1. `AppLifecycleIntegrationTest`：start/stop/restart 端点对 mock RestClient 的请求路径与权限校验断言；
2. `AppScalingIntegrationTest`：vertical/horizontal/scaling 写本地 + region 调用断言；
3. `AppAutoscalerIntegrationTest`：xparules POST/GET/PUT 双写本地 autoscaler_rules + autoscaler_rule_metrics；
4. `AppEventLogIntegrationTest`：events / event_log / log / log_instance 透传响应字段保留断言。

#### Scenario: 集成测试全部使用真实 MySQL

- **WHEN** 在 docker-compose 启动后跑 `mvn -Dtest='cn.kuship.console.modules.appruntime.**' test`
- **THEN** 每类测试在 `@BeforeAll` 用高位 user_id（9090xx）插入 user/team/service 数据
- **AND** 在 `@AfterAll` 清理避免数据残留
- **AND** 全部用例通过

#### Scenario: Region API 用 MockRestClient 桩

- **WHEN** 测试不依赖真实 region
- **THEN** 测试类用 `@MockBean` 替换 `ServiceOperations` / `MonitorOperations` / `AutoscalerOperations`
- **AND** 用 `Mockito.when(...).thenReturn(...)` 桩响应

### Requirement: 应用模板（rainbond_center_app）CRUD 端点

kuship-console SHALL 暴露与 rainbond-console 100% 兼容的应用模板 CRUD 端点，覆盖 `/enterprise/{eid}/app-models{,/<app_id>{,/version/<ver>}}` 共 5 path 的 GET/POST/PUT/DELETE。模板数据 SHALL 通过 `RainbondCenterApp`（rainbond_center_app）+ `RainbondCenterAppVersion`（rainbond_center_app_version）两个 Entity 落地，列名严格与 schema 真相对齐（保留 `is_ingerit` 历史拼写不修复）。

#### Scenario: GET /enterprise/{eid}/app-models 列表 + tag 过滤 + 分页

- **WHEN** 调 `GET /console/enterprise/E1/app-models?page=1&page_size=10&tag_id=2&scope=enterprise`
- **THEN** kuship-console 用 JPQL 三表 join `rainbond_center_app` + `rainbond_center_app_tag_relation`+`rainbond_center_app_tag` 查询
- **AND** 响应 `data.list` 数组，`data.bean.total` 总数
- **AND** 每条 item 含 app_id / app_name / pic / scope / dev_status / is_official / install_number 等字段

#### Scenario: POST /enterprise/{eid}/app-models 创建模板

- **WHEN** 调 POST body `{"app_name":"my-app","scope":"enterprise","describe":"..."}`
- **THEN** kuship-console 生成 32-char `app_id` UUID
- **AND** 写入 `rainbond_center_app` 一行（is_official=0、install_number=0、is_ingerit=0、enterprise_id=E1）
- **AND** 响应 `data.bean.app_id`

#### Scenario: GET /app-model/{app_id}/version/{version} 模板版本详情

- **WHEN** 调 GET 含 `app_template` 大字段
- **THEN** kuship-console 读 `rainbond_center_app_version` 单行，原样返回

### Requirement: 应用模板 Tag CRUD 端点

kuship-console SHALL 实现 `/enterprise/{eid}/app-models/tag` Tag 字典 CRUD（GET 列表 / POST 新建 / PUT/DELETE `tag/{tag_id}`）+ `/enterprise/{eid}/app-model/{app_id}/tag` 关联绑定/解绑（POST 绑定、DELETE 解绑），共 5 endpoint；落地表为 `rainbond_center_app_tag` + `rainbond_center_app_tag_relation`。

#### Scenario: POST /tag 创建 Tag

- **WHEN** 调 POST body `{"name":"web"}`
- **THEN** kuship-console 写入 `rainbond_center_app_tag` (name='web', enterprise_id=E1, is_deleted=0)
- **AND** 响应 tag_id 整数

#### Scenario: POST /app-model/{app_id}/tag 绑定 Tag

- **WHEN** 调 POST body `{"tag_id": 5}`
- **THEN** kuship-console 写入 `rainbond_center_app_tag_relation` (app_id=, tag_id=5, enterprise_id=E1)

### Requirement: 远程应用市场（AppMarket）凭据端点

kuship-console SHALL 实现 `/enterprise/{eid}/cloud/markets`（GET/POST 列表 + 创建）/ `cloud/bind-markets`（POST 批量绑定）/ `cloud/markets/{name}` (GET/PUT/DELETE) / `cloud/bindable-markets` (GET 可绑列表) / `cloud/markets/{name}/app-models{,/{model_id}/{versions,version/{v}}}`（GET 浏览远程模板及其版本）共 8 endpoint。`AppMarket` 凭据 Entity 落地 `app_market` 表。

#### Scenario: GET /cloud/markets 列出已绑定市场

- **WHEN** 调 GET
- **THEN** 返回 list 含 name / domain / type / access_key_masked（access_key 用 `***xx` 掩码）

#### Scenario: GET /cloud/markets/{name}/app-models 透传远程市场模板

- **WHEN** 调 GET
- **THEN** kuship-console 用本地 access_key + url 远程调用对应市场 API
- **AND** 响应原样透传 `data.list`

### Requirement: 从模板创建组件端点

kuship-console SHALL 实现 `POST /teams/{team_name}/apps/market_create` —— 接收 `{app_id, version, group_id, region_name}` 拉取模板 → 创建 group 内全部 service + ports + envs + relations；`POST /teams/{team_name}/apps/cmd_create` 同时支持 `helm` / `image` / `source_code` 三种 kind 命令行式安装。复用 `AppCreateService.create()`（appcreate 模块）逐个建组件。

#### Scenario: POST /apps/market_create 从模板创建多组件

- **WHEN** 调 body `{"app_id":"abc","version":"1.0","group_id":42}`
- **THEN** kuship-console 读 `rainbond_center_app_version.app_template` 解出 components 数组
- **AND** 对每个 component 调 `AppCreateService.create()` 创建 tenant_service + service_source
- **AND** 写 `service_group_relation` 关联到 group_id=42
- **AND** 响应 `data.bean.created` 数量

#### Scenario: POST /apps/cmd_create 命令行安装 helm

- **WHEN** 调 body `{"kind":"helm","command":"helm install foo bar/baz","group_id":42}`
- **THEN** kuship-console 调 `HelmOperations.commandInstall(region, team, command)`
- **AND** 写 `team_helm_release_source` 记录

### Requirement: 单组件版本快照与回滚端点

kuship-console SHALL 实现 `/teams/{team_name}/apps/{service_alias}/version` GET 列表 + `/version/{version_id}` GET 详情 + POST 回滚 + `/version/snapshot` 列表 + `/version/snapshot/{snap_id}` 详情 + `/version/rollback` POST + `/version/rollback/records` GET + `/rollback/records/{record_id}` GET 共 8 endpoint。版本数据由 region 持有，console 仅做查询透传与回滚触发。

#### Scenario: GET /apps/{alias}/version 版本列表

- **WHEN** 调 GET
- **THEN** kuship-console 调 region `/v2/tenants/{}/services/{}/versions` 透传

#### Scenario: POST /apps/{alias}/version/{version_id} 回滚到指定版本

- **WHEN** 调 POST body `{"deploy_version":"v1.0"}`
- **THEN** kuship-console 调 region `/rollback` 触发回滚 + 拿 event_id
- **AND** 本地不写新表，仅 update_time + update_version+1

### Requirement: 应用整组升级端点

kuship-console SHALL 实现 `/teams/{team_name}/groups/{group_id}/upgrade-records` GET 列表 + POST 创建 + `/upgrade-records/{record_id}` GET 详情 + `/upgrade-records/{record_id}/{upgrade,deploy,rollback,info,detail,components}` 共 9 endpoint。`AppUpgradeRecord` Entity 落地 `app_upgrade_record` 表，子表 `app_upgrade_snapshots` + `service_upgrade_record` 通过 record_id 关联。

#### Scenario: POST /upgrade-records 创建升级记录

- **WHEN** 调 POST body `{"version":"v2.0","upgrade_group_id":42,"market_name":"local"}`
- **THEN** kuship-console 写 `app_upgrade_record` 一行 status=0 record_type=upgrade
- **AND** 响应 record_id

#### Scenario: POST /upgrade-records/{record_id}/upgrade 推送至 region

- **WHEN** 调 POST
- **THEN** kuship-console 调 region 升级 API → 拿 event_id → update record.status=1 + event_id

#### Scenario: POST /upgrade-records/{record_id}/rollback 触发回滚

- **WHEN** 调 POST
- **THEN** kuship-console 在事务内 INSERT 新 record_type=rollback 行（parent_id=原 record_id）→ 调 region rollback

### Requirement: 服务分享异步流程端点

kuship-console SHALL 实现 `/teams/{team_name}/groups/{group_id}/share/record` POST 启动 + DELETE 取消 + GET version + `/share/{share_id}/{info,events{,/{event_id}{,/plugin}},giveup,complete}` 共 11 endpoint。`ServiceShareRecord` Entity 落地 `service_share_record` 表，事件流落 `service_share_record_event` 表；保留 rainbond 原 6 阶段 step 与 3 阶段 status 状态机不重新设计。

#### Scenario: POST /share/record 启动分享

- **WHEN** 调 POST body `{"share_app_market_name":"local","share_version":"1.0","share_app_model_name":"my-app"}`
- **THEN** kuship-console 写 `service_share_record` 一行（step=0、status=0、share_version、share_app_model_name）
- **AND** 响应 share_id

#### Scenario: POST /share/{share_id}/info 推送应用模板

- **WHEN** 调 POST body 含 app_template 等字段
- **THEN** kuship-console 调 region 服务分享 API → 拿到 event_id → 写 `service_share_record_event` 一行 + update record.step=2

#### Scenario: POST /share/{share_id}/complete 完成分享

- **WHEN** 调 POST
- **THEN** kuship-console update record status=1 step=5

### Requirement: 应用模板导出与导入端点

kuship-console SHALL 实现 `/enterprise/{eid}/app-models/export` POST 导出（生成 zip 流式返回）、`/app-models/import` POST 启动导入 + `/import/{event_id}` GET 状态轮询 + `/import/{event_id}/dir` GET 目录预览 共 4 endpoint。导出 SHALL 在 console 端 java.util.zip 内存压缩；导入由 region 处理（console 仅持久化 event_id + status）。

#### Scenario: POST /app-models/export 导出为 zip

- **WHEN** 调 POST body `{"app_ids":["a1","a2"],"format":"rainbond-app"}`
- **THEN** kuship-console 用 `ZipOutputStream` 流式生成 zip 含 metadata.json + 各 app_template
- **AND** 响应 Content-Disposition: attachment; filename=...

#### Scenario: POST /app-models/import 启动导入

- **WHEN** 调 POST 含 file
- **THEN** kuship-console 调 region 导入 API → 拿 event_id → 写 `groupapp_backup_import` 一行
- **AND** 响应 event_id

### Requirement: Helm Chart 应用安装端点

kuship-console SHALL 实现 `/teams/{team_name}/{helm_app, helm_command, helm_list, helm_cmd_add, helm_center_app}` 5 endpoint + 全局 `/helm/repos` GET/POST/DELETE 共 7 endpoint。`HelmRepo` Entity 落地 `helm_repo` 表；密码 SHALL 用 AES-GCM 加密后存（密钥来自 `kuship.helm.repo-password-key` 配置）；prod profile 缺密钥时启动失败。

#### Scenario: POST /helm/repos 添加 Repo

- **WHEN** 调 POST body `{"name":"bitnami","url":"https://charts.bitnami.com/bitnami","username":"","password":""}`
- **THEN** kuship-console 写 `helm_repo` 一行（password 加密）
- **AND** 调 region `HelmOperations.addRepo(...)` 通知 region

#### Scenario: POST /teams/{team}/helm_app 从 Chart 安装

- **WHEN** 调 POST body `{"chart_name":"redis","repo":"bitnami","version":"19.0.0","values":"..."}`
- **THEN** kuship-console 调 `HelmOperations.installChart(...)` → 拿 release_id
- **AND** 写 `team_helm_release_source` 一行 + `app_helm_overrides` 一行（values）

#### Scenario: POST /teams/{team}/helm_command 命令行安装

- **WHEN** 调 POST body `{"command":"helm install foo bar/baz --set k=v"}`
- **THEN** 调 `HelmOperations.commandInstall(...)` 直接透传命令字符串

### Requirement: 整组备份与导入端点

kuship-console SHALL 实现 `/teams/{team_name}/groupapp/{group_id}/{backup,backup/all_status,backup/export,backup/import}` + `/groupapp/backup`（团队级列表）+ `/all/groupapp/backup`（全企业列表）+ `/enterprise/{eid}/{backups,backups/{name},upload-backups}` 共 9 endpoint。`ServiceGroupBackup` Entity 落地 `groupapp_backup` 表；`BackupOperations` 接口承载 region 调用。

#### Scenario: POST /groupapp/{group_id}/backup 启动整组备份

- **WHEN** 调 POST body `{"note":"weekly","mode":"full"}`
- **THEN** kuship-console 写 `groupapp_backup` 一行 status=starting + backup_id 32-char UUID
- **AND** 调 `BackupOperations.backup(region, team, body)` 触发 region 异步备份
- **AND** 响应 backup_id

#### Scenario: GET /groupapp/{group_id}/backup/all_status 轮询状态

- **WHEN** 前端按 5s 轮询调 GET
- **THEN** kuship-console 读本地 `groupapp_backup.status` 直接返回（不每次重打 region）

#### Scenario: POST /groupapp/{group_id}/backup/import 导入备份

- **WHEN** 调 POST 含 file
- **THEN** kuship-console 写 `groupapp_backup_import` + 调 region 恢复

### Requirement: 整组应用复制与迁移端点

kuship-console SHALL 实现 `/teams/{team_name}/groupapp/{group_id}/copy` POST（同 region 复制）+ `/groupapp/{group_id}/migrate` POST（跨 region 迁移）+ `/groupapp/{group_id}/migrate/record` GET 共 3 endpoint。

#### Scenario: POST /groupapp/{group_id}/copy 同 region 复制

- **WHEN** 调 POST body `{"target_team_name":"team2","new_group_name":"my-app-copy"}`
- **THEN** kuship-console 列出 group 全部 service → 复用 `AppCreateService.create()` 逐个新建至 target_team
- **AND** 响应 `data.bean.new_group_id`

#### Scenario: POST /groupapp/{group_id}/migrate 跨 region 迁移

- **WHEN** 调 POST body `{"target_region":"r2","target_team":"team2"}`
- **THEN** 调 region migrate API → 写 `service_group_migration` 一行 + 返回 record_id

### Requirement: 团队 image_tags 列表端点

kuship-console SHALL 实现 `GET /teams/{team_name}/apps/image_tags?image=xxx` —— 调 hub registry HTTP API 拿 image 的 tag 列表。复用第 5 阶段 `team_registry_auths` 凭据；timeout 5s；失败返回空数组。

#### Scenario: GET /apps/image_tags 拉取公网 hub tags

- **WHEN** 调 GET `?image=nginx`
- **THEN** kuship-console 公网调 `https://registry-1.docker.io/v2/library/nginx/tags/list`
- **AND** 5 秒内返回 list；超时返回 `data.list=[]`

### Requirement: appmarket 模块 10 张表的 JPA Entity 与 Repository

kuship-console SHALL 在 `cn.kuship.console.modules.appmarket.{market,share,upgrade,backup,helm}.entity` 包下新增以下 Entity：
1. `RainbondCenterApp`（rainbond_center_app，19 列含 is_ingerit）
2. `RainbondCenterAppVersion`（rainbond_center_app_version，25 列）
3. `CenterAppTag`（rainbond_center_app_tag，4 列：id/name/enterprise_id/is_deleted）
4. `CenterAppTagRelation`（rainbond_center_app_tag_relation，4 列）
5. `AppMarket`（app_market）
6. `ServiceShareRecord`（service_share_record，19 列）
7. `ServiceShareRecordEvent`（service_share_record_event）
8. `AppUpgradeRecord`（app_upgrade_record，17 列）
9. `ServiceGroupBackup`（groupapp_backup）
10. `HelmRepo`（helm_repo）

主键全部 Integer 自增；列名与 schema 真相严格对齐，不擅自加 create_time / update_time（除非 DESC 已显示存在）。

#### Scenario: ddl-auto=validate 启动通过

- **WHEN** 应用启动连真实 MySQL（rainbond docker compose）
- **THEN** Hibernate ddl-auto=validate 不报缺列 / 多列 / 错类型错误

### Requirement: HelmOperations 6 method 完整实现

kuship-console SHALL 在 `appmarket/helm/api/HelmOperationsImpl.java` 中标注 `@Service @Primary` 替换 14 接口骨架的 unsupported 占位，实现 6 method：addRepo / removeRepo / listChart / queryChart / installChart / commandInstall。

#### Scenario: HelmOperations 完整 6 method 接通

- **WHEN** controller 注入 `HelmOperations` 调用任一 method
- **THEN** Impl 用 `RegionApiSupport.exchange(lambda)` 模板调 region `/v2/helm/*` 路径
- **AND** 不再抛 UnsupportedOperationException

### Requirement: BackupOperations 新接口 4 method 实现

kuship-console SHALL 在 `appmarket/backup/api/` 包下新建 `BackupOperations` 接口（4 method：backup / backupStatus / restore / export）+ Impl，作为本 change 的非骨架新增 region 接口。

#### Scenario: BackupOperations.backup 触发 region 备份

- **WHEN** controller 调用 `backupOperations.backup(region, team, body)`
- **THEN** Impl 调 region `/v2/tenants/{tenant}/groupapp/{group_id}/backup` 拿 backup_id
- **AND** 同步响应给 controller 写本地 groupapp_backup 元数据

### Requirement: appmarket 模块测试覆盖

kuship-console SHALL 提供至少 5 类集成测试覆盖 appmarket 核心：
1. `MarketTemplateIntegrationTest`：rainbond_center_app POST/GET CRUD + tag 绑定
2. `ShareRecordIntegrationTest`：service_share_record 启动 → info → events → complete 全状态机
3. `AppUpgradeIntegrationTest`：upgrade-records POST/GET + upgrade 推送 + rollback
4. `GroupBackupIntegrationTest`：groupapp/backup POST 写本地 + region mock 调用
5. `HelmRepoIntegrationTest`：helm/repos POST 双写本地 + region mock + 密码加密验证

#### Scenario: 集成测试全部使用真实 MySQL

- **WHEN** 在 docker-compose 启动后跑 `mvn -Dtest='cn.kuship.console.modules.appmarket.**' test`
- **THEN** 每类测试在 `@BeforeAll` 用高位 user_id（9091xx）插入 user/team 数据
- **AND** 在 `@AfterAll` 清理避免数据残留
- **AND** 全部用例通过

#### Scenario: HelmRepoIntegrationTest 验证密码加密

- **WHEN** POST `/helm/repos` 含 password=secretkey
- **THEN** 测试断言 `helm_repo.password` 列存的不是 'secretkey' 明文（说明 AES 加密生效）
- **AND** 调 GET 时返回的 password 字段为 `***`（掩码）

### Requirement: 团队插件 CRUD 端点

kuship-console SHALL 实现团队级插件 CRUD：`GET/POST /teams/{team_name}/plugins`、`GET /teams/{team_name}/plugins/all`、`POST /teams/{team_name}/plugins/default`、`GET/PUT/DELETE /teams/{team_name}/plugins/{plugin_id}`、`GET /teams/{team_name}/plugins/{plugin_id}/used_services` 共 8 endpoint。`TenantPlugin` Entity 落地 `tenant_plugin` 表（17 列含 `desc` 保留字反引号）。

#### Scenario: POST /teams/{team}/plugins 创建插件

- **WHEN** 调 POST body `{"plugin_name":"my-sidecar","plugin_alias":"侧车","category":"net-plugin:up","build_source":"image","image":"nginx:1.20"}`
- **THEN** kuship-console 生成 32-char `plugin_id`
- **AND** 写入 `tenant_plugin` 一行（origin=local, origin_share_id=空）
- **AND** 调 region 创建插件 → 失败回滚
- **AND** 响应 plugin_id

#### Scenario: GET /plugins/{plugin_id} 详情

- **WHEN** 调 GET
- **THEN** kuship-console 读 `tenant_plugin` + 关联读 `plugin_build_version` 当前版本，返回组合 bean

#### Scenario: GET /plugins/{plugin_id}/used_services 已挂载组件

- **WHEN** 调 GET
- **THEN** kuship-console 用 JPQL join `tenant_service_plugin_relation` + `tenant_service` 返回组件列表
- **AND** 每条含 service_id / service_alias / service_cname / plugin_status

### Requirement: 插件版本构建端点

kuship-console SHALL 实现 `/teams/{team}/plugins/{plugin_id}/build-history` GET + `/new-version` POST + `/version/{build_version}` GET/PUT + `/version/.../config` GET/PUT/DELETE + `/preview` GET + `/build` POST + `/status` GET + `/event-log` GET 共 10 endpoint。`PluginBuildVersion` Entity 落地 `plugin_build_version` 表（含 `build_status` + `plugin_version_status` 双状态）。

#### Scenario: POST /plugins/{plugin_id}/new-version 创建新版本

- **WHEN** 调 POST body `{"build_version":"1.1.0","update_info":"add tracing"}`
- **THEN** kuship-console INSERT `plugin_build_version` (build_status='unbuilding', plugin_version_status='unbuild')
- **AND** 响应 build_version

#### Scenario: POST /version/{ver}/build 触发构建

- **WHEN** 调 POST
- **THEN** kuship-console 调 region build → 拿 event_id
- **AND** UPDATE `plugin_build_version.event_id + plugin_version_status='building'`
- **AND** 响应 event_id

#### Scenario: GET /version/{ver}/status 查询状态

- **WHEN** 调 GET
- **THEN** kuship-console 调 region 拿状态 → UPDATE 本地 status 字段 → 返回 `{build_status, plugin_version_status}`

### Requirement: 插件配置组与配置项端点

kuship-console SHALL 实现 `/teams/{team}/plugins/{plugin_id}/version/{build_version}/config` GET/PUT/DELETE + `/preview` GET 共 4 endpoint。`PluginConfigGroup` + `PluginConfigItems` 双表落地（含 longtext `attr_alt_value` / `attr_default_value`）。

#### Scenario: PUT /version/{ver}/config 配置组+配置项写入

- **WHEN** 调 PUT body `{"config_groups":[{"config_name":"upstream","service_meta_type":"upstream_port","items":[{"attr_name":"weight","attr_type":"int","attr_default_value":"1"}]}]}`
- **THEN** kuship-console 在事务内 INSERT 配置组一行 + INSERT 配置项 N 行
- **AND** 旧配置先 DELETE（按 plugin_id+build_version 级联）

#### Scenario: GET /version/{ver}/preview 配置预览

- **WHEN** 调 GET
- **THEN** kuship-console 读两表组装预览结构（attrs 数组 + 描述）

### Requirement: 组件挂载插件端点

kuship-console SHALL 实现 `/teams/{team}/apps/{service_alias}/{pluginlist,plugins/{plugin_id}/{install,open,configs},analyze_plugins}` 共 5 endpoint。挂载流程操作三表：`tenant_service_plugin_relation` + `tenant_service_plugin_attr` + `service_plugin_config_var`。

#### Scenario: GET /apps/{alias}/pluginlist 已挂载列表

- **WHEN** 调 GET
- **THEN** kuship-console 读 `tenant_service_plugin_relation` 按 service_id 返回插件列表
- **AND** 每条含 plugin_name / plugin_status / build_version

#### Scenario: POST /apps/{alias}/plugins/{id}/install 挂载插件

- **WHEN** 调 POST body `{"build_version":"1.0.0","attrs":[{"attr_name":"weight","attr_value":"5"}]}`
- **THEN** kuship-console 在事务内 INSERT `relation`（plugin_status=true）+ INSERT `config_var` N 行（按 attrs 数组）
- **AND** 调 region installPlugin → 失败回滚

#### Scenario: PUT /apps/{alias}/plugins/{id}/open 启停插件

- **WHEN** 调 PUT body `{"plugin_status":false}`
- **THEN** kuship-console UPDATE `relation.plugin_status=false`
- **AND** 调 region 重启服务

#### Scenario: PUT /apps/{alias}/plugins/{id}/configs 更新配置

- **WHEN** 调 PUT body `{"attrs":[...]}`
- **THEN** kuship-console DELETE 旧 `config_var` + batch INSERT 新行
- **AND** 调 region 更新

#### Scenario: DELETE /apps/{alias}/plugins/{id}/install 卸载

- **WHEN** 调 DELETE
- **THEN** kuship-console 调 region detach → DELETE 三张表

### Requirement: 插件分享异步流程端点

kuship-console SHALL 实现 `/teams/{team}/plugins/{plugin_id}/share/record` POST + `/plugin-share/{share_id}{,/events,/events/{event_id},/complete}` 共 5 endpoint。`TenantPluginShare` Entity 落地 `tenant_plugin_share` 表（含 varchar(4096) `config`）；事件流落 `plugin_share_record_event`；状态机 6-step / 3-status 与第 9 阶段 service_share 完全同构。

#### Scenario: POST /plugins/{plugin_id}/share/record 启动分享

- **WHEN** 调 POST body `{"plugin_name":"my-sidecar","share_version":"1.0","desc":"..."}`
- **THEN** kuship-console INSERT `tenant_plugin_share`（origin_plugin_id=plugin_id，share_id 32-char UUID）
- **AND** 响应 share_id

#### Scenario: POST /plugin-share/{share_id}/events/{event_id} 推送事件

- **WHEN** 调 POST
- **THEN** kuship-console INSERT `plugin_share_record_event` 一行

#### Scenario: POST /plugin-share/{share_id}/complete 完成分享

- **WHEN** 调 POST
- **THEN** kuship-console UPDATE `tenant_plugin_share` 标记完成（用 share_version 字段映射 status）

### Requirement: 应用市场插件同步与安装端点

kuship-console SHALL 实现 `/market/plugins{,/sync,/sync-template,/uninstall-template,/install}` + `/plugins{,/installable}` + `/teams/{team}/apps/plugins` 共 8 endpoint。`RainbondCenterPlugin` Entity 落地 `rainbond_center_plugin` 表。

#### Scenario: GET /market/plugins 列出市场插件

- **WHEN** 调 GET
- **THEN** kuship-console 读 `rainbond_center_plugin` 全部记录返回

#### Scenario: POST /market/plugins/sync 同步远程市场

- **WHEN** 调 POST body `{"market_name":"local"}`
- **THEN** kuship-console（MVP）插入或更新一些占位插件；远程 HTTP 真实拉取留作 hardening

#### Scenario: POST /market/plugins/install 从市场安装到当前团队

- **WHEN** 调 POST body `{"plugin_key":"...","tenantName":"team1"}`
- **THEN** kuship-console 读市场 entity → INSERT `tenant_plugin`（origin='market'）+ 配置组复制

#### Scenario: GET /plugins/installable 列出可安装插件

- **WHEN** 调 GET
- **THEN** kuship-console 返回 `rainbond_center_plugin` 中尚未在当前团队安装过的插件

### Requirement: 平台插件 region 代理端点

kuship-console SHALL 实现 `/enterprise/{eid}/regions/{r}/{plugins,platform-plugins,platform-plugins/{id}/install,officialplugins}` + `/regions/{r}/{plugins/{name}/status,static/plugins/{name},proxy/plugins/{name}/{file_path:**},backend/plugins/{name}/{file_path:**}}` 共 8 endpoint。代理端点透传任意 path + HTTP method 给 region；超时提升到 30s（静态资源 + 冷启动场景）。

#### Scenario: GET /enterprise/{eid}/regions/{r}/plugins 列出 region 插件

- **WHEN** 调 GET
- **THEN** kuship-console 调 `RainbondPluginOperations.listPlugins(region)` 透传

#### Scenario: POST /enterprise/{eid}/regions/{r}/platform-plugins/{id}/install 安装平台插件

- **WHEN** 调 POST body
- **THEN** kuship-console 调 `RainbondPluginOperations.installPlatformPlugin(region, id, body)` 透传

#### Scenario: GET /regions/{r}/static/plugins/{name} 静态资源透传

- **WHEN** 调 GET
- **THEN** kuship-console 调 region 静态路径 → 返回 `byte[]` + 保留 Content-Type
- **AND** 单文件超过 10MB 时返回 413

#### Scenario: GET /regions/{r}/proxy/plugins/{name}/{file_path} 通用反向代理

- **WHEN** 调 GET / POST 任意 method
- **THEN** kuship-console 透传 method + path + body 至 region

### Requirement: plugin 模块 9 张表的 JPA Entity 与 Repository

kuship-console SHALL 在 `cn.kuship.console.modules.plugin.{team,service,market}.entity` 包下新增 9 个 Entity（按 schema 真相）：
1. `TenantPlugin`（tenant_plugin，17 列含 `desc` 反引号）
2. `PluginBuildVersion`（plugin_build_version，16 列双状态）
3. `PluginConfigGroup`（plugin_config_group，6 列）
4. `PluginConfigItems`（plugin_config_items，12 列含 longtext）
5. `TenantServicePluginRelation`（tenant_service_plugin_relation，8 列）
6. `TenantServicePluginAttr`（tenant_service_plugin_attr，17 列含跨服务字段）
7. `ServicePluginConfigVar`（service_plugin_config_var，11 列含 longtext attrs）
8. `TenantPluginShare`（tenant_plugin_share，17 列）
9. `PluginShareRecordEvent`（plugin_share_record_event，10 列）
10. `RainbondCenterPlugin`（rainbond_center_plugin，市场插件）

主键全部 Integer 自增；列名严格对齐 schema（特别 `desc` 保留字反引号）。

#### Scenario: ddl-auto=validate 启动通过

- **WHEN** 应用启动连真实 MySQL（rainbond docker compose）
- **THEN** Hibernate ddl-auto=validate 不报缺列 / 多列 / 错类型错误

### Requirement: PluginOperations 与 RainbondPluginOperations 两 region API 接口

kuship-console SHALL 在 `cn.kuship.console.modules.plugin.api/` 新建：
- `PluginOperations` 接口 + `PluginOperationsImpl`（@Primary @Service）：10 method 覆盖 plugin 全部 region 调用
- `RainbondPluginOperations` 接口 + `RainbondPluginOperationsImpl`（@Primary @Service）：8 method 覆盖平台插件代理 + 静态资源/后端透传

两接口都不在原 14 接口骨架中，作为本 change 新增的非骨架 region 接口。

#### Scenario: PluginOperations 10 method 全部接通

- **WHEN** controller 注入并调用 `PluginOperations.<method>(...)`
- **THEN** Impl 用 `RegionApiSupport.exchange(lambda)` 模板调对应 region path
- **AND** 不再抛 UnsupportedOperationException

#### Scenario: RainbondPluginOperations.proxyStaticResource 流式透传

- **WHEN** controller 调用 `proxyStaticResource(region, name)`
- **THEN** Impl 调 region 静态路径 → 返回 `byte[]` + Content-Type
- **AND** 不走 RegionApiResponseProcessor.extractBean 包装（直接二进制透传）

### Requirement: plugin 模块测试覆盖

kuship-console SHALL 提供至少 4 类集成测试覆盖 plugin 核心：
1. `PluginCrudIntegrationTest`：POST/GET/PUT/DELETE 团队插件 + tenant_plugin 表写入断言
2. `PluginVersionBuildIntegrationTest`：创建版本 + 配置组/配置项写入 + 状态查询
3. `ServicePluginInstallIntegrationTest`：组件挂载插件三表写入 + 启停 + 卸载
4. `PluginShareIntegrationTest`：分享异步流程（record → events → complete）+ tenant_plugin_share 状态变更

#### Scenario: 集成测试全部使用真实 MySQL

- **WHEN** 在 docker-compose 启动后跑 `mvn -Dtest='cn.kuship.console.modules.plugin.**' test`
- **THEN** 每类测试在 `@BeforeAll` 用高位 user_id（9092xx）插入 user/team 数据
- **AND** 在 `@AfterAll` 清理避免数据残留
- **AND** 全部用例通过

#### Scenario: 全套 ≥ 96 用例

- **WHEN** 跑 `mvn test`
- **THEN** 总用例数 ≥ 96（94 老 + 4 新插件）全部通过

### Requirement: 用户消息中心端点

kuship-console SHALL 实现 `GET /teams/{team_name}/message` 列出当前用户未读消息 + `PUT /teams/{team_name}/message` 批量标记已读/未读/删除共 2 endpoint。`UserMessage` Entity 落地 `user_message` 表（11 列含 `announcement_id` + `level`）。

#### Scenario: GET /message 返回当前用户未读消息

- **WHEN** 调 `/console/teams/team1/message?msg_type=warning&is_read=false`
- **THEN** kuship-console 按 receiver_id = current user_id 过滤
- **AND** 响应 list 含 message_id / title / content / level / msg_type / create_time

#### Scenario: PUT /message 批量标记已读

- **WHEN** 调 PUT body `{"message_ids":["m1","m2"],"action":"read"}`
- **THEN** kuship-console UPDATE `user_message.is_read = 1` WHERE message_id IN (m1,m2) AND receiver_id = current user_id

### Requirement: Webhook 触发部署端点

kuship-console SHALL 实现 8 个 Webhook 端点：
- `POST /webhooks/{service_id}` git webhook 触发 redeploy
- `POST /image/webhooks/{service_id}` 镜像仓库 webhook
- `POST /custom/deploy/{service_id}` 通用 webhook
- `GET /teams/{team}/apps/{alias}/webhooks/get-url` 拿 webhook URL + secret
- `POST /teams/{team}/apps/{alias}/webhooks/trigger` 手动触发
- `GET /teams/{team}/apps/{alias}/webhooks/status` 查询配置
- `PUT /teams/{team}/apps/{alias}/webhooks/updatekey` 重新生成 secret

`ServiceWebhooks` Entity 落地 `service_webhooks` 表（5 列）；secret key 沿用 `tenant_service.secret` 字段（已有第 7 阶段 entity）。MVP **不做 HMAC 验签**，按 `secret` query param 简单匹配；正式 HMAC 留作 hardening。

#### Scenario: POST /webhooks/{service_id}?secret=xxx 触发部署

- **WHEN** GitHub push 调 webhook
- **THEN** kuship-console 校验 query string `secret` 与 `tenant_service.secret` 是否匹配
- **AND** 不匹配返回 401
- **AND** 匹配后调 region build 触发 redeploy
- **AND** 响应 `{event_id: ...}`

#### Scenario: GET /webhooks/get-url 返回 URL + secret

- **WHEN** 调 GET
- **THEN** 响应 `{webhook_url: "https://kuship.example.com/console/webhooks/{service_id}?secret={secret}", secret: ...}`

### Requirement: MCP Query JSON-RPC 端点

kuship-console SHALL 实现 `POST /mcp/query/http` MCP（Model Context Protocol）JSON-RPC 同步入口共 1 endpoint，接受标准 JSON-RPC 2.0 请求格式，将 LLM 来源的查询请求路由至已有的 application / plugin / market 等 query method。SSE / message 流式入口推迟到独立 hardening change。

#### Scenario: POST /mcp/query/http 返回 LLM 友好的应用列表

- **WHEN** 调 POST body `{"jsonrpc":"2.0","id":1,"method":"list_apps","params":{"team_name":"team1"}}`
- **THEN** kuship-console 路由到 GroupController.list 等已有方法
- **AND** 响应 JSON-RPC 2.0 格式 `{"jsonrpc":"2.0","id":1,"result":{...}}` 或 error 形式

### Requirement: 文件上传端点

kuship-console SHALL 实现 `POST /files/upload` 通用文件上传 + `GET /files/{file_id}` 读取共 2 endpoint。文件存储到 `${kuship.upload.dir:/tmp/kuship}/`，按 UUID 重命名。单文件最大 5MB。响应含 file_url + file_name + file_size。

#### Scenario: POST /files/upload 上传图片

- **WHEN** 调 POST multipart form 含 `file=@avatar.png`
- **THEN** kuship-console 写文件至 `${kuship.upload.dir}/{uuid}.png`
- **AND** 响应 `{file_url: "/console/files/{uuid}.png", file_name: "avatar.png", file_size: ...}`

#### Scenario: 文件超过 5MB 返回 400

- **WHEN** 调 POST 上传 6MB 文件
- **THEN** 响应 400 + `msg_show: "文件超过 5MB"`

### Requirement: 登录事件查询端点

kuship-console SHALL 实现 `GET /enterprise/{eid}/login-events` 列出当前企业最近登录事件共 1 endpoint，分页查询 `login_events` 表（10 列含 client_ip/user_agent/duration）。

#### Scenario: GET /login-events 按时间倒序

- **WHEN** 调 GET `?page=1&page_size=20`
- **THEN** kuship-console 按 enterprise_id 过滤 + 按 login_time 倒序
- **AND** 响应 list 含 username / login_time / client_ip / ip_locale_main / user_agent / duration

### Requirement: 操作审计日志查询端点

kuship-console SHALL 实现 3 个审计查询端点：
- `GET /enterprise/{eid}/operation-logs` —— 企业级（限制 EnterpriseAdmin）
- `GET /teams/{team_name}/operation-logs` —— 团队级
- `GET /teams/{team_name}/apps/{app_id}/operation-logs` —— 应用级

`OperationLog` Entity 落地 `operation_log` 表（14 列含 longtext old_/new_information）。列表查询用 JPQL 限定字段（不返回 longtext），单条详情 endpoint 才查完整行。

#### Scenario: GET /enterprise/{eid}/operation-logs 列表

- **WHEN** 调 GET `?operation_type=upgrade&page=1`
- **THEN** kuship-console 按 enterprise_id 过滤
- **AND** 响应 list 含 username / operation_type / team_name / app_name / service_cname / comment / create_time
- **AND** 不返回 old_information / new_information 字段（避免大字段 SELECT）

### Requirement: Console 升级查询端点

kuship-console SHALL 实现 4 个 console 自身升级查询端点（MVP 占位返回固定版本字符串；真升级靠运维换 jar）：
- `GET /enterprise/upgrade` 当前版本
- `GET /enterprise/upgrade/version` 可升级版本列表
- `GET /enterprise/upgrade/version/{version}` 版本详情
- `GET /enterprise/upgrade/version/{version}/images` 镜像列表

#### Scenario: GET /enterprise/upgrade 返回当前版本

- **WHEN** 调 GET
- **THEN** 响应 `{current_version: "0.1.0-SNAPSHOT", build_time: "..."}`

### Requirement: 企业全局配置端点

kuship-console SHALL 实现企业全局配置 6 endpoint：
- `GET /enterprises/{eid}/configs` 列表
- `PUT /enterprises/{eid}/configs/{key}` 更新单项
- `DELETE /enterprises/{eid}/configs/{key}` 删除
- `GET/PUT /enterprise/object_storage` 对象存储配置
- `GET/PUT /enterprise/appstore_image_hub` 应用市场镜像源
- `GET /enterprise/{eid}/visualmonitor` 可视化监控开关
- `GET /enterprise/{eid}/alerts` 告警配置

复用第 4 阶段已落地的 `ConsoleConfig` entity。key 命名规则：`{enterprise_id}.{config_name}`。

#### Scenario: PUT /enterprise/{eid}/configs/{key} 更新配置

- **WHEN** 调 PUT body `{"value":"...","description":"..."}`
- **THEN** kuship-console UPSERT `console_config`（key=enterprise_id.{key}）

### Requirement: SMS 短信端点

kuship-console SHALL 实现 4 个 SMS 端点：
- `GET/PUT /enterprises/{eid}/sms-config` SMS 配置
- `POST /sms/send-code` 发送验证码
- `POST /users/register-by-phone` 手机号注册
- `POST /users/login-by-phone` 手机号登录

`SmsVerificationCode` Entity 落地 `sms_verification_code` 表（6 列）。MVP **不调外部 SMS SDK**：写表 + dev profile 控制台打印 code（prod profile 拒绝该 controller，要求接 SDK）。

#### Scenario: POST /sms/send-code 写表

- **WHEN** 调 POST body `{"phone":"13800000000","purpose":"login"}`
- **THEN** kuship-console 生成 6 位随机数字 code
- **AND** INSERT `sms_verification_code`（expires_at = now + 5min）
- **AND** dev profile 控制台打印 code
- **AND** 响应 `{sent: true, expires_at: ...}`

#### Scenario: POST /users/login-by-phone 验证 + 签发 JWT

- **WHEN** 调 POST body `{"phone":"...","code":"123456"}`
- **THEN** kuship-console 查询最近未过期的 sms_verification_code 匹配
- **AND** 匹配后签发 JWT 同 user_auth 流程
- **AND** 不匹配返回 401

### Requirement: KubeBlocks 数据库端点

kuship-console SHALL 实现 8 个 KubeBlocks 端点全部 region 透传，无本地 entity：
- `GET /teams/{team}/regions/{r}/kubeblocks/{supported_databases,storage_classes,backup_repos}`
- `GET /teams/{team}/apps/{alias}/kubeblocks/{detail,backup-config,backups,parameters,restore}`

#### Scenario: GET /kubeblocks/supported_databases 透传

- **WHEN** 调 GET
- **THEN** kuship-console 调 region `/v2/kubeblocks/supported-databases` 透传响应

### Requirement: API Gateway 端点

kuship-console SHALL 实现 4 个 API Gateway 透传端点：
- `GET/POST /teams/{team}/api-gateway/routes`
- `GET/POST /teams/{team}/api-gateway/certificates`

全部 region 透传无本地 entity。

#### Scenario: GET /api-gateway/routes 透传

- **WHEN** 调 GET
- **THEN** kuship-console 调 region `/v2/api-gateway/routes` 透传

### Requirement: 占位端点（k8s_attribute / errlog / task_guidance / platform_settings / team_overview / team_resources）

kuship-console SHALL 实现剩余 ~10 个占位端点，返回空数据或固定字符串：
- `/teams/{team}/apps/{alias}/k8s_attributes` GET/POST → region 透传
- `/teams/{team}/apps/{alias}/k8s_resources` GET → region 透传
- `/console/errlog` POST → 写本地 logback，不写 DB
- `/console/task-guidance` GET → 返回空数组
- `/console/platform-settings` GET → 返回固定 platform 配置（type=community + version 字符串）
- `/console/teams/{team}/overview` GET → 聚合 Tenants/TenantService 计数
- `/console/teams/{team}/resources` GET → 聚合资源使用 + 配额

#### Scenario: GET /platform-settings 返回固定配置

- **WHEN** 调 GET
- **THEN** 响应 `{type:"community", version:"0.1.0-SNAPSHOT", commit_id:"..."}`

#### Scenario: POST /errlog 写日志

- **WHEN** 调 POST body `{"msg":"...","stack":"..."}`
- **THEN** kuship-console 用 SLF4J `error()` 输出到日志，响应 200

### Requirement: misc 模块 5 张表的 JPA Entity 与 Repository

kuship-console SHALL 在 `cn.kuship.console.modules.misc.{message,webhook,audit,sms}.entity` 包下新增 5 个 Entity：
1. `UserMessage`（user_message，11 列含 announcement_id/level）
2. `ServiceWebhooks`（service_webhooks，5 列）
3. `LoginEvents`（login_events，10 列）
4. `OperationLog`（operation_log，14 列含 longtext old_/new_information）
5. `SmsVerificationCode`（sms_verification_code，6 列含 created_at/expires_at）

主键全部 Integer 自增；列名严格对齐 schema（特别 SMS 表的 `id` 小写而非 `ID`）；不引入复杂 helper（参考 audit 模块的 longtext 字段处理）。

#### Scenario: ddl-auto=validate 启动通过

- **WHEN** 应用启动连真实 MySQL
- **THEN** Hibernate ddl-auto=validate 不报缺列 / 多列 / 错类型错误

### Requirement: misc 模块测试覆盖

kuship-console SHALL 提供至少 2 类集成测试覆盖 misc 核心：
1. `MessageIntegrationTest`：POST 模拟系统消息插入 + GET 列表 + PUT 标记已读
2. `OperationLogIntegrationTest`：模拟 INSERT operation_log + GET 列表分页 + 验证 longtext 字段不在列表返回

#### Scenario: 集成测试全部使用真实 MySQL

- **WHEN** 在 docker-compose 启动后跑 `mvn -Dtest='cn.kuship.console.modules.misc.**' test`
- **THEN** 每类测试在 `@BeforeAll` 用高位 user_id（9093xx）插入 user/team 数据
- **AND** 在 `@AfterAll` 清理避免数据残留
- **AND** 全部用例通过

#### Scenario: 全套 ≥ 97 用例

- **WHEN** 跑 `mvn test`
- **THEN** 总用例数 ≥ 97（95 老 + 2 新 misc）全部通过


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

### Requirement: Maven native profile

kuship-console SHALL 在 `pom.xml` 中提供独立的 `native` Maven profile，包含 `org.graalvm.buildtools:native-maven-plugin:0.10.4` 配置；profile 不影响默认 `mvn package` 行为（继续产 fat jar），仅 `mvn -Pnative package` 触发 GraalVM Native Image 构建。

#### Scenario: 默认 mvn package 仍产 fat jar

- **WHEN** 跑 `mvn clean package`
- **THEN** `target/kuship-console-0.1.0-SNAPSHOT.jar` 生成，文件大小约 150MB
- **AND** 不触发 native:compile

#### Scenario: mvn -Pnative package 产 native binary

- **WHEN** 跑 `mvn -Pnative -DskipTests package` 在装有 GraalVM 21 community 的环境
- **THEN** `target/kuship-console`（Linux/macOS 可执行文件）生成
- **AND** 文件大小约 60-80MB

#### Scenario: native plugin buildArgs 显式启用必要选项

- **WHEN** 检查 native plugin 配置
- **THEN** `--enable-url-protocols=http,https` / `-H:+AddAllCharsets` / `-H:IncludeResources=db/migration/.*\\.sql` / `-H:IncludeResources=application.*\\.yaml` 全部出现

### Requirement: RuntimeHints 注册

kuship-console SHALL 提供 `KuShipConsoleRuntimeHints implements RuntimeHintsRegistrar`，通过 `@ImportRuntimeHints` 在 `NativeConfig` 中关联；hint 注册全部 `cn.kuship.console.modules.**.entity` 包下的 `@Entity` 类（用反射 scanner 自动遍历）+ `cn.kuship.console.common.**` 下的 record 类（如 JwtClaims / OperationLogSummary）。

#### Scenario: NativeConfig 加载

- **WHEN** 应用启动加载 `NativeConfig.class`
- **THEN** Spring 通过 `@ImportRuntimeHints` 关联 `KuShipConsoleRuntimeHints`
- **AND** native build 时 AOT processor 把 hint 写入 `target/native-image/META-INF/native-image/...`

#### Scenario: 全部 Entity 反射访问可用

- **WHEN** native binary 运行启动
- **THEN** Hibernate 通过反射访问 `UserInfo` / `Tenants` / `TenantService` 等 ~58 entity 不报 `ClassNotFoundException`

### Requirement: Spring AOT enable

kuship-console SHALL 在 `application.yaml` 中默认 `spring.aot.enabled=false`，但允许通过环境变量 `SPRING_AOT=true` 覆盖；native build 时 plugin 自动设此值为 true。普通 fat jar 启动时此值为 false，不受 AOT 影响。

#### Scenario: native build 时 spring.aot.enabled=true

- **WHEN** 跑 `mvn -Pnative package`
- **THEN** AOT processor 跑完后 `target/spring-aot/main/sources/...` 生成
- **AND** native image 包含 AOT 优化代码

#### Scenario: fat jar 启动时 spring.aot.enabled=false

- **WHEN** `java -jar fat.jar` 启动（默认）
- **THEN** Spring 不执行 AOT 加载路径，行为与第 1-12 阶段完全一致

### Requirement: Hibernate 字节码增强关闭

kuship-console SHALL 在 `application.yaml` 中设 `hibernate.jakarta.persistence.bytecode.strategy=none` 与 `hibernate.bytecode.use_reflection_optimizer=false`，关闭 Hibernate 6 默认的 ByteBuddy 字节码增强（与 GraalVM Native 不兼容）。

#### Scenario: 字节码增强关闭后 ddl-auto=validate 仍生效

- **WHEN** native binary 启动连真实 MySQL
- **THEN** Hibernate 跑 ddl-validate 校验所有 entity 列对齐 schema
- **AND** 不报 ByteBuddy 相关错误

#### Scenario: 5% 运行时性能损失可接受

- **WHEN** native binary 跑 100 次组件查询
- **THEN** 性能比 fat jar 慢 ~5%（lazy load 退化为 eager load）
- **AND** 该退化不阻塞正常使用（< 1k DAU 场景下 CPU 非瓶颈）

### Requirement: native smoke test

kuship-console SHALL 提供 1 个 native 兼容的 smoke 测试 `NativeSmokeTest`，验证 native image 启动 + 基础 endpoint 可调；通过 `mvn -Pnative -Dtest=NativeSmokeTest test` 触发。其余 100 个 fat-jar 测试用例不要求在 native image 下运行（部分用 Mockito mock 需独立 hint，留作 hardening）。

#### Scenario: NativeSmokeTest 通过

- **WHEN** 跑 `mvn -Pnative -Dtest=NativeSmokeTest test`
- **THEN** 测试在 native test infra 中启动应用 + 调 `/console/healthz` 返回 200
- **AND** 整个测试在 < 30 秒内完成

#### Scenario: fat jar 测试不受影响

- **WHEN** 跑 `mvn test`
- **THEN** 现有 101 测试用例继续全部通过（包括 16 集成测试 + 单测）

### Requirement: Native Dockerfile + 多阶段构建

kuship-console SHALL 在 `kuship-console/Dockerfile.native` 提供两阶段构建文件：第 1 阶段用 `ghcr.io/graalvm/native-image-community:21` 镜像 + Maven build native binary；第 2 阶段用 `gcr.io/distroless/base-debian12` 装载 binary。最终镜像约 80MB。

#### Scenario: docker build 成功

- **WHEN** 跑 `docker build -f Dockerfile.native -t kuship-console-native .`
- **THEN** 构建成功生成镜像
- **AND** `docker images kuship-console-native --format "{{.Size}}"` 显示约 80MB

#### Scenario: docker run 启动 < 2s

- **WHEN** 跑 `docker run -p 8080:8080 kuship-console-native`
- **THEN** 容器启动到 `/console/healthz` 返回 200 全过程 < 2 秒

### Requirement: 启动性能 SLO

kuship-console native binary 启动时间 SHALL 在 macOS M2 / Linux x86_64 标准硬件上 < 2 秒；运行时 RSS 内存 < 300MB。

#### Scenario: 本地启动时间测量

- **WHEN** 在 macOS M2 上跑 `time ./target/kuship-console`
- **THEN** "Started KuShipConsoleApplication in" 日志在启动后 < 2.0s 出现

#### Scenario: 运行时内存测量

- **WHEN** native binary 启动稳定后跑 `ps -o rss= -p $(pgrep kuship-console) | awk '{print $1/1024 \" MB\"}'`
- **THEN** RSS 输出 < 300 MB

### Requirement: standalone 镜像可选切换 native

kuship-console SHALL 在根 `standalone/Dockerfile` 中提供 `NATIVE` build arg（默认 `false`），允许用户通过 `docker build --build-arg NATIVE=true` 切换到多阶段 native 构建路径；默认行为保持 fat-jar 不变（向后兼容）。

#### Scenario: 默认 build arg 仍走 fat-jar

- **WHEN** 跑 `docker build -f standalone/Dockerfile -t kuship-standalone .`
- **THEN** 镜像内仍打包 fat jar（与 12 阶段后行为一致）

#### Scenario: 显式 NATIVE=true 切换

- **WHEN** 跑 `docker build --build-arg NATIVE=true -f standalone/Dockerfile .`
- **THEN** 第 1 阶段编译 native binary，第 2 阶段装载到 standalone 镜像
- **AND** 镜像总大小（含 k3s 离线 + rainbond docker）从 ~3.5GB 降到 ~3GB

### Requirement: CI 双架构 native build

kuship-console SHALL 在 `.github/workflows/native-build.yml` 中配置 GitHub Actions matrix 构建：`ubuntu-22.04`（amd64） + `ubuntu-22.04-arm64`（arm64）双架构；构建产物作为 artifact 上传 release。CI 仅在 release tag 触发，避免每个 PR 增加 ~10 分钟构建时间。

#### Scenario: PR 仍用 fat-jar 测试

- **WHEN** 提交 PR
- **THEN** 触发现有 fat-jar workflow（`mvn test`），不触发 native build

#### Scenario: release tag 触发双架构 build

- **WHEN** 推送 `v*.*.*` tag
- **THEN** native-build.yml 启动 amd64 + arm64 矩阵构建
- **AND** artifact `kuship-console-amd64` 与 `kuship-console-arm64` 上传

### Requirement: 文档与启动方式矩阵

kuship-console SHALL 在 `kuship-console/CLAUDE.md` 与 `kuship-console/README.md` 新增"GraalVM Native（enable-graalvm-native）"段落，列出 5 种启动方式（fat jar dev / fat jar prod / native dev / native prod / docker native）的对比矩阵：启动时间 / 内存 / 用途。

#### Scenario: CLAUDE.md 含 native 段落

- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档含"GraalVM Native"标题段
- **AND** 段落含 5 种启动方式对比表（启动时间 / 内存 / 用途三列）

#### Scenario: README 含本地 native 构建说明

- **WHEN** 阅读 `kuship-console/README.md`
- **THEN** 文档含 `mvn -Pnative -DskipTests package` 命令 + 必要前置条件（GraalVM 21 community 安装步骤）

### Requirement: Native test profile

构建系统 SHALL 提供 `native-test` Maven profile，继承 `native` profile 的 AOT 与 native-image 配置，但额外开启测试编译与 surefire 执行；该 profile 与默认 JVM 测试 profile 互斥。

#### Scenario: native-test profile 触发 surefire
- **WHEN** 开发者执行 `mvn -Pnative,native-test test`
- **THEN** Maven SHALL 调用 native-image 编译 test classes
- **AND** surefire SHALL 在 native binary 模式下运行 JUnit 5 测试

#### Scenario: 默认 native profile 跳过测试
- **WHEN** 开发者执行 `mvn -Pnative package`
- **THEN** Maven SHALL 跳过测试以加速 binary 构建
- **AND** 仅产出 `target/kuship-console` native 二进制

#### Scenario: JVM 测试不受影响
- **WHEN** 开发者执行 `mvn test`（不带 `-Pnative-test`）
- **THEN** Maven SHALL 使用 JVM 模式跑 surefire
- **AND** 102/102 既有 JVM 测试 SHALL 全部通过

### Requirement: NativeTestRuntimeHintsRegistrar

测试 classpath SHALL 提供 `NativeTestRuntimeHintsRegistrar`（位于 `src/test/java/cn/kuship/console/native_/`），自动扫描 `cn.kuship.console.modules.**.dto` / `cn.kuship.console.modules.**.controller` / `cn.kuship.console.common.response` 包下所有类，注册反射 hint（`MemberCategory.values()`）；该 registrar SHALL 通过 Spring Boot 4 的 `RuntimeHintsRegistrar` SPI 仅在测试运行时加载，不进入生产 native binary。

#### Scenario: registrar 自动扫描 DTO 与 controller
- **WHEN** native test 启动 ApplicationContext
- **THEN** registrar SHALL 通过 ClassPathScanningCandidateComponentProvider 扫描所述包
- **AND** 为每个发现的类调用 `hints.reflection().registerType(<class>, MemberCategory.values())`

#### Scenario: registrar 不污染生产 binary
- **WHEN** 执行 `mvn -Pnative package`（无 native-test）
- **THEN** test 工具类 SHALL 不被打包进 native binary
- **AND** binary 体积保持 ≤ 100MB（与现有 enable-graalvm-native 一致）

#### Scenario: 漏注册 hint 时给出诊断
- **WHEN** native test 因反射缺失抛 `ClassNotFoundException` 或 `NoSuchMethodException`
- **THEN** 失败堆栈 SHALL 完整保留触发的 FQCN
- **AND** `scripts/native-test.sh` SHALL grep 该堆栈并以 `[HINT-MISSING] <class>` 行汇总

### Requirement: Mockito native 兼容策略

测试依赖 SHALL 包含 `mockito-inline-mock-maker`（test scope），并在 native-image build args 中显式 `--initialize-at-run-time=org.mockito.internal.creation.bytebuddy.MockMethodAdvice`；不能 native 化的测试用例 SHALL 使用 `@DisabledInNativeImage(value="<reason>")` 显式标记。

#### Scenario: 默认 @MockBean 测试可在 native 下运行
- **WHEN** 测试类使用 `@MockBean SomeService svc` 替换 bean
- **AND** 在 native test profile 下运行
- **THEN** Mockito SHALL 通过 `mockito-inline-mock-maker` 创建 proxy
- **AND** 测试方法 SHALL 正常执行

#### Scenario: 不兼容用例显式禁用
- **WHEN** 某测试用例需要 mock final class 或 static method
- **THEN** 该用例 SHALL 标 `@DisabledInNativeImage(value="<具体不兼容原因>")`
- **AND** JVM 测试 SHALL 仍照常运行该用例

#### Scenario: 标记规则文档化
- **WHEN** 开发者新增测试时
- **THEN** `kuship-console/CLAUDE.md` SHALL 提供"何时加 `@DisabledInNativeImage` 注解"的 checklist
- **AND** checklist SHALL 至少覆盖 final class mock / static mock / 反射访问私有字段三种场景

### Requirement: AbstractIntegrationTest native 端口兜底

测试基类 SHALL 在 native image 模式（通过 `org.graalvm.nativeimage.imagecode` 系统属性检测）下使用 `webEnvironment=DEFINED_PORT` + `server.port=0`，JVM 模式下保持 `RANDOM_PORT`。

#### Scenario: native 下走 DEFINED_PORT
- **WHEN** native test 启动 `@SpringBootTest` 基类
- **AND** 系统属性 `org.graalvm.nativeimage.imagecode` 存在
- **THEN** 基类 SHALL 注入 `webEnvironment=DEFINED_PORT` 与 `server.port=0`
- **AND** 测试可通过 `@LocalServerPort` 注入 OS 分配的实际端口

#### Scenario: JVM 下保持原状
- **WHEN** 现有 102 个 JVM 测试用例运行
- **THEN** 基类 SHALL 维持 `RANDOM_PORT` 行为
- **AND** 测试结果 SHALL 与 hardening 前完全一致

### Requirement: native-test.sh 一键脚本

`scripts/native-test.sh` SHALL 提供一键命令执行 `mvn -Pnative,native-test test`，并在退出前打印 pass / fail / skipped 统计与 hint 缺失诊断；脚本检测到本地无 GraalVM 21 时 SHALL 立即报错并指引用户安装路径。

#### Scenario: 脚本检测 GraalVM 安装
- **WHEN** 用户在没有 GraalVM 的环境运行 `bash scripts/native-test.sh`
- **THEN** 脚本 SHALL 检查 `native-image --version` 返回非零
- **AND** 立即报错 `GraalVM 21 community not found, install via 'sdk install java 21.0.2-graalce'`
- **AND** 退出码非零

#### Scenario: 脚本输出统计与诊断
- **WHEN** 脚本完成 mvn 执行
- **THEN** 末尾 SHALL 打印 `[SUMMARY] passed=X failed=Y skipped=Z`
- **AND** 如有 ClassNotFoundException / NoSuchMethodException 堆栈 SHALL grep 出 `[HINT-MISSING] <class>` 行
- **AND** 退出码 SHALL 与 mvn 一致（保留 CI 失败传播）

### Requirement: native-test CI 工作流

CI workflow SHALL 提供独立的 `native-test` job，使用 `org.graalvm.buildtools/setup-graalvm@v1` action 安装 GraalVM 21 community，运行 `bash scripts/native-test.sh`；初版 SHALL 标 `continue-on-error: true`，pass rate 持续 ≥ 90% 满 2 周后移除。

#### Scenario: native-test job 不阻塞主线
- **WHEN** PR push 触发 CI
- **THEN** native-test job SHALL 标 `continue-on-error: true`
- **AND** 即使 native-test 失败 PR 仍可合并（依赖 JVM test job 必过）

#### Scenario: pass rate 监控
- **WHEN** native-test job 完成
- **THEN** job 输出 SHALL 包含 `pass_rate=<percent>` 行供下游观测
- **AND** 当 pass rate ≥ 90% 持续 2 周时 SHALL 提交 PR 移除 `continue-on-error: true`

### Requirement: native test 用例覆盖率目标

测试套件 SHALL 在 native test profile 下达到 ≥ 90% pass rate（基线 92/102），不能 native 化的用例 SHALL ≤ 10 个并集中标 `@DisabledInNativeImage`，每个均提供 `value` 字段说明原因。

#### Scenario: 92/102 通过下限
- **WHEN** 执行 `bash scripts/native-test.sh`
- **THEN** 至少 92 个用例 SHALL 通过
- **AND** 不超过 10 个用例标 `@DisabledInNativeImage`

#### Scenario: 禁用用例可审计
- **WHEN** 检视所有 `@DisabledInNativeImage` 注解
- **THEN** 每个 SHALL 包含 `value="<具体原因>"`，例如 `"final class mock not supported"` 或 `"@MockitoSettings reflection limitation"`
- **AND** 原因 SHALL 不超过 3 类（final/static/private-field）

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

### Requirement: WebhookSignatureVerifier

系统 SHALL 提供 `WebhookSignatureVerifier` 共享组件，封装 GitHub `X-Hub-Signature-256`、GitLab `X-Gitlab-Token`、Harbor `Authorization: Bearer`、kuship custom `X-Kuship-Signature` 四种 webhook 签名/令牌验证；HMAC 算法使用 HMAC-SHA256；secret 比对使用 `MessageDigest.isEqual` 常量时间方法。

#### Scenario: GitHub HMAC-SHA256 通过
- **WHEN** 客户端 POST 带 `X-Hub-Signature-256: sha256=<hex>` 头，hex 是 HMAC-SHA256(secret, raw_body)
- **THEN** verifier `verifyGitHub` 返回 true

#### Scenario: GitHub HMAC 签名错误
- **WHEN** `X-Hub-Signature-256` 头存在但 hex 与 HMAC 不一致
- **THEN** verifier 返回 false
- **AND** 不触发任何下游业务调用

#### Scenario: GitLab token 通过
- **WHEN** 客户端 POST 带 `X-Gitlab-Token: <secret>` 头，secret 等于 service.secret
- **THEN** verifier `verifyGitLab` 返回 true（常量时间比对）

#### Scenario: Harbor bearer 通过
- **WHEN** 客户端 POST 带 `Authorization: Bearer <secret>` 头，secret 等于 service.secret
- **THEN** verifier `verifyHarbor` 返回 true

#### Scenario: custom HMAC 通过
- **WHEN** 客户端 POST 带 `X-Kuship-Signature: sha256=<hex>` 头，hex 是 HMAC-SHA256(secret, raw_body)
- **THEN** verifier `verifyCustom` 返回 true

### Requirement: trigger 端点 header 签名优先

`WebhookTriggerController` 的 git / image / custom 三个 trigger 端点 SHALL 优先检查对应 header 签名（git: GitHub/GitLab；image: Harbor；custom: kuship），命中即按 header 验签；任意 header 命中且签名失败 SHALL 拒绝（不 fallback 到 secret query）。

#### Scenario: git trigger 优先 GitHub HMAC
- **WHEN** POST `/console/webhooks/{service_id}` 带有效 `X-Hub-Signature-256`
- **THEN** trigger SHALL 仅用 GitHub HMAC 验签
- **AND** 不读取 `secret` query 参数

#### Scenario: git trigger 缺 GitHub 头时尝试 GitLab token
- **WHEN** POST `/console/webhooks/{service_id}` 缺 `X-Hub-Signature-256` 但有 `X-Gitlab-Token`
- **THEN** trigger SHALL 用 GitLab token 验证
- **AND** 不读取 `secret` query

#### Scenario: image trigger 优先 Harbor bearer
- **WHEN** POST `/console/image/webhooks/{service_id}` 带 `Authorization: Bearer <token>`
- **THEN** trigger SHALL 用 Harbor bearer 验证
- **AND** 不读取 `secret` query

#### Scenario: header 签名错误不退回 query
- **WHEN** POST 带 `X-Hub-Signature-256` 但签名错误
- **THEN** trigger SHALL 立即返回 401
- **AND** 不再读取 `secret` query 重试

### Requirement: secret query fallback 与 deprecation

trigger 端点 SHALL 在所有 header 签名都缺失时退回 `?secret=<x>` query 校验；fallback 走通时 SHALL 输出 WARN 日志 `webhook <kind> for service <service_id> using deprecated query secret; switch to header signature`，并 SHALL 在 `WebhookManageController.getUrl` 输出 deprecation 提示字段。

#### Scenario: 缺 header 时 query 仍 work
- **WHEN** POST `/console/webhooks/{service_id}?secret=<x>` 不带任何 header 签名
- **AND** secret 等于 service.secret
- **THEN** trigger SHALL 通过校验并返回 200
- **AND** 输出 WARN 日志记录 deprecation

#### Scenario: getUrl 输出 v2 URL
- **WHEN** GET `/console/teams/{team_name}/apps/{service_alias}/webhooks/get-url`
- **THEN** 响应 SHALL 包含 `git_webhook_url_v2`、`image_webhook_url_v2`、`custom_webhook_url_v2` 三个不带 secret query 的 URL
- **AND** 响应 SHALL 包含 `signature_examples` 字段提示用户三种 header 写法
- **AND** 旧字段 `git_webhook_url` / `image_webhook_url` / `custom_webhook_url`（带 secret query）保留兼容

### Requirement: 反重放 delivery dedup

trigger 端点 SHALL 在收到 `X-GitHub-Delivery`、`X-Gitlab-Event-UUID` 或 `X-Kuship-Delivery` 头时，使用 Caffeine 内存缓存（key=`<service_id>:<delivery_id>`、TTL 5 分钟、maxSize 1024）做去重；命中重复 delivery_id 时直接返回 200 + `{triggered: false, dedup: true}`，不调下游 region API。

#### Scenario: 同 delivery_id 5 分钟内不重复触发
- **WHEN** POST 带 `X-GitHub-Delivery: <uuid>` 通过 HMAC 验签后
- **AND** 客户端 1 分钟后再发同 uuid
- **THEN** trigger SHALL 第一次正常触发，第二次返回 200 + `{triggered:false, dedup:true}`
- **AND** lifecycleOps.upgradeService 仅被调用一次

#### Scenario: 不同 service 的相同 delivery_id 不冲突
- **WHEN** service_a 收到 delivery `abc` 后
- **AND** service_b 收到 delivery `abc`
- **THEN** 两次 trigger SHALL 都正常执行
- **AND** cache key 包含 service_id 前缀避免冲突

### Requirement: 常量时间签名比对

WebhookSignatureVerifier 的所有 secret / HMAC 比对 SHALL 使用 `MessageDigest.isEqual(byte[], byte[])` 常量时间方法，禁止使用 `String.equals()` 或 `Arrays.equals()`。

#### Scenario: 长度差异不提前返回
- **WHEN** verifier 比对一个 64 字节签名与一个 1 字节签名
- **THEN** 比对耗时 SHALL 与同长度比对耗时无显著差异（通过 unit test 多次采样的 stddev 检验通过即可）

### Requirement: 文档与 deprecation 时间表

`kuship-console/CLAUDE.md` 的 webhook 段落 SHALL 列出：四种 header 签名格式表、secret query deprecation 时间表（本 change 起 6 个月警告，此后独立 `enforce-webhook-signatures` change 移除）、运维监控 WARN 日志的方法。

#### Scenario: 文档段落齐全
- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档 SHALL 包含 "Webhook HMAC 签名（harden-webhook-hmac）" 段落
- **AND** 段落 SHALL 至少包含 4 行签名格式说明 + deprecation 路径 + 监控指引

### Requirement: SmsProvider 抽象接口

系统 SHALL 提供 `SmsProvider` 接口（位于 `cn.kuship.console.modules.misc.sms.provider`），暴露 `send(String phone, String code, String purpose)` 方法返回 `SmsResult`；至少 SHALL 提供 `LoggingSmsProvider`（默认 + dev / local / contract-test profile）和 `AliyunSmsProvider`（prod）两种实现，通过 `kuship.sms.provider` 配置项选择；缺省值 `logging`。

#### Scenario: 默认配置使用 LoggingSmsProvider
- **WHEN** 应用启动且 `kuship.sms.provider` 未设置或等于 `logging`
- **THEN** Spring 上下文 SHALL 注入 `LoggingSmsProvider`
- **AND** 调用 `send` SHALL 仅打印 `[SMS-MVP] phone=... code=...` 日志，不发真实短信

#### Scenario: aliyun 配置使用 AliyunSmsProvider
- **WHEN** 应用启动且 `kuship.sms.provider=aliyun`
- **AND** 4 个必需配置项（access-key-id / access-key-secret / sign-name / template-code）全部非空
- **THEN** Spring 上下文 SHALL 注入 `AliyunSmsProvider`
- **AND** 调用 `send` SHALL 通过 aliyun-dysmsapi SDK 发送真实短信

### Requirement: AliyunSmsProvider 启动校验

`AliyunSmsProvider` SHALL 在 `@PostConstruct` 阶段验证 4 个必需配置项（access-key-id / access-key-secret / sign-name / template-code），缺任一项 SHALL 抛 `IllegalStateException` 让 Spring fail-fast 拒绝启动。

#### Scenario: 缺 access-key-id 拒绝启动
- **WHEN** 应用以 `kuship.sms.provider=aliyun` 启动但 `kuship.sms.aliyun.access-key-id` 为空
- **THEN** AliyunSmsProvider 的 @PostConstruct SHALL 抛 IllegalStateException("aliyun-sms: access-key-id is required")
- **AND** Spring SHALL 拒绝启动整个 ApplicationContext

#### Scenario: 4 项齐全则启动通过
- **WHEN** 4 项配置都非空
- **THEN** AliyunSmsProvider SHALL 成功初始化 SDK Client 实例
- **AND** 控制台日志 SHALL 输出 `aliyun-sms provider initialised, endpoint=<endpoint>`

### Requirement: 单手机号 60s 限流

`SMSVerificationController.sendCode` SHALL 在调 SmsProvider 前查 `SmsRateLimiter` 限流；同一手机号 60 秒窗口内最多发 1 条；命中限流 SHALL 返回 429 + `{detail: "rate limited", code: 429}` 风格错误。

#### Scenario: 60s 内第二次请求被拒
- **WHEN** 客户端 1 秒内对同一手机号连续调 send-code 两次
- **THEN** 第二次 SHALL 返回业务错误 + msg 含"频率"
- **AND** 数据库 SHALL 仅插入一条 sms_verification_code 记录

#### Scenario: 不同手机号互不影响
- **WHEN** phone-a 与 phone-b 各自请求 send-code
- **THEN** 两次都 SHALL 通过限流
- **AND** 各自插入 sms_verification_code 记录

### Requirement: 验证码失败次数限流

注册 / 登录验证码比对 SHALL 在 5 分钟窗口对单 (phone, purpose) 计数失败次数；失败 ≥ 5 次后 SHALL 拒绝继续比对（不去查 DB），并返回业务错误"验证码已锁定，请稍后重试"。

#### Scenario: 第 5 次失败正常返回 401
- **WHEN** 客户端连续 5 次用错码 verify
- **THEN** 5 次都 SHALL 返回 401 "code mismatch"
- **AND** 计数器累加到 5

#### Scenario: 第 6 次失败被锁定
- **WHEN** 已经失败 5 次后再次 verify
- **THEN** SHALL 立即返回 429 + "验证码已锁定，请稍后重试"
- **AND** 不查询 sms_verification_code 表

#### Scenario: 5 分钟后窗口重置
- **WHEN** 失败 5 次后等待 5 分钟以上
- **THEN** 计数器 SHALL 因 cache 过期被清空
- **AND** 下次 verify SHALL 重新走 DB 比对

### Requirement: SMS 配置项规范

应用 SHALL 通过以下 yaml 配置项控制 SMS 行为；prod profile 推荐通过环境变量注入凭据：

| 配置项 | 默认值 | 来源建议 |
|---|---|---|
| `kuship.sms.provider` | `logging` | yaml |
| `kuship.sms.aliyun.access-key-id` | （空） | env `ALIYUN_SMS_ACCESS_KEY_ID` |
| `kuship.sms.aliyun.access-key-secret` | （空） | env `ALIYUN_SMS_ACCESS_KEY_SECRET` |
| `kuship.sms.aliyun.sign-name` | （空） | yaml |
| `kuship.sms.aliyun.template-code` | （空） | yaml |
| `kuship.sms.aliyun.endpoint` | `dysmsapi.aliyuncs.com` | yaml |
| `kuship.sms.rate-limit.enabled` | `true` (prod) / `false` (dev) | yaml |

#### Scenario: 配置项默认值正确
- **WHEN** 应用以 dev profile 启动
- **AND** 未显式设 `kuship.sms.*`
- **THEN** Spring 配置 SHALL 解析出 provider=logging、rate-limit.enabled=false
- **AND** 应用启动通过 + 调用 send-code 不发真实短信

### Requirement: SMS GraalVM Native 兼容

aliyun-dysmsapi SDK SHALL 在 GraalVM native binary 下可用；如 SDK 自带的 `META-INF/native-image/` hint 不全，SHALL 在 `KuShipConsoleRuntimeHints` 补充必要反射类型。

#### Scenario: native binary 启动 aliyun provider
- **WHEN** 用 native 模式启动应用
- **AND** `kuship.sms.provider=aliyun` 且 4 项配置齐全
- **THEN** AliyunSmsProvider SDK Client SHALL 成功构造
- **AND** 调用 send 不抛 ClassNotFoundException 或 NoSuchMethodException

### Requirement: SMS 文档与运维指引

`kuship-console/CLAUDE.md` SHALL 新增 "SMS 集成（add-aliyun-sms）" 段落，列出：provider 选择策略、阿里云模板创建步骤、RAM 子账号 + K8s Secret 注入流程、限流策略、暴破防护、未来 hardening（多 provider / 回执回调 / 分布式限流）。

#### Scenario: SMS 文档段落齐全
- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档 SHALL 包含 "SMS 集成（add-aliyun-sms）" 段落
- **AND** 段落 SHALL 至少含：provider 切换矩阵、RAM 子账号建议、限流参数、暴破防护说明
