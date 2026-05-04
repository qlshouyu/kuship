package cn.kuship.console.modules.misc.mcp.tool.impl;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.entity.PermRelTenant;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.PermRelTenantRepository;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.misc.mcp.tool.McpTool;
import cn.kuship.console.modules.misc.mcp.tool.McpToolException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListTeamsTool implements McpTool {

    private final PermRelTenantRepository permRepo;
    private final TenantsRepository tenantsRepo;
    private final ObjectMapper json;

    public ListTeamsTool(PermRelTenantRepository permRepo, TenantsRepository tenantsRepo, ObjectMapper json) {
        this.permRepo = permRepo;
        this.tenantsRepo = tenantsRepo;
        this.json = json;
    }

    @Override public String name() { return "list_teams"; }

    @Override public String description() {
        return "List all teams the current user belongs to (via tenant_perms).";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
    }

    @Override public JsonNode call(JsonNode arguments, RequestContext ctx) {
        if (ctx.getUserId() == null) {
            throw McpToolException.invalidParams("no authenticated user");
        }
        List<PermRelTenant> rels = permRepo.findByUserId(ctx.getUserId());
        List<Map<String, Object>> beans = rels.stream()
                .map(rel -> tenantsRepo.findById(rel.getTenantId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(t -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("team_id", t.getTenantId());
                    b.put("team_name", t.getTenantName());
                    b.put("tenant_alias", t.getTenantAlias());
                    b.put("namespace", t.getNamespace());
                    b.put("enterprise_id", t.getEnterpriseId());
                    return b;
                }).toList();
        return json.valueToTree(Map.of("teams", beans));
    }
}
