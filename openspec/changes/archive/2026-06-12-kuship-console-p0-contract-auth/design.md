## Context

完整背景、系统全景、被复刻对象 rainbond-console 现状测绘见 `docs/kuship-console-架构设计.md`（真相源）。本文件只承载 P0 一轮的技术决策。

现状：rainbond-console 是 Python 3.6 / Django 2.2 / DRF 3.8 实现，~577 端点、~123 张表，共享 MySQL `console` 库，经 region-api 操作 K8s。本轮用 Java 21 / Spring Boot 4.0.6 从零重做 P0 地基，**完全替换**旧实现，rainbond-ui 仅改代理目标即可直连。

三条不可变契约（已对真实代码实测验证，是 P0 的硬约束）：
1. **URL**：`config.js:67` 把 `/console` 整段代理到后端 → controller 必须显式声明完整 `/console/...` 前缀，路径变量保持 snake_case。
2. **鉴权**：`request.js:222` 写死 `Authorization: GRJWT <token>`，`token.js:17` 接受 `GRJWT/JWT/Bearer` → 同源 `JWT_SECRET_KEY` + HS256 双向互认。
3. **响应信封**：`return_message.py:4` `general_message(code,msg,msg_show,bean,list,...)` → 自动包装且字段顺序/snake_case 强制对齐。

## Goals / Non-Goals

**Goals:**
- 固化契约层（响应信封 / 异常映射 / JWT+Redis 黑名单 / 多租户上下文）一次到位，后续业务域零契约改动地往上灌。
- 打通登录鉴权闭环 + healthz，让 rainbond-ui 指向 8000 即可登录拿 token。
- 建立共享 `console` 库的 JPA 底座（`validate`，无 DB 外键约定）与 RegionClient 骨架。

**Non-Goals:**
- 不实现任何业务域接口（account/team/region/application…）。
- 不实现 RegionClient 的具体域方法（仅骨架与传输层）。
- 不做 `/openapi/v1`、多 region / rke2、monitor。
- 不输出任何业务 DDL；不改 `console` 库 schema。

## Decisions

**D1 响应信封用 `ResponseBodyAdvice` 自动包装，而非每个 controller 手动包。**
- 映射：POJO/Map→`data.bean`；`List`→`data.list`；`Page`→`data.list=content`+`data.total`（**实测 `general_message(..., total=total)` 把 total 放在 `data` 顶层，不是 `data.bean`**）；`ApiResult` 幂等不重包；`String` 不自动包（Spring 特殊处理）；`@SkipResponseWrapper` 用于 SSE/文件下载。
- 顶层字段顺序 `code→msg→msg_show→data`；`msg_show` 用 `@JsonProperty("msg_show")` 强制 snake_case；`data` 必含 `bean`(默认 `{}`) 与 `list`(默认 `[]`)。
- 备选：手动包/AOP——侵入性强、易漏，弃。

**D2 全局异常映射 HTTP 状态码 = 业务 code（对齐 DRF）。**
- `ServiceHandleException`（带中文 `msg_show` + `status_code`）透传；参数校验类 → 400；Region 异常 → 优先 httpStatus；兜底 `Exception` → 500，body 带 `data.bean.trace_id`。
- 理由：rainbond-ui 的 `request.js` 按 HTTP 状态进 axios catch + 全局 toast，状态码必须与业务语义对齐。

**D3 JWT 用 jjwt 复刻 djangorestframework-jwt 1.11.0，HS256，密钥同源。**
- 接受 `GRJWT`(主)/`jwt`(兼容) 前缀，大小写不敏感；claims 直用 Django 风格 `user_id/username/nick_name/email/exp`，**不做名字转换**。**实测 `JWT_ALLOW_REFRESH=False`，token 不含 `orig_iat`**；密钥来自环境变量 `SECRET_KEY`（drf-jwt 的 `JWT_SECRET_KEY=SECRET_KEY`）。
- `JWT_SECRET_KEY` 非 local profile 启动时为空则**拒绝启动**（防止与旧端不互认）。
- token 中 `user_id` 必须真实存在于 `user_info` 表，否则 401 `user not found`。
- 备选：OAuth2 Resource Server——claims 形态与 drf-jwt 不一致，弃。

