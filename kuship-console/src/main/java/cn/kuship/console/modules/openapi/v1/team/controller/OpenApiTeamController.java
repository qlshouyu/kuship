package cn.kuship.console.modules.openapi.v1.team.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAPI v1 team 端点：6 主要端点 + 5 占位 endpoint = 共 11 endpoint。 */
@RestController
public class OpenApiTeamController {

    private final TenantsRepository tenantsRepo;
    private final RegionInfoEntityRepository regionRepo;
    private final RequestContext requestContext;

    public OpenApiTeamController(TenantsRepository tenantsRepo, RegionInfoEntityRepository regionRepo,
                                    RequestContext requestContext) {
        this.tenantsRepo = tenantsRepo;
        this.regionRepo = regionRepo;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/openapi/v1/teams", "/openapi/v1/teams/"})
    public List<Map<String, Object>> list() {
        String eid = requestContext.getEnterpriseId();
        List<Tenants> teams = eid != null && !eid.isBlank()
                ? tenantsRepo.findByEnterpriseId(eid)
                : tenantsRepo.findAll();
        return teams.stream().map(OpenApiTeamController::toBean).toList();
    }

    @GetMapping(value = {"/openapi/v1/teams/resource", "/openapi/v1/teams/resource/"})
    public Map<String, Object> resourceSummary() {
        String eid = requestContext.getEnterpriseId();
        long count = (eid != null && !eid.isBlank()
                ? tenantsRepo.findByEnterpriseId(eid)
                : tenantsRepo.findAll()).size();
        return Map.of("team_count", count, "enterprise_id", eid == null ? "" : eid);
    }

    @GetMapping(value = {"/openapi/v1/app_model", "/openapi/v1/app_model/"})
    public List<Map<String, Object>> entAppModels() {
        // 占位：复用第 9 阶段 RainbondCenterApp 留作 hardening
        return List.of();
    }

    @GetMapping(value = {"/openapi/v1/teams/{team_id}", "/openapi/v1/teams/{team_id}/"})
    public Map<String, Object> detail(@PathVariable("team_id") String teamId) {
        return OpenApiTeamController.toBean(requireTeam(teamId));
    }

    @GetMapping(value = {"/openapi/v1/teams/{team_id}/regions", "/openapi/v1/teams/{team_id}/regions/"})
    public List<Map<String, Object>> teamRegions(@PathVariable("team_id") String teamId) {
        Tenants team = requireTeam(teamId);
        // MVP 简化：返回该企业的全部 region；正式按 team_region_info 关联留作 hardening
        return regionRepo.findByEnterpriseId(team.getEnterpriseId()).stream()
                .map(r -> Map.<String, Object>of(
                        "region_id", r.getRegionId(),
                        "region_name", r.getRegionName(),
                        "region_alias", r.getRegionAlias()))
                .toList();
    }

    @GetMapping(value = {"/openapi/v1/teams/{team_id}/certificates",
                          "/openapi/v1/teams/{team_id}/certificates/"})
    public List<Map<String, Object>> certificates(@PathVariable("team_id") String teamId) {
        return List.of(); // 占位
    }

    @GetMapping(value = {"/openapi/v1/teams/{team_id}/certificates/{certificate_id}",
                          "/openapi/v1/teams/{team_id}/certificates/{certificate_id}/"})
    public Map<String, Object> certificateDetail(@PathVariable("team_id") String teamId,
                                                       @PathVariable("certificate_id") String certId) {
        return Map.of("certificate_id", certId, "team_id", teamId);
    }

    @GetMapping(value = {"/openapi/v1/teams/{team_id}/regions/{region_name}/resource",
                          "/openapi/v1/teams/{team_id}/regions/{region_name}/resource/"})
    public Map<String, Object> regionResource(@PathVariable("team_id") String teamId,
                                                    @PathVariable("region_name") String region) {
        Tenants team = requireTeam(teamId);
        return Map.of(
                "team_id", team.getTenantId(),
                "region_name", region,
                "limit_memory", team.getLimitMemory() == null ? 0 : team.getLimitMemory());
    }

    @GetMapping(value = {"/openapi/v1/teams/{team_id}/regions/{region_name}/overview",
                          "/openapi/v1/teams/{team_id}/regions/{region_name}/overview/"})
    public Map<String, Object> regionOverview(@PathVariable("team_id") String teamId,
                                                    @PathVariable("region_name") String region) {
        return regionResource(teamId, region);
    }

    @GetMapping(value = {"/openapi/v1/teams/{team_id}/regions/{region_name}/events/{event_id}/logs",
                          "/openapi/v1/teams/{team_id}/regions/{region_name}/events/{event_id}/logs/"})
    public Map<String, Object> eventLogs(@PathVariable("team_id") String teamId,
                                              @PathVariable("region_name") String region,
                                              @PathVariable("event_id") String eventId) {
        // 占位：透传 region 留作 hardening
        return Map.of("event_id", eventId, "logs", List.of());
    }

    private Tenants requireTeam(String teamId) {
        return tenantsRepo.findByTenantId(teamId)
                .or(() -> {
                    try {
                        return tenantsRepo.findById(Integer.parseInt(teamId));
                    } catch (NumberFormatException e) {
                        return java.util.Optional.empty();
                    }
                })
                .or(() -> tenantsRepo.findByTenantName(teamId))
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }

    static Map<String, Object> toBean(Tenants t) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("team_id", t.getTenantId());
        b.put("team_name", t.getTenantName());
        b.put("tenant_alias", t.getTenantAlias());
        b.put("namespace", t.getNamespace());
        b.put("enterprise_id", t.getEnterpriseId());
        b.put("limit_memory", t.getLimitMemory());
        b.put("create_time", t.getCreateTime());
        return b;
    }
}
