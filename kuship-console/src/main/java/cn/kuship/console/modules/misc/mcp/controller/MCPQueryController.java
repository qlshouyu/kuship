package cn.kuship.console.modules.misc.mcp.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.SkipResponseWrapper;
import cn.kuship.console.modules.misc.mcp.protocol.JsonRpcRequest;
import cn.kuship.console.modules.misc.mcp.protocol.JsonRpcResponse;
import cn.kuship.console.modules.misc.mcp.protocol.McpProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Synchronous JSON-RPC entry point for MCP. Companion to the SSE channel in
 * {@link cn.kuship.console.modules.misc.mcp.transport.McpSseController}.
 *
 * <p>Behavior depends on the {@code Accept} header:
 * <ul>
 *   <li>{@code application/json} (default) — return JSON {@link JsonRpcResponse}</li>
 *   <li>{@code text/event-stream} — return single-event SSE stream with the response, then close</li>
 * </ul>
 */
@RestController
@RequestMapping("/console/mcp/query")
public class MCPQueryController {

    private static final Logger log = LoggerFactory.getLogger(MCPQueryController.class);

    private final McpProtocolHandler handler;
    private final RequestContext ctx;
    private final ObjectMapper json;

    public MCPQueryController(McpProtocolHandler handler, RequestContext ctx, ObjectMapper json) {
        this.handler = handler;
        this.ctx = ctx;
        this.json = json;
    }

    @PostMapping(value = {"", "/"})
    @SkipResponseWrapper
    public Object http(@RequestBody JsonNode body,
                          @RequestHeader(value = "Accept", required = false) String accept) {
        JsonRpcRequest req;
        try {
            req = json.treeToValue(body, JsonRpcRequest.class);
        } catch (RuntimeException e) {
            JsonRpcResponse parseErr = JsonRpcResponse.error(null,
                    cn.kuship.console.modules.misc.mcp.protocol.JsonRpcError.PARSE_ERROR,
                    "Parse error: " + e.getMessage());
            return ResponseEntity.ok(parseErr);
        }
        JsonRpcResponse resp = handler.handle(req, ctx);
        boolean wantsSse = accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
        if (!wantsSse) {
            // notifications return null → respond with 202-style empty body
            return ResponseEntity.ok(resp == null ? JsonRpcResponse.ok(null, java.util.Map.of()) : resp);
        }
        SseEmitter emitter = new SseEmitter(0L);
        try {
            if (resp != null) {
                emitter.send(SseEmitter.event().name("message")
                        .data(json.writeValueAsString(resp), MediaType.APPLICATION_JSON));
            }
            emitter.complete();
        } catch (IOException e) {
            log.warn("[MCP] single-rpc SSE send failed", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
