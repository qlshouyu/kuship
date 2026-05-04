## Context

MCP（Model Context Protocol，Anthropic 2024-11-05 spec）是 LLM 客户端 ↔ tool server 通信协议；transport 默认走 stdio，但 web 场景下用 SSE + HTTP POST 双工：

1. 客户端发 `GET /sse` 建立长连接，Server 立即推 `event: endpoint\ndata: <message_url>` 告诉客户端"后续 JSON-RPC 通过这个 URL POST"
2. 客户端发 `POST <message_url>` 携带 `{jsonrpc, id, method, params}`，Server 把响应通过 SSE 通道推回
3. SSE 通道周期发 `event: ping` 心跳保持连接
4. Session 超时 / 关闭 / 容量上限触发清理

rainbond Python 端 `mcp_query.py` 用 Django StreamingHttpResponse + threading.Queue 实现，每个 session 一个 ThreadLocal Queue。kuship-console 用 Spring `SseEmitter` + `BlockingQueue` 等价实现，session 状态在 Caffeine cache。

工具方法 293 个迁移成本高，本 change 不一次性翻译；引入 `McpTool` interface + `McpToolRegistry` 自动收集，新增工具仅需新增一个 @Component 实现类，零修改协议层。

## Goals / Non-Goals

**Goals:**
1. SSE + HTTP RPC 三端点 1:1 对齐 rainbond Python 行为（路径 / 鉴权 / 协议握手）
2. JSON-RPC 协议层（5 method）支持 batch + 错误码（-32600 / -32601 / -32602 / -32603）
3. Tool registry 自动收集；新工具加 1 个 @Component 即用，不动协议层
4. 8 个 MVP tool 让 LLM 客户端连上后可立即查询 region/team/app/component/pod/log
5. SSE 鉴权支持 `Authorization` header（推荐）+ `?access_token=` query（兼容浏览器 EventSource）
6. GraalVM native binary 下 SSE 流式响应可用（验证 `SseEmitter` reflection hint）

**Non-Goals:**
- **285 个剩余 tool 实现** —— 留给后续 `add-mcp-tools-batch1`（apps lifecycle / pods detail / events）/ `batch2`（deploy / build / scaling）/ `batch3`（share / market / autoscaler）等 batch hardening
- **分布式 session 存储**（多副本 console 间 session 路由）—— 留给 `add-distributed-mcp-sessions`（Redis Streams）；当前在多副本部署下 SSE 会话与具体实例绑定，需 nginx ip_hash
- **MCP capabilities negotiation 高级特性**（resources / prompts / sampling 三大类）—— 当前仅声明 `tools` capability；resources 留给 `add-mcp-resources`
- **Server→client notifications 主动推**（`notifications/progress` / `notifications/cancelled`）—— 仅响应客户端 RPC，不主动推
- **工具 schema 校验**（JSONSchema Draft 7）—— 当前 tool 自行校验 args；统一 schema 校验留给 `add-mcp-schema-validation`

## Decisions

### Decision 1: Spring `SseEmitter` 而非 WebFlux `Sinks.Many`

kuship-console 是 Spring MVC（servlet stack），不是 WebFlux。`SseEmitter` 是 MVC 模式下原生流式响应支持，每个 emitter 绑定到一个 servlet 异步线程，完成时调 `complete()`。

**Why:**
- 无需引入 reactor-netty / WebFlux 依赖
- 与 controller 测试基础设施（MockMvc）兼容
- GraalVM native 已被 Spring AOT 默认覆盖（`SseEmitter` 是 Spring MVC core 类）

**Alternative considered:** 切换到 WebFlux 整个 controller 模块。**否决**：本 change 仅 SSE 一处需要流式，全栈切换成本爆炸。

### Decision 2: Session 状态用 Caffeine in-memory

每个 SSE session 含 `session_id`（UUID）/ `userId` / `BlockingQueue<String>` / `SseEmitter` / `lastHeartbeat`。Caffeine cache：`maxSize=1024 + expireAfterAccess=30min`。

