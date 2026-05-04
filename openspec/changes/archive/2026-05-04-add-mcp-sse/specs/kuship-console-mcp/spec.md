## ADDED Requirements

### Requirement: SSE 长连接端点

kuship-console SHALL 暴露 `GET /console/mcp/query/sse` 长连接端点；返回 `Content-Type: text/event-stream` 与 `Cache-Control: no-cache`；建立成功后 SHALL 立即推送一个 `event: endpoint` 事件，data 为绝对 URL `<scheme>://<host>/console/mcp/query/message?session_id=<sid>`；session_id SHALL 是 32-char 由 `SecureRandom` 生成的 base32 字符串；连接保持期间 SHALL 周期性（默认 25s）发送 `event: ping` 注释保活；连接断开（client 关 / 超时 / Server 重启）时 SHALL 调用 `McpSseSessionManager.remove(sessionId)` 清理 session。

#### Scenario: 建立连接收到 endpoint 事件
- **WHEN** 客户端发 `GET /console/mcp/query/sse` 携带 `Authorization: Bearer <PAT>`
- **THEN** 响应 SHALL 200 + `Content-Type: text/event-stream`
- **AND** 第一个事件 SHALL 是 `event: endpoint\ndata: <abs-url>?session_id=<32-char>\n\n`
- **AND** session 被注册进 `McpSseSessionManager`（cache.size +1）

#### Scenario: 连接关闭清理 session
- **WHEN** 客户端关闭连接（`SseEmitter.onCompletion` 触发）
- **THEN** Session SHALL 从 cache 移除
- **AND** 该 session_id 不再可被 `POST /message` 命中

### Requirement: 消息端点 SSE 推送

kuship-console SHALL 暴露 `POST /console/mcp/query/message?session_id=<sid>` 端点；body 为 JSON-RPC 2.0 请求；端点 SHALL 解析并交给 `McpProtocolHandler.handle`；响应 NOT 直接由 HTTP 返回，而 SHALL 推入对应 session 的 SSE 通道作为 `event: message` 事件；HTTP 响应仅返 202 Accepted（无 body）；session 不存在 SHALL 返 404 + `{detail: "session not found", code: 404}`。

#### Scenario: 合法消息 → SSE 推送响应
- **WHEN** 客户端先建立 SSE 拿到 session_id，再发 `POST /message?session_id=<sid>` body `{"jsonrpc":"2.0","id":1,"method":"ping"}`
- **THEN** HTTP 响应 SHALL 是 202 Accepted（无 body）
- **AND** SSE 通道 SHALL 收到 `event: message\ndata: {"jsonrpc":"2.0","id":1,"result":{}}\n\n`

#### Scenario: session_id 不存在
- **WHEN** 客户端发 `POST /message?session_id=ghost`
- **THEN** HTTP 响应 SHALL 是 404 + `{detail:"session not found",code:404}`

### Requirement: HTTP RPC 端点支持 SSE Accept 升级

kuship-console SHALL 升级既有 `POST /console/mcp/query` 端点：根据请求头 `Accept` 决定响应模式：

| Accept | 响应 |
|---|---|
| `application/json`（或缺省） | 普通 JSON `JsonRpcResponse` |
| `text/event-stream` | 单事件 SSE 流 `event: message\ndata: <rpc-response>\n\n` 后立即关闭 |

两种模式下 RPC 处理逻辑（method 路由 / tool dispatch）一致。

#### Scenario: JSON 模式
- **WHEN** 客户端 `POST /console/mcp/query` body `{"jsonrpc":"2.0","id":1,"method":"ping"}` Accept `application/json`
- **THEN** 响应 SHALL 是 200 + JSON `{"jsonrpc":"2.0","id":1,"result":{}}`

#### Scenario: SSE 模式
- **WHEN** 同请求 + Accept `text/event-stream`
- **THEN** 响应 SHALL 是 200 + `Content-Type: text/event-stream`
- **AND** body SHALL 是 `event: message\ndata: {"jsonrpc":"2.0","id":1,"result":{}}\n\n` 后立即关闭

### Requirement: JSON-RPC 协议层 5 method