**D4 Redis 会话黑名单（首版必需，非纯无状态）。**
- 每请求验签通过后查 Redis 黑名单，命中即 401；登出/强制下线把 token（或 `user_id`+`orig_iat`）写入黑名单，TTL 对齐 token 剩余 `exp`。
- 对应旧版可选的 `JwtManager`；用户已明确首版即要，故 Redis 是 P0 运行依赖。

**D5 多租户上下文用 `@RequestScope` Bean + Interceptor。**
- `JwtAuthenticationFilter` 验签后真实加载 user 写入 `RequestContext`；`TenantContextInterceptor` 从路径变量 `{team_name}`/`{region_name}` 注入 `teamName`/`regionName`。
- P0 只做注入机制；具体团队/权限校验随 account/team 域迁移再接。

**D6 JPA 反向映射既有 schema，`ddl-auto=validate`，不依赖 DB 外键。**
- console 库 Django `db_constraint=False`，**无 DB 外键** → JPA 关系用 `@JoinColumn(foreignKey=@ForeignKey(NO_CONSTRAINT))` 或直接存外键 ID + 手动关联，**不级联**。
- 主键混用自增 `ID` 与业务 char(32) UUID → entity 明确区分 DB 主键与业务标识。
- 不加 `@Version` 等 Django 不认识的列。P0 只映射必需实体（`UserInfo` / `RegionConfig` 等）。

**D7 RegionClient 基于 HttpClient5，本轮仅骨架。**
- 地址/凭证来自 `region_info` 表（`url/wsurl/httpdomain/tcpdomain` + token / 双向 TLS）。
- 认证优先级：企业级 Token → 区域级 token/env → 双向 TLS；`REGION_SSL_VERIFY` 默认 false。
- 重试默认 2 次；超时梯度 2s→10/15s→20s→300s；按 URL+SSL 缓存连接池。
- 单 region；预留多 region 扩展点；具体 `/v2/tenants/...` 域方法不在本轮。

**D8 URL 不使用 `context-path`。** 根域下还有 `/openapi`、`/app-server`、`/api` 等，每个 controller 显式声明完整 `/console/...`，trailing slash 在 controller 显式兼容。

## Risks / Trade-offs

- **契约行为对齐偏差** → 以 rainbond-ui 实调 + 7070 对照为验收；P0 先把登录/healthz/信封/错误码这几条主路径对齐，建契约测试。
- **`validate` 下 entity 与既有列不精确对齐会启动失败** → 逐列核对命名策略/类型/可空性；P0 实体数量少，可严格人工核对。
- **Django ORM 隐式行为**（软删除/`auto_now`/JSON 字段）→ P0 涉及实体少（user_info 为主），逐表核对，必要时保留 Django 行为。
- **Redis 依赖引入运维成本** → 用户已明确接受；P0 即把会话黑名单纳入，避免后期返工。
- **JWT 密钥配置错误导致与旧端不互认** → 启动时强校验非空（非 local），fail-fast。

## Migration Plan

- 部署：起 kuship-console(8000) + Redis；连既有 `console` 库（`validate`）；`JWT_SECRET_KEY` 与原 rainbond-console 同源。
- 切换：rainbond-ui 代理 `proxyTarget` 7070→8000；旧 rainbond-console 停掉（完全替换，无并发双写）。
- 回滚：proxy 指回 7070、重启旧 console 即可；kuship-console 只读/写同库且 P0 仅登录写极少数据，回滚安全。

## Open Questions

- 暂无阻塞性未决项；6 项关键决策已锁定（见 `docs/kuship-console-架构设计.md` §8 决策记录）。
