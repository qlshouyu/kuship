## 1. 工程骨架与依赖

- [ ] 1.1 创建 `kuship-console/` 工程与 `pom.xml`（Spring Boot 4.0.6 / Java 21 / spring-boot-starter-web / data-jpa / security / data-redis / validation / actuator；jjwt；httpclient5；mapstruct；lombok；springdoc）
- [ ] 1.2 建立 `cn.kuship.console` 包结构：`config` / `common`（response·exception·security·context·page·trace·util）/ `infrastructure`（jpa·region）/ `modules` / `healthz`
- [ ] 1.3 `KuShipConsoleApplication` 启动类 + `application.yml`（端口 8000、MySQL `console` 数据源、Redis、`hibernate.ddl-auto=validate`、**不设** `server.servlet.context-path`）
- [ ] 1.4 `GET /console/healthz` 健康检查（显式 `/console/...` 前缀，验证服务存活返回信封）

## 2. 响应信封契约（response-contract）

- [ ] 2.1 `ApiResult` / `GeneralMessage` 信封模型：字段顺序 `code→msg→msg_show→data`，`msg_show` 用 `@JsonProperty("msg_show")`，`data` 恒含 `bean`(`{}`) 与 `list`(`[]`)
- [ ] 2.2 `GeneralMessageResponseBodyAdvice`：POJO/Map→bean、List→list、Page→list+bean.total、ApiResult 幂等不重包、String 不包
- [ ] 2.3 `@SkipResponseWrapper` 注解 + Advice 跳过逻辑（SSE/文件下载）
- [ ] 2.4 契约测试：四类返回值（POJO/List/Page/ApiResult）与跳过端点的信封结构断言

## 3. 异常映射契约（response-contract）

- [ ] 3.1 `ServiceHandleException`（`status_code` + 中文 `msg_show`）与必要异常类型
- [ ] 3.2 `GlobalExceptionHandler`：业务异常透传、参数校验→400、Region 异常→优先 httpStatus、兜底 Exception→500
- [ ] 3.3 TraceId 横切（过滤器/MDC）+ 兜底 500 在 `data.bean.trace_id` 回填
- [ ] 3.4 契约测试：HTTP 状态码 = 业务 code、400/500 路径与 trace_id 存在性断言

## 4. 数据层底座（persistence-baseline）

- [ ] 4.1 `JpaConfig` + `BaseEntity`/审计字段约定；确认 `validate` 模式与无 DDL 输出
- [ ] 4.2 `UserInfo` 实体反向映射 `user_info` 表（精确对齐列名/类型/可空性；区分自增 ID 与业务 UUID；无 `@Version`）
- [ ] 4.3 `RegionConfig` 实体反向映射 `region_info` 表（供 RegionClient 取地址/凭证）
- [ ] 4.4 关系约定落地：`@ForeignKey(NO_CONSTRAINT)` / 外键 ID + 手动关联，不级联
- [ ] 4.5 启动校验测试：实体与既有 schema 对齐通过；故意制造不匹配验证 fail-fast

## 5. JWT 认证（jwt-authentication）

- [ ] 5.1 `JwtClaims` + jjwt HS256 签发/验签工具，claims 用 Django 风格 `user_id/username/nick_name/email/exp/orig_iat`（不转换名）
- [ ] 5.2 `JwtAuthenticationFilter`：解析 `GRJWT`/`jwt` 双前缀（大小写不敏感）、验签、加载 `user_info` 校验 `user_id` 存在
- [ ] 5.3 `JWT_SECRET_KEY` 启动强校验：非 local profile 为空则拒绝启动（fail-fast）
- [ ] 5.4 `SecurityConfig`：放行匿名端点（login/healthz）、保护其余、401/403 handler 返回统一信封
- [ ] 5.5 测试：GRJWT/jwt 前缀、缺 token→401、user_id 不存在→401（`msg` 具体 + `msg_show` 中文）

## 6. Redis 会话黑名单（jwt-authentication）

- [ ] 6.1 Spring Data Redis 配置 + 黑名单存取（key 用 token 或 `user_id`+`orig_iat`，TTL 对齐剩余 `exp`）
- [ ] 6.2 `JwtAuthenticationFilter` 验签后查黑名单，命中→401
- [ ] 6.3 测试：拉黑 token 被拦截、条目随 TTL 过期清除

## 7. 多租户上下文（tenant-context）

- [ ] 7.1 `RequestContext`(`@RequestScope`)：currentUser / enterpriseId / teamName / regionName
- [ ] 7.2 过滤器验签后写入真实 user；`TenantContextInterceptor` 从路径变量 `{team_name}`/`{region_name}` 注入
- [ ] 7.3 测试：路径变量注入、并发请求上下文隔离、请求结束销毁

## 8. 登录鉴权闭环（user-authentication）

- [ ] 8.1 `POST /console/users/login`（form `nick_name`+`password`）：校验 `user_info` 密码 → 签发 JWT → `data.bean.token`
- [ ] 8.2 登出接口：把当前 token 写入 Redis 黑名单
- [ ] 8.3 测试：登录成功/密码错/用户不存在；登出后同 token→401

## 9. RegionClient 骨架（region-client）

- [ ] 9.1 `RegionClient` 基于 HttpClient5：从 `RegionConfig`(`region_info`) 取地址/凭证，按 URL+SSL 缓存连接池
- [ ] 9.2 认证优先级（企业 Token→区域 token/env→双向 TLS）+ `REGION_SSL_VERIFY` 默认 false + BouncyCastle 证书加载
- [ ] 9.3 重试默认 2 次 + 超时梯度（2s→10/15s→20s→300s）+ 多 region 扩展点预留（**不实现** `/v2/tenants/...` 域方法）
- [ ] 9.4 测试：地址/凭证解析、认证优先级、重试/超时配置生效

## 10. 端到端验收（对照 7070）

- [ ] 10.1 rainbond-ui 代理 `proxyTarget` 指向 8000，完成登录、拿到 `data.bean.token`、`healthz` 通过
- [ ] 10.2 与 rainbond-console(7070) 对照：登录出参/信封字段顺序与 snake_case/错误码/JWT 双向互认一致
- [ ] 10.3 用同源 `JWT_SECRET_KEY` 验证两端 token 互认（7070 签发的 token 能被 8000 接受，反之亦然）
