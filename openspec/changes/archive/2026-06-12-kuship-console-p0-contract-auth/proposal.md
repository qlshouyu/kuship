## Why

rainbond-console 服务端是 Python/Django 实现，本项目要用 Java 21 / Spring Boot 4.0.6 从零重做为 kuship-console，**完全替换**旧实现。可行性已确认：Rainbond 是 console ↔ region-api ↔ K8s 三层架构，只换最上层 console 的实现语言，对上游 rainbond-ui、对下游 region-api 的协议契约一字不改（三条不可变契约已对真实代码实测验证）。

本轮是整个迁移的 **P0 最底座**：固化不可变契约层并打通登录鉴权。它是后续所有业务域（account/team、region、application…）迁移的前提——没有响应信封、错误码、JWT、多租户上下文这套地基，任何业务接口都无法与 rainbond-ui 兼容。

## What Changes

- 新建 `kuship-console/` Java 工程骨架（`cn.kuship.console` 包：config/common/infrastructure/modules/healthz），`pom.xml` 锁定 Spring Boot 4.0.6 / Java 21 / JPA+Hibernate6 / Spring Security / jjwt / HttpClient5 / Spring Data Redis / MapStruct / Lombok / springdoc。
- **响应信封自动包装**：返回值自动包成 `{code,msg,msg_show,data:{bean,list}}`，字段顺序与 snake_case 强制对齐 `general_message`；`@SkipResponseWrapper` 用于 SSE/文件下载例外。
- **全局异常映射**：`ServiceHandleException` + `GlobalExceptionHandler`，HTTP 状态码 = 业务 code，兜底带 `trace_id`。
- **JWT 认证**：`GRJWT`/`jwt` 双前缀、HS256、`JWT_SECRET_KEY` 与 rainbond-console 同源、Django 风格 claims、`user_id` 必须存在于 `user_info` 表。
- **Redis 会话黑名单**（首版必需）：每请求验签后查黑名单（命中 401），登出写黑名单、TTL 对齐 `exp`。
- **多租户请求上下文**：`RequestContext(@RequestScope)` + `TenantContextInterceptor`，从路径变量 `{team_name}`/`{region_name}` 注入。
- **登录鉴权闭环**：`POST /console/users/login`（form：`nick_name`+`password`）校验 `user_info` → 签发 JWT → 返回 `data.bean.token`；登出接口；`GET /console/healthz`。
- **数据层底座**：JPA 连接共享 MySQL `console` 库，`hibernate.ddl-auto=validate`（**绝不输出 DDL**），P0 必需实体（`UserInfo` 等）反向映射既有 schema，无 DB 外键。
- **RegionClient 骨架**（仅骨架，单 region-api）：HttpClient5 封装，从 `region_info` 表取地址与凭证，token/双向 TLS、重试与超时梯度，预留多 region 扩展点；**具体域方法不在本轮**。

非本轮（推迟）：`/openapi/v1` 对外开放 API、多 region / rke2 多集群管理、各业务域接口。

## Capabilities

### New Capabilities
- `response-contract`: 统一响应信封自动包装 + 全局异常/错误码映射，1:1 对齐 rainbond-console 的 `general_message` 与 DRF 异常语义。
- `jwt-authentication`: JWT 验签过滤器（`GRJWT`/`jwt` 双前缀、HS256、与 rainbond-console 同源密钥、`user_info` 存在校验）+ Redis 会话黑名单。
- `user-authentication`: 登录/登出闭环——`POST /console/users/login` 签发 token、登出写黑名单。
- `tenant-context`: 多租户请求上下文（`enterprise_id`/`team_name`/`region_name`）从路径变量注入。
- `persistence-baseline`: 共享 `console` 库的 JPA 底座（`validate` 模式、无 DB 外键约定、P0 实体反向映射）+ 工程骨架 + `healthz`。
- `region-client`: 单 region-api 的 RegionClient 骨架（地址/凭证来源、TLS/token、重试与超时梯度、多 region 扩展点）。

### Modified Capabilities
<!-- 无：本项目 specs/ 为空，本轮全部为新建能力 -->

## Impact

- **新增代码**：`kuship-console/`（全新 Java 工程，从零）。
- **共享数据库**：连接既有 MySQL `console` 库，仅 `validate`，不改 schema（schema 属权归 Django migrations）。
- **下游**：region-api 兼容基线钉死 Rainbond **v6.9.0-release** / `reference/rainbond@44c5c34d`。
- **前端**：rainbond-ui **零源码改动**，仅把代理 `proxyTarget` 由 7070 指向 8000。
- **新增运行依赖**：Redis（JWT 会话黑名单）。
- **替换关系**：完全替换旧 rainbond-console，旧的停掉，无共享库并发双写。
- **验收**：rainbond-ui 指向 8000 后能完成登录、拿到 token、`healthz` 通过；响应信封/错误码/JWT 行为与 rainbond-console(7070) 一致。
