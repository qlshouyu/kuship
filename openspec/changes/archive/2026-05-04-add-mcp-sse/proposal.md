## Why

rainbond-console 暴露了 MCP（Model Context Protocol，2024-11-05）服务，让 LLM 客户端（Claude Desktop / Cursor / Cline 等）能通过 SSE 长连接 + JSON-RPC 调用 293 个工具方法（`mcp_query_service.py` 6577 LoC），覆盖应用 / 组件 / Pod / 日志 / 事件 / 部署等全套查询与运维操作。这是 rainbond 在 SaaS 场景下吸引开发者的关键差异化能力——开发者直接在 IDE 内通过自然语言操作集群。

kuship-console 当前 MCP 仅落地了**单一 stub**（`POST /console/mcp/query/http` 返回 echo），缺：
1. **SSE 传输通道**（`GET /console/mcp/query/sse` 长连接 + `POST /console/mcp/query/message?session_id=` 推送）—— 这是 MCP 协议规范要求的双向通道，没有它 LLM 客户端无法连接
2. **JSON-RPC 协议层**（`initialize` / `notifications/initialized` / `tools/list` / `tools/call` / `ping` 5 个核心方法）
3. **Tool registry 抽象**（让 290+ 工具能逐批迁移而不重写传输层）
4. **5-8 个 MVP 工具**（让 LLM 客户端连上后能立即用，验证整条链路通）

不做的话：kuship-ui 切流到 kuship-console 后，所有用 MCP 的开发者会立即丢失能力，且 LLM 客户端报"connection refused"，体感等于 SaaS 服务降级。

## What Changes

- 新增 `cn.kuship.console.modules.misc.mcp.sse` 子包：
  - `transport/McpSseSession` —— 单 session bean（session_id + user + queue + heartbeat）
  - `transport/McpSseSessionManager` —— Caffeine cache（maxSize=1024 / TTL=30min idle）+ session lifecycle
  - `transport/McpSseController` —— `GET /console/mcp/query/sse` 建立长连接（`text/event-stream`）+ `POST /console/mcp/query/message` 双工配对
  - `transport/McpHttpController` —— 升级既有 `POST /console/mcp/query` 支持 `Accept: text/event-stream` 的单 RPC SSE 流（rainbond 兼容行为）
- 新增 JSON-RPC 协议层：
  - `protocol/JsonRpcRequest` / `JsonRpcResponse` / `JsonRpcError` records（含 `jsonrpc:"2.0"` / `id` / `method` / `params`）
  - `protocol/McpProtocolHandler` —— 5 个核心 method 路由：`initialize`（返协议版本 + 服务能力 capabilities）、`notifications/initialized`、`tools/list`、`tools/call`、`ping`
- 新增 Tool registry：
  - `tool/McpTool` interface（`name() / description() / inputSchema() / call(args, RequestContext)`）
  - `tool/McpToolRegistry` —— @Component 自动收集所有 `McpTool` bean
  - 8 个 MVP tool 实现：
    - `GetCurrentUserTool` —— 返 RequestContext.userId + nick_name
    - `ListRegionsTool` —— 列 enterprise 下所有 region
    - `ListTeamsTool` —— 列 user 所属 teams
    - `ListAppsTool` —— 给定 team + region 列应用（service_group）
    - `ListComponentsTool` —— 给定 app_id 列组件
    - `GetComponentDetailTool` —— 给定 service_id 返组件详情
    - `GetComponentPodsTool` —— 给定 service_id 返 Pod 列表（透传 region API）
    - `GetComponentLogsTool` —— 给定 service_id 返最近 N 行日志（透传 region API）
- 鉴权：
  - SSE 端点 SHALL 接受 `Authorization: Bearer <PAT>` 或 `?access_token=<PAT>` query（SSE 客户端通常无法设置 header）
  - HTTP RPC 端点 SHALL 接受现有 JWT（GRJWT）/ Bearer PAT
- `application.yaml` 加 `kuship.mcp.{session-ttl-minutes, max-sessions, heartbeat-seconds, internal-token-prefix}` 配置
- 升级 `OpenApiAuthFilter`：放行 `/console/mcp/query/sse` 的 `?access_token=` query 鉴权（仅这条路径例外，避免 SSE 客户端无 header 能力）
- 升级 `SecurityConfig`：MCP 三端点放行（鉴权由 `McpAuthFilter` 内部处理）
- GraalVM native 兼容：`KuShipConsoleRuntimeHints` 注册 8 个 tool record + JsonRpc records
- 单元测试：`McpProtocolHandlerTest`（5 method 路由）+ `McpSseSessionManagerTest`（创建 / 过期 / capacity）+ `JsonRpcRequestTest`（解析 / 错误 / batch）
- 集成测试：`McpSseIntegrationTest`（`initialize` → `tools/list` → `tools/call` 三步握手 over SSE + HTTP）+ `McpAuthIntegrationTest`（PAT / JWT 双模 + 401 路径）
- `kuship-console/CLAUDE.md` 新增 "MCP SSE（add-mcp-sse）" 段落，覆盖：协议握手图、5 个核心 method 表、8 个 MVP tool 表、SSE 鉴权矩阵、未来 hardening 工具批次路线（add-mcp-tools-app-create / add-mcp-tools-deploy / add-mcp-tools-share / ...）

## Capabilities

### New Capabilities

- `kuship-console-mcp`: MCP 服务的传输通道（SSE + HTTP RPC）+ JSON-RPC 协议层 + Tool registry 抽象 + MVP 工具集，承接 LLM 客户端对 kuship 集群的查询与操作。

### Modified Capabilities

<!-- 既有 capabilities 不修改：misc 模块下的 MCPQueryController 是 stub，会被本 change 替换为完整实现，但 capability 边界不变 -->

## Impact

- **新增文件**：`modules/misc/mcp/{sse,protocol,tool}/*.java`（约 25 文件，预计 1500-1800 行 Java）
- **新增测试**：`modules/misc/mcp/{transport,protocol,tool,integration}/*Test.java`（约 8 个测试类）
- **修改文件**：`MCPQueryController.java`（拆分为 transport 子包）、`SecurityConfig.java`（permitAll 加 mcp/query/sse 与 mcp/query/message）、`OpenApiAuthFilter.java`（access_token query 鉴权例外）、`KuShipConsoleRuntimeHints.java`、`application.yaml`、`kuship-console/CLAUDE.md`
- **不修改**：rainbond-go core（MCP 协议在 console 端实现，go core 仅被工具调用）、`mcp_query_service.py`（rainbond Python 端依然作为 reference 留存）
- **影响范围**：LLM 客户端（Claude Desktop / Cursor / Cline）从今天起可连 kuship-console 查询应用 / 组件 / Pod；未实现的 285 个工具会返 `tools/call` 错误 `tool not found`，由后续 batch hardening 逐步补齐
- **回退路径**：本 change 仅新增端点 + 工具，不修改任何既有路径；如发现 bug 可在 nginx 短期把 `/console/mcp/query/*` 路由回 rainbond-console
