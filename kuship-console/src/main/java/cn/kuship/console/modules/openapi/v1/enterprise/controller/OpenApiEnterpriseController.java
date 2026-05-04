package cn.kuship.console.modules.openapi.v1.enterprise.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAPI v1 enterprise + monitor 端点：14 endpoint。多数为 Prometheus 透传或聚合占位。 */
@RestController
public class OpenApiEnterpriseController {

    private final TenantsRepository tenantsRepo;
    private final ServiceGroupRepository groupRepo;
    private final TenantServiceRepository serviceRepo;
    private final RegionInfoEntityRepository regionRepo;
    private final RequestContext requestContext;

    public OpenApiEnterpriseController(TenantsRepository tenantsRepo,
                                          ServiceGroupRepository groupRepo,
                                          TenantServiceRepository serviceRepo,
                                          RegionInfoEntityRepository regionRepo,
                                          RequestContext requestContext) {
        this.tenantsRepo = tenantsRepo;
        this.groupRepo = groupRepo;
        this.serviceRepo = serviceRepo;
        this.regionRepo = regionRepo;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/openapi/v1/overview", "/openapi/v1/overview/"})
    public Map<String, Object> overview() {
        String eid = requestContext.getEnterpriseId();
        long teamCount = (eid != null && !eid.isBlank()
                ? tenantsRepo.findByEnterpriseId(eid)
                : tenantsRepo.findAll()).size();
        long appCount = groupRepo.count();
        long componentCount = serviceRepo.count();
        long regionCount = (eid != null && !eid.isBlank()
                ? regionRepo.findByEnterpriseId(eid)
                : regionRepo.findAll()).size();
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("enterprise_id", eid == null ? "" : eid);
        b.put("team_count", teamCount);
        b.put("app_count", appCount);
        b.put("component_count", componentCount);
        b.put("region_count", regionCount);
        return b;
    }

    @GetMapping(value = {"/openapi/v1/monitor/resource_over_view", "/openapi/v1/monitor/resource_over_view/"})
    public Map<String, Object> resourceOverview() {
        return Map.of("memory", 0, "cpu", 0, "disk", 0);
    }

    @GetMapping(value = {"/openapi/v1/monitor/service_overview", "/openapi/v1/monitor/service_overview/"})
    public Map<String, Object> serviceOverview() {
        return Map.of("running", 0, "stopped", 0, "abnormal", 0);
    }

    @GetMapping(value = {"/openapi/v1/monitor/component_memory_overview",
                          "/openapi/v1/monitor/component_memory_overview/"})
    public List<Map<String, Object>> componentMemoryOverview() {
        return List.of();
    }

    @GetMapping(value = {"/openapi/v1/monitor/performance_overview",
                          "/openapi/v1/monitor/performance_overview/"})
    public Map<String, Object> performanceOverview() {
        return Map.of("p50", 0, "p95", 0, "p99", 0);
    }

    @GetMapping(value = {"/openapi/v1/monitor/query", "/openapi/v1/monitor/query/"})
    public Map<String, Object> query(@RequestParam Map<String, String> params) {
        return Map.of("status", "success", "data", Map.of("resultType", "vector", "result", List.of()));
    }

    @GetMapping(value = {"/openapi/v1/monitor/query_range", "/openapi/v1/monitor/query_range/"})
    public Map<String, Object> queryRange(@RequestParam Map<String, String> params) {
        return Map.of("status", "success", "data", Map.of("resultType", "matrix", "result", List.of()));
    }

    @GetMapping(value = {"/openapi/v1/monitor/series", "/openapi/v1/monitor/series/"})
    public Map<String, Object> series(@RequestParam Map<String, String> params) {
        return Map.of("status", "success", "data", List.of());
    }

    @GetMapping(value = {"/openapi/v1/instances/monitor", "/openapi/v1/instances/monitor/"})
    public List<Map<String, Object>> instancesMonitor() {
        return List.of();
    }
}
