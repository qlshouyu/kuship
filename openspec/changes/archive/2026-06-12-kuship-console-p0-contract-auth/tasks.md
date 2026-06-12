## 1. 工程骨架与依赖

- [x] 1.1 创建 `kuship-console/` 工程与 `pom.xml`（Spring Boot 4.0.6 / Java 21 / spring-boot-starter-web / data-jpa / security / data-redis / validation / actuator；jjwt；httpclient5；mapstruct；lombok；springdoc）
- [x] 1.2 建立 `cn.kuship.console` 包结构：`config` / `common`（response·exception·security·context·page·trace·util）/ `infrastructure`（jpa·region）/ `modules` / `healthz`
- [x] 1.3 `KuShipConsoleApplication` 启动类 + `application.yml`（端口 8000、MySQL `console` 数据源、Redis、`hibernate.ddl-auto=validate`、**不设** `server.servlet.context-path`）
- [x] 1.4 `GET /console/healthz` 健康检查（显式 `/console/...` 前缀，验证服务存活返回信封）

## 2. 响应信封契约（response-contract）

- [x] 2.1 `ApiResult` / `GeneralMessage` 信封模型：字段顺序 `code→msg→msg_show→data`，`msg_show` 用 `@JsonProperty("msg_show")`，`data` 恒含 `bean`(`{}`) 与 `list`(`[]`)
- [x] 2.2 `GeneralMessageResponseBodyAdvice`：POJO/Map→bean、List→list、Page→list+bean.total、ApiResult 幂等不重包、String 不包
- [x] 2.3 `@SkipResponseWrapper` 注解 + Advice 跳过逻辑（SSE/文件下载）
- [x] 2.4 契约测试：四类返回值（POJO/List/Page/ApiResult）与跳过端点的信封结构断言

## 3. 异常映射契约（response-contract）

- [x] 3.1 `ServiceHandleException`（`status_code` + 中文 `msg_show`）与必要异常类型
- [x] 3.2 `GlobalExceptionHandler`：业务异常透传、参数校验→400、Region 异常→优先 httpStatus、兜底 Exception→500
- [x] 3.3 TraceId 横切（过滤器/MDC）+ 兜底 500 在 `data.bean.trace_id` 回填
- [x] 3.4 契约测试：HTTP 状态码 = 业务 code、400/500 路径与 trace_id 存在性断言

## 4. 数据层底座（persistence-baseline）

- [x] 4.1 `JpaConfig` + `BaseEntity`/审计字段约定；确认 `validate` 模式与无 DDL 输出
- [x] 4.2 `UserInfo` 实体反向映射 `user_info` 表（精确对齐列名/类型/可空性；区分自增 ID 与业务 UUID；无 `@Version`）
- [x] 4.3 `RegionConfig` 实体反向映射 `region_info` 表（供 RegionClient 取地址/凭证）
- [x] 4.4 关系约定落地：`@ForeignKey(NO_CONSTRAINT)` / 外键 ID + 手动关联，不级联
- [x] 4.5 启动校验测试：实体与既有 schema 对齐通过；故意制造不匹配验证 fail-fast

## 5. JWT 认证（jwt-authentication）

- [x] 5.1 `JwtClaims` + jjwt HS256 签发/验签工具，claims 用 Django 风格 `user_id/username/nick_name/email/exp/orig_iat`（不转换名）
- [x] 5.2 `JwtAuthenticationFilter`：解析 `GRJWT`/`jwt` 双前缀（大小写不敏感）、验签、加载 `user_info` 校验 `user_id` 存在
- [x] 5.3 `JWT_SECRET_KEY` 启动强校验：非 local profile 为空则拒绝启动（fail-fast）
- [x] 5.4 `SecurityConfig`：放行匿名端点（login/healthz）、保护其余、401/403 handler 返回统一信封
- [x] 5.5 测试：GRJWT/jwt 前缀、缺 token→401、user_id 不存在→401（`msg` 具体 + `msg_show` 中文）

## 6. Redis 会话黑名单（jwt-authentication）

