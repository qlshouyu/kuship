package cn.kuship.console.modules.misc.mcp.transport;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.misc.mcp.protocol.JsonRpcRequest;
import cn.kuship.console.modules.misc.mcp.protocol.JsonRpcResponse;
import cn.kuship.console.modules.misc.mcp.protocol.McpProtocolHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE 长连接 + message 推送端点。鉴权由 {@link cn.kuship.console.modules.misc.mcp.auth.McpAuthFilter} 完成。
 *
 * <p>客户端流程：
 * <ol>
 *   <li>GET /console/mcp/query/sse → 收到 {@code event: endpoint\ndata: <message_url>?session_id=<sid>}</li>
 *   <li>POST &lt;message_url&gt; body=JSON-RPC → HTTP 202 Accepted；响应通过 SSE 通道作为 {@code event: message} 推回</li>
 * </ol>
 */
@RestController
@RequestMapping("/console/mcp/query")
public class McpSseController {

    private static final Logger log = LoggerFactory.getLogger(McpSseController.class);
    private static final long SSE_TIMEOUT_MS = 0L; // 0 = no timeout (kept alive by heartbeat)

    private final McpSseSessionManager sessionManager;
    private final McpProtocolHandler handler;
    private final RequestContext ctx;
    private final ObjectMapper json;
    private final long heartbeatSeconds;
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "mcp-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public McpSseController(McpSseSessionManager sessionManager,
                                McpProtocolHandler handler,
                                RequestContext ctx,
                                ObjectMapper json,
                                @Value("${kuship.mcp.heartbeat-seconds:25}") long heartbeatSeconds) {
        this.sessionManager = sessionManager;
        this.handler = handler;
        this.ctx = ctx;
        this.json = json;
        this.heartbeatSeconds = heartbeatSeconds;
    }

    @GetMapping(value = {"/sse", "/sse/"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        McpSseSession session = sessionManager.register(
                ctx.getUserId(), ctx.getUsername(), ctx.getEmail(),
                ctx.getEnterpriseId(), ctx.isSysAdmin(), emitter);
        String messageUrl = buildAbsoluteUrl(request, "/console/mcp/query/message")
                + "?session_id=" + session.sessionId();
        try {
            emitter.send(SseEmitter.event().name("endpoint").data(messageUrl, MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            log.warn("[MCP] failed to send endpoint event sid={}", session.sessionId(), e);
            emitter.completeWithError(e);
            sessionManager.remove(session.sessionId());
            return emitter;
        }
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
        Runnable cleanup = () -> {
            heartbeat.cancel(false);
            sessionManager.remove(session.sessionId());
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());
        return emitter;
    }

    @PostMapping(value = {"/message", "/message/"})
    public ResponseEntity<Map<String, Object>> message(@RequestParam("session_id") String sessionId,
                                                            @RequestBody JsonNode body) {
        McpSseSession session = sessionManager.get(sessionId).orElse(null);
        if (session == null) {
            return ResponseEntity.status(404).body(Map.of("detail", "session not found", "code", 404));
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "missing body", "code", 400));
        }
        JsonRpcRequest req;
        try {
            req = json.treeToValue(body, JsonRpcRequest.class);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "detail", "invalid jsonrpc body: " + e.getMessage(), "code", 400));
        }
        JsonRpcResponse resp = handler.handle(req, ctx);
        if (resp == null) {
            return ResponseEntity.accepted().body(Map.of());
        }
        try {
            session.emitter().send(SseEmitter.event().name("message").data(json.writeValueAsString(resp), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("[MCP] failed to push response sid={}", sessionId, e);
            sessionManager.remove(sessionId);
            return ResponseEntity.status(500).body(Map.of("detail", "sse push failed", "code", 500));
        }
        return ResponseEntity.accepted().body(Map.of());
    }

    private static String buildAbsoluteUrl(HttpServletRequest request, String path) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) scheme = request.getScheme();
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
                host = host + ":" + port;
            }
        }
        return scheme + "://" + host + path;
    }
}
