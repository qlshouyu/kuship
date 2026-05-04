## 1. 协议层 records + Handler

- [x] 1.1 新建 `cn.kuship.console.modules.misc.mcp.{protocol,tool,transport}` 子包结构
- [x] 1.2 新增 `JsonRpcRequest` record（jsonrpc, id, method, params: JsonNode）
- [x] 1.3 新增 `JsonRpcResponse` record（jsonrpc, id, result?, error?）+ static factory `ok` / `error`
- [x] 1.4 新增 `JsonRpcError` record（code, message, data?）+ 错误码常量 (-32600/-32601/-32602/-32603)
- [x] 1.5 新增 `McpProtocolHandler` (@Component) 注入 ToolRegistry + ObjectMapper
- [x] 1.6 实现 `handle(JsonRpcRequest, RequestContext)` 路由 5 method（initialize / notifications/initialized / tools/list / tools/call / ping）
- [x] 1.7 单元测试 `McpProtocolHandlerTest`：初始化握手 / 未知 method / params 错 / tool 错四类断言
- [x] 1.8 单元测试 `JsonRpcRequestTest`：合法 / 非法（缺 jsonrpc） / batch（数组） 三 case

## 2. Tool registry + 8 MVP tools

- [x] 2.1 新增 `McpTool` interface (name / description / inputSchema / call)
- [x] 2.2 新增 `McpToolException` (code, message)
- [x] 2.3 新增 `McpToolRegistry` (@Component) 注入 List<McpTool> + name → tool index
- [x] 2.4 实现 `GetCurrentUserTool` 用 `RequestContext.userId` + `UserInfoRepository`
- [x] 2.5 实现 `ListRegionsTool` 用 enterprise_id + `RegionInfoRepository`
- [x] 2.6 实现 `ListTeamsTool` 用 user 所属 teams（需 PermRelTenant 关联）
- [x] 2.7 实现 `ListAppsTool` 用 team_name + region 过滤 ServiceGroup
- [x] 2.8 实现 `ListComponentsTool` 用 app_id 过滤 TenantService（join service_group_relation）
- [x] 2.9 实现 `GetComponentDetailTool` 用 service_id 查 TenantService 全字段
- [x] 2.10 实现 `GetComponentPodsTool` 透传 region API（ServiceStatusOperations.getServicePods）
- [x] 2.11 实现 `GetComponentLogsTool` 透传 region API（ServiceLogOperations.getServiceLogs）
- [x] 2.12 单元测试 `McpToolRegistryTest`：8 个 tool 全部被发现 + name 索引正确
- [x] 2.13 单元测试 `GetCurrentUserToolTest` / `ListAppsToolTest`（mock repo + 校验返回字段）

## 3. SSE Session 管理

- [x] 3.1 新增 `McpSseSession` 类（sessionId / userId / nickName / SseEmitter / lastAccess / pendingMessages: BlockingQueue）
- [x] 3.2 新增 `McpSseSessionManager` (@Component) 内部 Caffeine cache + RemovalListener 关闭 emitter
- [x] 3.3 提供 `register(userId, nickName) -> McpSseSession`、`get(sessionId)`、`remove(sessionId)`、`size()`
- [x] 3.4 sessionId 用 SecureRandom 32-char base32
- [x] 3.5 RemovalListener cause=EXPIRED|SIZE → emitter.complete()
- [x] 3.6 单元测试 `McpSseSessionManagerTest`：register + get + remove + cache 超容量 LRU evict

## 4. McpAuthFilter + SecurityConfig

- [x] 4.1 新增 `McpAuthFilter extends OncePerRequestFilter`，path matcher `/console/mcp/**`
- [x] 4.2 优先 `Authorization: Bearer <PAT>`；缺失时 `request.getParameter("access_token")`
- [x] 4.3 PAT 校验通过 `UserAccessKeyRepository` + `UserInfoRepository`，PAT 不存在 / user_info 不存在 / inactive → 401 写 `{detail,code}`
- [x] 4.4 鉴权通过后注入 `RequestContext.userId / nickName / email / enterpriseId / sysAdmin`
- [x] 4.5 在 `SecurityConfig` 把 `/console/mcp/query/**` 加 permitAll（filter 内部鉴权）
- [x] 4.6 在 Spring Security FilterChain 中加 McpAuthFilter（在 OpenApiAuthFilter 之后 / JwtAuthenticationFilter 之前）
- [x] 4.7 单元测试 `McpAuthFilterTest`：header / query / 缺失 / wrong-pat / PAT-but-user-deleted 5 个 case