`McpProtocolHandler` SHALL 路由以下 5 个核心方法：

| method | params | result | 说明 |
|---|---|---|---|
| `initialize` | `{protocolVersion, capabilities, clientInfo}` | `{protocolVersion, capabilities: {tools:{}}, serverInfo}` | MCP 协议握手 |
| `notifications/initialized` | `{}` | （无，notification 无响应） | client 完成初始化通知 |
| `tools/list` | `{cursor?: string}` | `{tools: [{name, description, inputSchema}], nextCursor?}` | 列工具 |
| `tools/call` | `{name, arguments}` | `{content: [{type, text|json}]}` | 调工具 |
| `ping` | `{}` | `{}` | 心跳 |

未识别 method SHALL 返 JSON-RPC error code -32601 "Method not found"；params 反序列化失败 SHALL 返 -32602 "Invalid params"；tool 内部异常 SHALL 返 -32603 "Internal error" 含 message。

#### Scenario: initialize 返协议版本
- **WHEN** 客户端调 `initialize` params `{"protocolVersion":"2024-11-05","clientInfo":{"name":"claude-desktop","version":"0.1"}}`
- **THEN** 响应 result SHALL 含 `protocolVersion="2024-11-05"`、`capabilities.tools={}`、`serverInfo.name="kuship-console"`

#### Scenario: 未知 method
- **WHEN** 客户端调 `method="unknown/method"`
- **THEN** 响应 SHALL 是 `{jsonrpc:"2.0", id:<原id>, error:{code:-32601, message:"Method not found"}}`

#### Scenario: tools/list 返 8 个 MVP tool
- **WHEN** 客户端调 `tools/list`
- **THEN** result.tools SHALL 是长度 8 的数组，每项含 `name`、`description`、`inputSchema`（JSON Schema 形式）
- **AND** name 集合 SHALL 包含 `get_current_user, list_regions, list_teams, list_apps, list_components, get_component_detail, get_component_pods, get_component_logs`

### Requirement: Tool registry 抽象

`cn.kuship.console.modules.misc.mcp.tool.McpTool` interface SHALL 定义 4 个方法：

```java
interface McpTool {
    String name();                              // unique kebab/snake_case
    String description();
    Map<String, Object> inputSchema();          // JSON Schema Draft 7 root object
    JsonNode call(JsonNode arguments, RequestContext ctx) throws McpToolException;
}
```

`McpToolRegistry` (@Component) SHALL 注入 `List<McpTool>` 自动收集，按 `name()` 唯一索引；`tools/call` 命中后 SHALL 调用对应 tool 的 `call`；找不到 SHALL 抛 `McpToolException(MethodNotFound, "tool '<name>' not found")`。

#### Scenario: 添加新 tool 无需改协议层
- **WHEN** 开发者新建 `MyNewTool implements McpTool` 加 `@Component`
- **AND** Spring 启动 + 客户端调 `tools/list`
- **THEN** result.tools 数组长度 SHALL +1
- **AND** `tools/call` name=my_new_tool SHALL 命中并调用 `MyNewTool.call`

### Requirement: 8 个 MVP Tool

kuship-console SHALL 提供以下 8 个 `McpTool` 实现（每个对应一个 @Component）：

| name | input schema | 输出（result.content） |
|---|---|---|
| `get_current_user` | `{}` | `{user_id, nick_name, email, enterprise_id}` |
| `list_regions` | `{}` | `[{region_name, region_alias, region_type, status}]`（user 所属 enterprise 的 regions） |
| `list_teams` | `{}` | `[{team_id, team_name, tenant_alias, namespace}]` |
| `list_apps` | `{team_name, region_name}` | `[{app_id, group_name, governance_mode}]` |
| `list_components` | `{app_id}` | `[{service_id, service_alias, service_cname, deploy_version}]` |
| `get_component_detail` | `{service_id}` | `{tenant_service 全字段}` |
| `get_component_pods` | `{service_id}` | region API 透传 |
| `get_component_logs` | `{service_id, lines? = 100}` | region API 透传 |

#### Scenario: get_current_user 返当前用户
- **WHEN** RequestContext.userId=42 + nick_name="alice"
- **AND** 客户端调 `tools/call` name=get_current_user args={}
- **THEN** result.content SHALL 含 `user_id=42, nick_name="alice"`

