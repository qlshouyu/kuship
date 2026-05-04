package cn.kuship.console.modules.misc.mcp.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class McpToolRegistry {

    private final Map<String, McpTool> byName = new LinkedHashMap<>();

    public McpToolRegistry(List<McpTool> tools) {
        for (McpTool t : tools) {
            McpTool prev = byName.put(t.name(), t);
            if (prev != null) {
                throw new IllegalStateException(
                        "duplicate McpTool name: " + t.name() + " (" + prev.getClass().getName()
                                + " vs " + t.getClass().getName() + ")");
            }
        }
    }

    public List<McpTool> all() {
        return List.copyOf(byName.values());
    }

    public McpTool require(String name) {
        McpTool t = byName.get(name);
        if (t == null) {
            throw McpToolException.notFound(name);
        }
        return t;
    }

    public int size() {
        return byName.size();
    }
}
