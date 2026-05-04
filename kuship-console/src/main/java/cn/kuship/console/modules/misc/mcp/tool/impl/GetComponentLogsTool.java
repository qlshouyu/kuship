package cn.kuship.console.modules.misc.mcp.tool.impl;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.infrastructure.region.api.ServiceLogOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.misc.mcp.tool.McpTool;
import cn.kuship.console.modules.misc.mcp.tool.McpToolException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetComponentLogsTool implements McpTool {

    private final TenantServiceRepository serviceRepo;
    private final TenantsRepository tenantsRepo;
    private final ServiceLogOperations logOps;
    private final ObjectMapper json;

    public GetComponentLogsTool(TenantServiceRepository serviceRepo,
                                    TenantsRepository tenantsRepo,
                                    ServiceLogOperations logOps,
                                    ObjectMapper json) {
        this.serviceRepo = serviceRepo;
        this.tenantsRepo = tenantsRepo;
        this.logOps = logOps;
        this.json = json;
    }

    @Override public String name() { return "get_component_logs"; }

    @Override public String description() {
        return "Return last N lines of component logs (passthrough to region API).";
    }

    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("service_id", Map.of("type", "string"));
        props.put("lines", Map.of("type", "integer", "default", 100, "minimum", 1, "maximum", 5000));
        return Map.of("type", "object", "properties", props, "required", List.of("service_id"));
    }

    @Override public JsonNode call(JsonNode arguments, RequestContext ctx) {
        String serviceId = ListAppsTool.readString(arguments, "service_id");
        int lines = arguments.has("lines") && !arguments.get("lines").isNull()
                ? Math.min(Math.max(arguments.get("lines").asInt(), 1), 5000) : 100;
        TenantService svc = serviceRepo.findByServiceId(serviceId)
                .orElseThrow(() -> McpToolException.invalidParams("service not found: " + serviceId));
        Tenants team = tenantsRepo.findByTenantId(svc.getTenantId())
                .orElseThrow(() -> McpToolException.internal("tenant not found: " + svc.getTenantId()));
        Map<String, Object> result;
        try {
            result = logOps.getServiceLogs(svc.getServiceRegion(), team.getTenantName(),
                    svc.getServiceAlias(), Map.of("lines", lines));
        } catch (UnsupportedOperationException e) {
            throw McpToolException.internal("getServiceLogs not yet implemented in region API");
        } catch (RuntimeException e) {
            throw McpToolException.internal("region call failed: " + e.getMessage());
        }
        return json.valueToTree(result == null ? Map.of() : result);
    }
}
