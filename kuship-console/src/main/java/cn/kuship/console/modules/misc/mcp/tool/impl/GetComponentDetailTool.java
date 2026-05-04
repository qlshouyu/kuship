package cn.kuship.console.modules.misc.mcp.tool.impl;

import cn.kuship.console.common.context.RequestContext;
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
public class GetComponentDetailTool implements McpTool {

    private final TenantServiceRepository serviceRepo;
    private final ObjectMapper json;

    public GetComponentDetailTool(TenantServiceRepository serviceRepo, ObjectMapper json) {
        this.serviceRepo = serviceRepo;
        this.json = json;
    }

    @Override public String name() { return "get_component_detail"; }

    @Override public String description() {
        return "Return full component (tenant_service) details by service_id.";
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
        return json.valueToTree(svc);
    }
}
