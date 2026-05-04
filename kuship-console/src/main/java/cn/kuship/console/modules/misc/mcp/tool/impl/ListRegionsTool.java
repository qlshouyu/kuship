package cn.kuship.console.modules.misc.mcp.tool.impl;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.misc.mcp.tool.McpTool;
import cn.kuship.console.modules.misc.mcp.tool.McpToolException;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListRegionsTool implements McpTool {

    private final RegionInfoEntityRepository regionRepo;
    private final ObjectMapper json;

    public ListRegionsTool(RegionInfoEntityRepository regionRepo, ObjectMapper json) {
        this.regionRepo = regionRepo;
        this.json = json;
    }

    @Override public String name() { return "list_regions"; }

    @Override public String description() {
        return "List all regions (clusters) the current user's enterprise has access to.";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
    }

    @Override public JsonNode call(JsonNode arguments, RequestContext ctx) {
        if (ctx.getEnterpriseId() == null || ctx.getEnterpriseId().isBlank()) {
            throw McpToolException.invalidParams("user has no enterprise_id");
        }
        List<RegionInfo> regions = regionRepo.findByEnterpriseId(ctx.getEnterpriseId());
        List<Map<String, Object>> beans = regions.stream().map(r -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("region_id", r.getRegionId());
            b.put("region_name", r.getRegionName());
            b.put("region_alias", r.getRegionAlias());
            b.put("region_type", r.getRegionType());
            b.put("status", r.getStatus());
            return b;
        }).toList();
        return json.valueToTree(Map.of("regions", beans));
    }
}