**Why:**
- 与 add-aliyun-sms 的 `SmsRateLimiter` / harden-webhook-hmac 的 `WebhookDeliveryDeduper` 一致的内存 cache 模式
- expire 触发 listener 关闭 SseEmitter + 释放 Queue
- 多副本部署需 nginx ip_hash 把同一客户端打到同一副本

**Alternative considered:** Redis-backed sessions（Spring Session）。**否决**：引入 Redis 依赖大；分布式 session 路由本身需要 sticky 否则 SSE 推送的副本可能不是 POST 的副本。留作独立 hardening。

### Decision 3: Tool registry 用 Spring `@Component` 自动收集

`McpTool` interface + 8 个 `@Component` 实现类。`McpToolRegistry` 注入 `List<McpTool>`，按 `name()` 分桶。

**Why:**
- 添加新 tool 仅需新建一个 `@Component implements McpTool`
- Spring 启动时自动发现，无需注册中心 / 配置文件
- 测试时通过 `@MockitoBean` 替换或排除单个 tool 容易

**Alternative considered:** Annotation-driven `@McpTool("tool_name")` + classpath scan。**否决**：与 Spring 既有 `@Component` 重复造轮子；后期可考虑加 `@McpTool` 注解作为补充而非替代。

### Decision 4: SSE 鉴权 PAT 双通道（header + query）

- 主通道：`Authorization: Bearer <PAT>`（推荐）
- 兼容通道：`GET /sse?access_token=<PAT>`（浏览器原生 EventSource API 不支持自定义 header）

`McpAuthFilter`（独立于 OpenApiAuthFilter）匹配 `/console/mcp/**` 时优先 header，缺失时取 query。鉴权后注入 `RequestContext.userId`。query 模式 query string 不会被记入 nginx access log（容易泄露），需 nginx 配置剥离日志中的 access_token 参数。

**Why:**
- 浏览器 SSE 客户端是 LLM 集成场景的少数派，但需要支持
- Header 优先保证 native 客户端（Claude Desktop curl）的标准用法
- 文档中明确告诉用户：query 模式仅 dev / 内网调试用，prod 用 header

**Alternative considered:** 仅 header。**否决**：堵死浏览器场景。

### Decision 5: HTTP RPC 端点 `Accept: text/event-stream` 升级

rainbond Python `MCPQueryHTTPView.post` 检查 `Accept` 头：
- `application/json` → 返回普通 JSON
- `text/event-stream` → 返回单事件 SSE 流（`event: message\ndata: <rpc-response>\n\n`）然后关闭

kuship-console 同样行为，由 controller 内部 if-else 分支决定返回 `ResponseEntity<JsonRpcResponse>` 还是 `SseEmitter`。

**Why:** 保持 rainbond 兼容；某些客户端只接受 event-stream，某些只接受 JSON。

### Decision 6: 工具 args 校验在 tool 内部，不在协议层

每个 `McpTool.call(JsonNode args, RequestContext ctx)` 自行 cast / 校验入参，校验失败抛 `McpToolException(InvalidParams, "missing field x")`，协议层映射为 JSON-RPC error -32602。

**Why:** 8 个 MVP tool 的 schema 简单（多数 1-3 个字段），统一 JSONSchema 校验过度工程；后续 `add-mcp-schema-validation` change 引入 `@McpInputSchema` 注解 + json-schema-validator 依赖时再统一收口。

### Decision 7: 协议握手在 `protocol/McpProtocolHandler` 单点

5 个 core method（`initialize` / `notifications/initialized` / `tools/list` / `tools/call` / `ping`）由 `McpProtocolHandler.handle(JsonRpcRequest, McpSseSession)` 路由。`tools/call` 进一步委托给 `McpToolRegistry.lookup(name).call(args, ctx)`。

**Why:** 协议层与工具层解耦；增加 tool 不动 protocol；改协议（如升级到 2025 版）不动 tool。