#### Scenario: list_apps 校验 args
- **WHEN** 客户端调 `tools/call` name=list_apps args={}（缺 team_name）
- **THEN** 响应 SHALL 是 JSON-RPC error -32602 "Invalid params: missing field 'team_name'"

### Requirement: SSE 鉴权双通道

`/console/mcp/query/sse` 端点 SHALL 接受两种 PAT 鉴权方式：

1. `Authorization: Bearer <PAT>` header（推荐）
2. `?access_token=<PAT>` query 参数（浏览器原生 EventSource 兼容）

`/console/mcp/query/message` + `/console/mcp/query` 端点 SHALL 仅接受 header 鉴权（POST 客户端可设 header）。

任一通道 PAT 必须存在于 `user_access_key` 表（`access_key` 列）且关联 user_info 行存在；缺失 / 错误 PAT SHALL 返 401 + `{detail:"unauthorized",code:401}`。

#### Scenario: header 鉴权 SSE
- **WHEN** 客户端发 `GET /sse` `Authorization: Bearer valid-pat`
- **THEN** 响应 SHALL 200 + endpoint 事件
- **AND** RequestContext.userId 注入

#### Scenario: query 鉴权 SSE
- **WHEN** 客户端发 `GET /sse?access_token=valid-pat`
- **THEN** 响应 SHALL 200 + endpoint 事件

#### Scenario: PAT 错误 401
- **WHEN** 客户端发 `GET /sse` `Authorization: Bearer wrong-pat`
- **THEN** 响应 SHALL 401

### Requirement: Session 管理

`McpSseSessionManager` SHALL 用 Caffeine cache 维护 session 状态：

- 默认 maxSize = 1024（可由 `kuship.mcp.max-sessions` 覆盖）
- 默认 expireAfterAccess = 30 分钟（可由 `kuship.mcp.session-ttl-minutes` 覆盖）
- 超过 maxSize 时 LRU evict（被 evict 的 session 应触发 `SseEmitter.complete()` 关闭客户端连接）
- expire 触发同样关闭 SseEmitter

#### Scenario: cache 满触发 evict
- **WHEN** 创建 1025 个 session，maxSize=1024
- **THEN** 最早访问的 session SHALL 被 evict
- **AND** 对应 SseEmitter 调用 `complete()`
- **AND** 客户端 EventSource 收到 connection close

### Requirement: GraalVM Native 兼容

`KuShipConsoleRuntimeHints` SHALL 注册以下类的 `MemberCategory.values()` 反射 hint：

- `JsonRpcRequest` / `JsonRpcResponse` / `JsonRpcError`
- 8 个 MVP tool 类
- `McpSseSession`（含其 record 字段）

`SseEmitter` 不需手动注册（Spring AOT 默认覆盖）。

#### Scenario: native 模式 initialize + tools/list
- **WHEN** native binary 启动 + 客户端 SSE → initialize → tools/list
- **THEN** SHALL 不抛 ClassNotFoundException 或 InaccessibleObjectException
- **AND** tools/list 返 8 个 tool

### Requirement: 文档与运维指引

`kuship-console/CLAUDE.md` SHALL 新增 "MCP SSE（add-mcp-sse）" 段落，至少含：

- 协议握手图（client GET /sse → server endpoint event → client POST /message → server SSE message event）
- 5 个核心 method 表
- 8 个 MVP tool 表
- SSE 鉴权矩阵（header / query 两通道 + nginx access_token 剥离配置示例）
- 多副本部署 nginx ip_hash 配置示例 + 限制说明
- 后续 hardening 路径（add-mcp-tools-batch1 / add-mcp-tools-deploy / add-distributed-mcp-sessions / add-mcp-resources / add-mcp-async-tools / add-mcp-schema-validation）

#### Scenario: 文档段落齐全
- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档 SHALL 包含 "MCP SSE（add-mcp-sse）" 段落
- **AND** 段落 SHALL 至少含：协议握手图 + 5 method 表 + 8 tool 表 + 鉴权矩阵 + ip_hash 限制
