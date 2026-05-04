package cn.kuship.console.modules.misc.mcp.protocol;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.misc.mcp.tool.McpTool;
import cn.kuship.console.modules.misc.mcp.tool.McpToolException;
import cn.kuship.console.modules.misc.mcp.tool.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes JSON-RPC method calls to MCP protocol handlers (initialize / tools/list / tools/call / ping).
 *
 * <p>Tool dispatch delegates to {@link McpToolRegistry}; new tools added as @Component
 * beans become available without touching this class.
 */
@Component
public class McpProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(McpProtocolHandler.class);

    private final McpToolRegistry tools;
    private final ObjectMapper json;
    private final String protocolVersion;
    private final String serverName;
    private final String serverVersion;

    public McpProtocolHandler(McpToolRegistry tools, ObjectMapper json,
                                  @Value("${kuship.mcp.protocol-version:2024-11-05}") String protocolVersion,
                                  @Value("${kuship.mcp.server-name:kuship-console}") String serverName,
                                  @Value("${kuship.mcp.server-version:0.1.0}") String serverVersion) {
        this.tools = tools;
        this.json = json;
        this.protocolVersion = protocolVersion;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    /**
     * @return JsonRpcResponse to send back, or {@code null} for notifications (no response).
     */
    public JsonRpcResponse handle(JsonRpcRequest req, RequestContext ctx) {
        if (req == null || req.method() == null) {
            return JsonRpcResponse.error(req == null ? null : req.id(),
                    JsonRpcError.INVALID_REQUEST, "Invalid Request");
        }
        try {
            return switch (req.method()) {
                case "initialize" -> JsonRpcResponse.ok(req.id(), buildInitializeResult());
                case "notifications/initialized" -> null;
                case "ping" -> JsonRpcResponse.ok(req.id(), Map.of());
                case "tools/list" -> JsonRpcResponse.ok(req.id(), buildToolsListResult());
                case "tools/call" -> JsonRpcResponse.ok(req.id(), buildToolsCallResult(req.params(), ctx));
                default -> JsonRpcResponse.error(req.id(), JsonRpcError.METHOD_NOT_FOUND,
                        "Method not found: " + req.method());
            };
        } catch (McpToolException e) {
            log.debug("[MCP] tool error method={} code={} msg={}", req.method(), e.getCode(), e.getMessage());
            return JsonRpcResponse.error(req.id(), e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("[MCP] internal error method={}: {}", req.method(), e.getMessage(), e);
            return JsonRpcResponse.error(req.id(), JsonRpcError.INTERNAL_ERROR,
                    "Internal error: " + e.getMessage());
        }
    }

    private Map<String, Object> buildInitializeResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", protocolVersion);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", Map.of("name", serverName, "version", serverVersion));
        return result;
    }

    private Map<String, Object> buildToolsListResult() {
        List<Map<String, Object>> arr = tools.all().stream().map(t -> {
            Map<String, Object> tBean = new LinkedHashMap<>();
            tBean.put("name", t.name());
            tBean.put("description", t.description());
            tBean.put("inputSchema", t.inputSchema());
            return tBean;
        }).toList();
        return Map.of("tools", arr);
    }

    private Map<String, Object> buildToolsCallResult(JsonNode params, RequestContext ctx) {
        if (params == null || !params.has("name") || params.get("name").isNull()) {
            throw McpToolException.invalidParams("missing field 'name'");
        }
        String name = params.get("name").asText();
        JsonNode args = params.has("arguments") ? params.get("arguments") : null;
        McpTool tool = tools.require(name);
        JsonNode toolResult = tool.call(args, ctx);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "text");
        entry.put("text", toolResult == null ? "" : toolResult.toString());
        return Map.of("content", List.of(entry));
    }
}