### Decision 8: GraalVM native — record + tool class 都需 hint

JSON-RPC records（`JsonRpcRequest` / `JsonRpcResponse` / `JsonRpcError`）+ 8 个 tool class + `McpProtocolHandler` 的 5 method `params` 反序列化目标都需反射 hint。在 `KuShipConsoleRuntimeHints` 注册 11 个 fqcn。

**SseEmitter** 是 Spring 标准类，AOT 默认覆盖。

## Risks / Trade-offs

- **[多副本部署 SSE 路由问题]** → Decision 2 接受；CLAUDE.md 写明需 nginx ip_hash + 提供 `add-distributed-mcp-sessions` 路径
- **[query string PAT 泄露日志]** → Decision 4 接受；CLAUDE.md 提供 nginx access log 剥离 `access_token` 参数的配置示例
- **[8 个 MVP tool 不够用]** → Decision 1 接受；客户端调未实现 tool 时返 `tool not found` 错误（明确而非崩溃）；后续 batch hardening 加；运维监控 `grep "tool not found"` 统计高频被请求的未迁移 tool 排序优先级
- **[`SseEmitter` 长连接占用 servlet 线程]** → Tomcat 默认 200 线程；与 maxSessions=1024 不匹配。需调 `server.tomcat.threads.max=2000` 或切 NIO Connector + async；本 change 加 `application.yaml` 注释 + 配置默认 max-sessions=200 与默认线程池匹配
- **[MCP 协议版本演进]** → 当前实现 2024-11-05；`initialize` 返 `protocolVersion` 由 `kuship.mcp.protocol-version` 配置，未来升级仅需改配置 + 加新 method handler
- **[tool 内部直接调 region API + DB 可能阻塞 SSE 线程]** → 单 RPC 处理时间预算 5s（rainbond 同等约束）；超时 tool 应 throw timeout exception；本 change 不引入异步 tool（留给 `add-mcp-async-tools`）
- **[OpenApiAuthFilter 与 McpAuthFilter 顺序]** → McpAuthFilter 在 `OncePerRequestFilter` 中匹配仅 `/console/mcp/**`，加在 OpenApiAuthFilter 之后；不冲突

## Migration Plan

1. **Phase A — 协议层 + Session 管理**：JsonRpc records + McpProtocolHandler + McpSseSession + McpSseSessionManager + 单元测试。Verify: `mvn test -Dtest=McpProtocolHandlerTest,McpSseSessionManagerTest,JsonRpcRequestTest` GREEN
2. **Phase B — Tool registry + 8 MVP tools**：McpTool interface + Registry + 8 tool 实现。Verify: 单元测试 GREEN
3. **Phase C — Transport + Auth Filter**：McpSseController + McpHttpController + McpAuthFilter + SecurityConfig 改动。Verify: 集成测试 GREEN
4. **Phase D — Native + 文档**：RuntimeHints + CLAUDE.md 段落。Verify: `bash scripts/native-test.sh --quick` GREEN

**回滚路径**：本 change 仅新增端点。如 SSE 实现 bug 影响生产，nginx 把 `/console/mcp/query/sse` 反代回 rainbond-console，HTTP RPC 同理。

## Open Questions

1. **`tools/list` 是否要分页？** rainbond Python 一次返全部 293 个 tool（响应体~80KB）。kuship MVP 只 8 个所以无所谓，但加 batch1 后会到 50+；建议本 change 接口预留 `params: {cursor?: string}` 但 MVP 阶段不实现分页（client 全量收）。
2. **`tools/call` 超时？** rainbond 端没显式 timeout，依赖 nginx 60s。建议本 change tool 内部用 `kuship.region.timeout-seconds=5`（既有配置）的同等约束；超时抛 `McpToolException(InternalError, "tool timeout")`。
3. **session_id 安全性** —— rainbond 用 UUID v4。kuship 用 `SecureRandom` 生成 32-char base32 ID（攻击者枚举成本 ~2^160）。
