package cn.kuship.console.modules.misc.mcp.tool.impl;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.infrastructure.region.api.ServiceStatusOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.misc.mcp.tool.McpTool;
import cn.kuship.console.modules.misc.mcp.tool.McpToolException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class GetComponentPodsTool implements McpTool {

    private final TenantServiceRepository serviceRepo;
    private final TenantsRepository tenantsRepo;
    private final ServiceStatusOperations statusOps;
    private final ObjectMapper json;

    public GetComponentPodsTool(TenantServiceRepository serviceRepo,
                                    TenantsRepository tenantsRepo,
                                    ServiceStatusOperations statusOps,
                                    ObjectMapper json) {
        this.serviceRepo = serviceRepo;
        this.tenantsRepo = tenantsRepo;
        this.statusOps = statusOps;
        this.json = json;
    }

    @Override public String name() { return "get_component_pods"; }

    @Override public String description() {
        return "Return pod list for a component (transparent passthrough to region API).";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties",
                Map.of("service_id", Map.of("type", "string")),
                "required", List.of("service_id"));
    }

    @Override public JsonNode call(JsonNode arguments, RequestContext ctx) {
        String serviceId = ListAppsTool.readString(arguments, "service_id");
        TenantService svc = serviceRepo.findByServiceId(serviceId)
                .orElseThrow(() -> McpToolException.invalidParams("service not found: " + serviceId));
        Tenants team = tenantsRepo.findByTenantId(svc.getTenantId())
                .orElseThrow(() -> McpToolException.internal("tenant not found: " + svc.getTenantId()));
        Map<String, Object> result;
        try {
            result = statusOps.getServicePods(svc.getServiceRegion(), team.getTenantName(),
                    svc.getServiceAlias(), team.getEnterpriseId());
        } catch (UnsupportedOperationException e) {
            throw McpToolException.internal("getServicePods not yet implemented in region API");
        } catch (RuntimeException e) {
            throw McpToolException.internal("region call failed: " + e.getMessage());
        }
        return json.valueToTree(result == null ? Map.of() : result);
    }
}
