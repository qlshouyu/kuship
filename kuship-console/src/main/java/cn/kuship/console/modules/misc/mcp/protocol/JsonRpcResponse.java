package cn.kuship.console.modules.misc.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(
        @JsonProperty("jsonrpc") String jsonrpc,
        @JsonProperty("id") Object id,
        @JsonProperty("result") Object result,
        @JsonProperty("error") JsonRpcError error) {

    public static JsonRpcResponse ok(Object id, Object result) {
        return new JsonRpcResponse("2.0", id, result, null);
    }

    public static JsonRpcResponse error(Object id, int code, String message) {
        return new JsonRpcResponse("2.0", id, null, JsonRpcError.of(code, message));
    }
}
