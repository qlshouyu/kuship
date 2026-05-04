package cn.kuship.console.modules.misc.mcp.protocol;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.misc.mcp.tool.McpTool;
import cn.kuship.console.modules.misc.mcp.tool.McpToolException;
import cn.kuship.console.modules.misc.mcp.tool.McpToolRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpProtocolHandlerTest {

    private final ObjectMapper json = JsonMapper.builder().build();
    private final McpToolRegistry registry = new McpToolRegistry(List.of(new EchoTool()));
    private final McpProtocolHandler handler = new McpProtocolHandler(
            registry, json, "2024-11-05", "kuship-console", "0.1.0");
    private final RequestContext ctx = new RequestContext();

    @Test
    void initialize_returns_protocol_capabilities() {
        JsonRpcResponse resp = handler.handle(req(1, "initialize", null), ctx);
        assertNotNull(resp);
        assertEquals(1, resp.id());
        assertNull(resp.error());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.result();
        assertEquals("2024-11-05", result.get("protocolVersion"));
        assertNotNull(result.get("capabilities"));
        assertTrue(((Map<?, ?>) result.get("capabilities")).containsKey("tools"));
    }

    @Test
    void notifications_initialized_returns_null() {
        assertNull(handler.handle(req(null, "notifications/initialized", null), ctx));
    }

    @Test
    void unknown_method_returns_method_not_found() {
        JsonRpcResponse resp = handler.handle(req(2, "unknown/method", null), ctx);
        assertNotNull(resp.error());
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, resp.error().code());
    }

    @Test
    void tools_list_includes_registered_tool() {
        JsonRpcResponse resp = handler.handle(req(3, "tools/list", null), ctx);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).get("name"));
    }

    @Test
    void tools_call_dispatches_to_tool() {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.put("name", "echo");
        params.set("arguments", JsonNodeFactory.instance.objectNode().put("payload", "hello"));
        JsonRpcResponse resp = handler.handle(req(4, "tools/call", params), ctx);
        assertNull(resp.error());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).get("type"));
        assertTrue(content.get(0).get("text").toString().contains("hello"));
    }

    @Test
    void tools_call_unknown_tool_returns_method_not_found() {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.put("name", "no_such_tool");
        JsonRpcResponse resp = handler.handle(req(5, "tools/call", params), ctx);
        assertNotNull(resp.error());
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, resp.error().code());
    }

    @Test
    void tools_call_missing_name_returns_invalid_params() {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        JsonRpcResponse resp = handler.handle(req(6, "tools/call", params), ctx);
        assertEquals(JsonRpcError.INVALID_PARAMS, resp.error().code());
    }

    @Test
    void invalid_request_with_null_method_returns_error() {
        JsonRpcResponse resp = handler.handle(req(7, null, null), ctx);
        assertEquals(JsonRpcError.INVALID_REQUEST, resp.error().code());
    }

    @Test
    void ping_returns_empty_object() {
        JsonRpcResponse resp = handler.handle(req(8, "ping", null), ctx);
        assertNull(resp.error());
        assertEquals(Map.of(), resp.result());
    }

    private static JsonRpcRequest req(Object id, String method, JsonNode params) {
        return new JsonRpcRequest("2.0", id, method, params);
    }

    static class EchoTool implements McpTool {
        @Override public String name() { return "echo"; }
        @Override public String description() { return "echo back"; }
        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties",
                    Map.of("payload", Map.of("type", "string")));
        }
        @Override public JsonNode call(JsonNode args, RequestContext ctx) {
            if (args == null || !args.has("payload")) {
                throw McpToolException.invalidParams("missing payload");
            }
            return args.get("payload");
        }
    }
}
