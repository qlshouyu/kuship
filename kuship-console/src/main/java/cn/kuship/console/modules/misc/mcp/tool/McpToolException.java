package cn.kuship.console.modules.misc.mcp.tool;

import cn.kuship.console.modules.misc.mcp.protocol.JsonRpcError;
import lombok.Getter;

@Getter
public class McpToolException extends RuntimeException {
    private final int code;

    public McpToolException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static McpToolException invalidParams(String message) {
        return new McpToolException(JsonRpcError.INVALID_PARAMS, message);
    }

    public static McpToolException notFound(String name) {
        return new McpToolException(JsonRpcError.METHOD_NOT_FOUND, "tool '" + name + "' not found");
    }

    public static McpToolException internal(String message) {
        return new McpToolException(JsonRpcError.INTERNAL_ERROR, message);
    }
}
