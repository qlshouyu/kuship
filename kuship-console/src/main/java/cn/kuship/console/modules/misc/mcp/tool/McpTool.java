package cn.kuship.console.modules.misc.mcp.tool;

import cn.kuship.console.common.context.RequestContext;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * MCP tool 抽象。每个 @Component 实现类自动被 {@link McpToolRegistry} 收集；新增 tool 只需新建一个 @Component，
 * 不需要修改 protocol layer 或 registry。
 */
public interface McpTool {

    /** 全局唯一 name（snake_case，与 rainbond Python tool 名对齐）。 */
    String name();

    /** 给 LLM 看的描述。 */
    String description();

    /** JSON Schema Draft 7 root object 描述入参；空对象 = 无参数。 */
    Map<String, Object> inputSchema();

    /**
     * 执行 tool。失败抛 {@link McpToolException} 让协议层映射为 JSON-RPC error；
     * 返回值会被包装为 MCP {@code tools/call} result 的 {@code content[]} 数组的第一个 entry。
     */
    JsonNode call(JsonNode arguments, RequestContext ctx);
}