## 5. Transport — SSE Controller

- [x] 5.1 新增 `McpSseController` 接管 `GET /console/mcp/query/sse` 与 `POST /console/mcp/query/message`
- [x] 5.2 GET /sse 调 SessionManager.register → 立即推 `event: endpoint\ndata: <abs-url>?session_id=<sid>\n\n`
- [x] 5.3 在 emitter 上启动单线程 heartbeat（25s 周期 `event: ping`）
- [x] 5.4 onCompletion / onTimeout / onError 回调 SessionManager.remove
- [x] 5.5 POST /message 解析 body → 校验 session_id → handler.handle → emitter.send `event: message\ndata: <rpc>`
- [x] 5.6 POST /message 响应 HTTP 202 Accepted（无 body）
- [x] 5.7 session_id 不存在 SHALL 返 404 + `{detail,code}`

## 6. Transport — HTTP RPC 升级

- [x] 6.1 改造 `MCPQueryController` POST /console/mcp/query：检查 `Accept` 头
- [x] 6.2 Accept=`application/json` 走 JSON 模式（既有逻辑替换为 handler.handle 真实分发）
- [x] 6.3 Accept=`text/event-stream` 走 SSE 单事件模式（新建临时 emitter，写 1 个 message 事件后立即 complete）
- [x] 6.4 删除原 `MCPQueryController` 的 stub 路由 `/console/mcp/query/http`，迁移到根路径

## 7. 配置 + Native

- [x] 7.1 `application.yaml` 加 `kuship.mcp.{protocol-version=2024-11-05, max-sessions=1024, session-ttl-minutes=30, heartbeat-seconds=25, server-name=kuship-console}`
- [x] 7.2 `KuShipConsoleRuntimeHints` 注册 11 个 fqcn（3 records + 8 tool class）
- [x] 7.3 `bash scripts/native-test.sh --quick` → 4/4 pass

## 8. 集成测试

- [x] 8.1 新增 `McpAuthIntegrationTest`：header / query 两通道 + 4 失败路径
- [x] 8.2 新增 `McpSseIntegrationTest`：建立 SSE → POST initialize → SSE 收到 endpoint 事件 + initialize result
- [x] 8.3 case: tools/list over SSE 返 8 个 tool name
- [x] 8.4 case: tools/call get_current_user 返当前 user 字段
- [x] 8.5 case: tools/call unknown_tool 返 -32601 错误
- [x] 8.6 新增 `McpHttpIntegrationTest`：POST /mcp/query JSON 模式 ping → 200 + JSON
- [x] 8.7 case: POST /mcp/query SSE 模式 ping → 200 + Content-Type text/event-stream + 单事件
- [x] 8.8 case: session 不存在 → 404
- [x] 8.9 case: list_apps 缺 team_name → -32602 invalid params

## 9. 文档

- [x] 9.1 `kuship-console/CLAUDE.md` 新增 "MCP SSE（add-mcp-sse）" 段落
- [x] 9.2 含协议握手图（ASCII / sequence-style）
- [x] 9.3 含 5 method 表 + 8 tool 表
- [x] 9.4 含 SSE 鉴权矩阵（header / query 双通道 + nginx access_token 日志剥离）
- [x] 9.5 含多副本 nginx ip_hash 配置示例 + 限制说明
- [x] 9.6 含未来 hardening 路径（add-mcp-tools-batch* / add-distributed-mcp-sessions / add-mcp-resources / add-mcp-async-tools / add-mcp-schema-validation）

## 10. 验证收尾

- [x] 10.1 `mvn test` 全 GREEN（既有 157 + 本 change ~15-20 case ≈ 172+）
- [x] 10.2 `bash scripts/native-test.sh --quick` 全 GREEN
- [x] 10.3 `openspec validate add-mcp-sse --strict` 通过
- [x] 10.4 手工 `curl -N -H "Authorization: Bearer <PAT>" http://localhost:8080/console/mcp/query/sse` 收到 endpoint 事件，配合 `curl -X POST` 验 ping → message 事件回推
