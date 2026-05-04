package cn.kuship.console.modules.misc.mcp.tool.impl;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.misc.mcp.tool.McpTool;
import cn.kuship.console.modules.misc.mcp.tool.McpToolException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListAppsTool implements McpTool {

    private final TenantsRepository tenantsRepo;
    private final ServiceGroupRepository groupRepo;
    private final ObjectMapper json;

    public ListAppsTool(TenantsRepository tenantsRepo, ServiceGroupRepository groupRepo, ObjectMapper json) {
        this.tenantsRepo = tenantsRepo;
        this.groupRepo = groupRepo;
        this.json = json;
    }

    @Override public String name() { return "list_apps"; }

    @Override public String description() {
        return "List applications (service_group) within a team and region.";
    }

    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("team_name", Map.of("type", "string", "description", "team_name (tenant_name) or team_id"));
        props.put("region_name", Map.of("type", "string", "description", "region name"));
        return Map.of("type", "object", "properties", props,
                "required", List.of("team_name", "region_name"));
    }

    @Override public JsonNode call(JsonNode arguments, RequestContext ctx) {
        String teamName = readString(arguments, "team_name");
        String regionName = readString(arguments, "region_name");
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .or(() -> tenantsRepo.findByTenantId(teamName))
                .orElseThrow(() -> McpToolException.invalidParams("team not found: " + teamName));
        List<ServiceGroup> groups = groupRepo.findByTenantId(team.getTenantId()).stream()
                .filter(g -> regionName.equals(g.getRegionName()))
                .toList();
        List<Map<String, Object>> beans = groups.stream().map(g -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("app_id", g.getId());
            b.put("group_name", g.getGroupName());
            b.put("region_name", g.getRegionName());
            b.put("governance_mode", g.getGovernanceMode());
            b.put("k8s_app", g.getK8sApp());
            return b;
        }).toList();
        return json.valueToTree(Map.of("apps", beans));
    }

    static String readString(JsonNode args, String field) {
        if (args == null || !args.has(field) || args.get(field).isNull()) {
            throw McpToolException.invalidParams("missing field '" + field + "'");
        }
        return args.get(field).asText();
    }
}