- [x] 6.1 Spring Data Redis 配置 + 黑名单存取（key 用 token 或 `user_id`+`orig_iat`，TTL 对齐剩余 `exp`）
- [x] 6.2 `JwtAuthenticationFilter` 验签后查黑名单，命中→401
- [x] 6.3 测试：拉黑 token 被拦截、条目随 TTL 过期清除

## 7. 多租户上下文（tenant-context）

- [x] 7.1 `RequestContext`(`@RequestScope`)：currentUser / enterpriseId / teamName / regionName
- [x] 7.2 过滤器验签后写入真实 user；`TenantContextInterceptor` 从路径变量 `{team_name}`/`{region_name}` 注入
- [x] 7.3 测试：路径变量注入、并发请求上下文隔离、请求结束销毁

## 8. 登录鉴权闭环（user-authentication）

- [x] 8.1 `POST /console/users/login`（form `nick_name`+`password`）：校验 `user_info` 密码 → 签发 JWT → `data.bean.token`
- [x] 8.2 登出接口：把当前 token 写入 Redis 黑名单
- [x] 8.3 测试：登录成功/密码错/用户不存在；登出后同 token→401

## 9. RegionClient 骨架（region-client）

- [x] 9.1 `RegionClient` 基于 HttpClient5：从 `RegionConfig`(`region_info`) 取地址/凭证，按 URL+SSL 缓存连接池
- [x] 9.2 认证优先级（企业 Token→区域 token/env→双向 TLS）+ `REGION_SSL_VERIFY` 默认 false + BouncyCastle 证书加载
- [x] 9.3 重试默认 2 次 + 超时梯度（2s→10/15s→20s→300s）+ 多 region 扩展点预留（**不实现** `/v2/tenants/...` 域方法）
- [x] 9.4 测试：地址/凭证解析、认证优先级、重试/超时配置生效

## 10. 端到端验收（对照 7070）

- [x] 10.1 rainbond-ui 代理 `proxyTarget` 指向 8000，完成登录、拿到 `data.bean.token`、`healthz` 通过
- [x] 10.2 与 rainbond-console(7070) 对照：登录出参/信封字段顺序与 snake_case/错误码/JWT 双向互认一致
- [x] 10.3 用同源 `JWT_SECRET_KEY` 验证两端 token 互认（7070 签发的 token 能被 8000 接受，反之亦然）

## 实现偏差记录（对照参考代码校准后的变更）

- **JWT 改用 `javax.crypto` HmacSHA256 自实现，弃用 jjwt**（影响 5.1）：jjwt 强制 HS256 密钥 ≥256 位，而 Django `SECRET_KEY` 长度任意。自实现等价于 PyJWT，已实测两端 token **双向互认**。
- **token 无 `orig_iat`**（影响 5.1）：实测 `JWT_ALLOW_REFRESH=False`，载荷为 `user_id/username/email/nick_name/exp`；黑名单键改用 `sha256(token)`。
- **分页 total 在 `data.total` 顶层而非 `data.bean.total`**（影响 2.2）：实测 `general_message(..., total=total)` 把 total 放在 `data` 顶层。design/spec 已同步修正。
- **密钥来自环境变量 `SECRET_KEY`**（drf-jwt 的 `JWT_SECRET_KEY=SECRET_KEY`），非 `JWT_SECRET_KEY`。
- **springdoc 未纳入 P0**：避免 Boot 4 兼容风险，且登录/healthz 验收不需要 Swagger，推迟到需要时再加。
- **BaseEntity 暂不抽取**：`user_info` 主键 `user_id`、BaseModel 系列主键 `ID`，命名不一致，逐表显式映射更安全。
- **登录验证码/冻结逻辑（captcha/freeze）未纳入 P0**：属安全加固，非核心契约；正常登录/错误码路径已对齐。
- **端到端验收方式**：以 Docker MySQL+Redis 起本服务实测（healthz/登录/401/黑名单/PyJWT 双向互认/validate 通过）；与运行中的 7070 直接对照需待真实 rainbond 环境，已在 README 给出联调步骤。
