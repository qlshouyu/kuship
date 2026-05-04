package cn.kuship.console.modules.misc.mcp.tool.impl;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
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
public class ListComponentsTool implements McpTool {

    private final ServiceGroupRelationRepository relationRepo;
    private final TenantServiceRepository serviceRepo;
    private final ObjectMapper json;

    public ListComponentsTool(ServiceGroupRelationRepository relationRepo,
                                  TenantServiceRepository serviceRepo,
                                  ObjectMapper json) {
        this.relationRepo = relationRepo;
        this.serviceRepo = serviceRepo;
        this.json = json;
    }

    @Override public String name() { return "list_components"; }

    @Override public String description() {
        return "List components (services) within an application by app_id.";
    }

    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("app_id", Map.of("type", "integer", "description", "service_group ID"));
        return Map.of("type", "object", "properties", props, "required", List.of("app_id"));
    }

    @Override public JsonNode call(JsonNode arguments, RequestContext ctx) {
        if (arguments == null || !arguments.has("app_id") || arguments.get("app_id").isNull()) {
            throw McpToolException.invalidParams("missing field 'app_id'");
        }
        Integer appId = arguments.get("app_id").asInt();
        List<String> svcIds = relationRepo.findByGroupId(appId).stream()
                .map(ServiceGroupRelation::getServiceId).distinct().toList();
        if (svcIds.isEmpty()) {
            return json.valueToTree(Map.of("components", List.of()));
        }
        List<TenantService> services = serviceRepo.findByServiceIdIn(svcIds);
        List<Map<String, Object>> beans = services.stream().map(s -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("service_id", s.getServiceId());
            b.put("service_alias", s.getServiceAlias());
            b.put("service_cname", s.getServiceCname());
            b.put("deploy_version", s.getDeployVersion());
            b.put("min_node", s.getMinNode());
            b.put("min_memory", s.getMinMemory());
            b.put("update_time", s.getUpdateTime() == null ? null : s.getUpdateTime().toString());
            return b;
        }).toList();
        return json.valueToTree(Map.of("components", beans));
    }
}
