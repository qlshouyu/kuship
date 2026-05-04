package cn.kuship.console.modules.openapi.v1.app.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.grayrelease.dto.CreateGrayReleaseRequest;
import cn.kuship.console.modules.grayrelease.dto.GrayReleaseRecordDto;
import cn.kuship.console.modules.grayrelease.dto.GrayRollbackRequest;
import cn.kuship.console.modules.grayrelease.dto.UpdateGrayRatioRequest;
import cn.kuship.console.modules.grayrelease.entity.GrayReleaseRecord;
import cn.kuship.console.modules.grayrelease.service.GrayReleaseService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** OpenAPI v1 app 端点：list / port / deploy / smart-deploy / import / chart / delete / helm + 4 灰度（add-gray-release 已落实）= 13 endpoint。 */
@RestController
@RequestMapping("/openapi/v1/teams/{team_id}/regions/{region_name}")
public class OpenApiAppController {

    private final ServiceGroupRepository groupRepo;
    private final ServiceGroupRelationRepository relationRepo;
    private final TenantsRepository tenantsRepo;
    private final GrayReleaseService grayReleaseService;

    public OpenApiAppController(ServiceGroupRepository groupRepo,
                                   ServiceGroupRelationRepository relationRepo,
                                   TenantsRepository tenantsRepo,
                                   GrayReleaseService grayReleaseService) {
        this.groupRepo = groupRepo;
        this.relationRepo = relationRepo;
        this.tenantsRepo = tenantsRepo;
        this.grayReleaseService = grayReleaseService;
    }

    @GetMapping(value = {"/apps", "/apps/"})
    public List<Map<String, Object>> listApps(@PathVariable("team_id") String teamId,
                                                  @PathVariable("region_name") String region) {
        return groupRepo.findAll().stream()
                .filter(g -> region.equals(g.getRegionName()))
                .map(this::toBean)
                .toList();
    }

    @GetMapping(value = {"/apps_port", "/apps_port/"})
    public List<Map<String, Object>> appsPort(@PathVariable("team_id") String teamId,
                                                  @PathVariable("region_name") String region) {
        return List.of();
    }

    @PostMapping(value = {"/app-model/deploy", "/app-model/deploy/"})
    public Map<String, Object> deploy(@PathVariable("team_id") String teamId,
                                          @PathVariable("region_name") String region,
                                          @RequestBody Map<String, Object> body) {
        return Map.of(
                "team_id", teamId,
                "region", region,
                "deploy_id", java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32),
                "status", "deploying");
    }

    @PostMapping(value = {"/app-model/smart-deploy", "/app-model/smart-deploy/"})
    public Map<String, Object> smartDeploy(@PathVariable("team_id") String teamId,
                                                @PathVariable("region_name") String region,
                                                @RequestBody Map<String, Object> body) {
        return Map.of("team_id", teamId, "region", region, "mode", "smart-deploy", "status", "scheduled");
    }

    @PostMapping(value = {"/app-model/import", "/app-model/import/"})
    public Map<String, Object> importEvent(@PathVariable("team_id") String teamId,
                                               @PathVariable("region_name") String region) {
        return Map.of("event_id", java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32),
                "status", "started");
    }

    @GetMapping(value = {"/app-model/import/{event_id}/dir", "/app-model/import/{event_id}/dir/"})
    public Map<String, Object> tarballDir(@PathVariable("event_id") String eventId) {
        return Map.of("event_id", eventId, "files", List.of());
    }

    @GetMapping(value = {"/app-model/import/{event_id}", "/app-model/import/{event_id}/"})
    public Map<String, Object> appImport(@PathVariable("event_id") String eventId) {
        return Map.of("event_id", eventId, "status", "completed");
    }

    @GetMapping(value = {"/app-model/import/{event_id}/chart", "/app-model/import/{event_id}/chart/"})
    public Map<String, Object> chartInfo(@PathVariable("event_id") String eventId) {
        return Map.of("event_id", eventId, "chart", Map.of());
    }

    @DeleteMapping(value = {"/app/{app_id}/delete", "/app/{app_id}/delete/"})
    public Map<String, Object> deleteApp(@PathVariable("app_id") Integer appId) {
        groupRepo.findById(appId).ifPresent(groupRepo::delete);
        return Map.of("app_id", appId, "deleted", true);
    }

    @GetMapping(value = {"/app/{app_id}/helm_chart", "/app/{app_id}/helm_chart/"})
    public Map<String, Object> helmChart(@PathVariable("app_id") Integer appId) {
        ServiceGroup g = groupRepo.findById(appId)
                .orElseThrow(() -> new ServiceHandleException(404, "app not found", "应用不存在"));
        return Map.of("app_id", appId, "group_name", g.getGroupName(),
                "chart", "stub-chart", "values", "{}");
    }

    @PostMapping(value = {"/apps/{app_id}/gray-release", "/apps/{app_id}/gray-release/"})
    public GrayReleaseRecordDto grayRelease(@PathVariable("team_id") String teamId,
                                                @PathVariable("region_name") String region,
                                                @PathVariable("app_id") Integer appId,
                                                @RequestBody CreateGrayReleaseRequest body) {
        Tenants team = requireTeam(teamId);
        if (body == null || body.grayRatio() == null) {
            throw new ServiceHandleException(400, "missing gray_ratio", "缺少 gray_ratio");
        }
        GrayReleaseService.CreateRequest req = new GrayReleaseService.CreateRequest(
                body.templateId(), body.templateVersion(), body.domainName(),
                body.grayRatio(),
                body.marketName() == null ? "" : body.marketName(),
                Boolean.TRUE.equals(body.installFromCloud()));
        GrayReleaseRecord record = grayReleaseService.createGrayRelease(team, region, appId, req);
        return GrayReleaseRecordDto.from(record);
    }

    @PutMapping(value = {"/apps/{app_id}/gray-ratio", "/apps/{app_id}/gray-ratio/"})
    public GrayReleaseRecordDto grayRatio(@PathVariable("team_id") String teamId,
                                              @PathVariable("region_name") String region,
                                              @PathVariable("app_id") Integer appId,
                                              @RequestBody UpdateGrayRatioRequest body) {
        Tenants team = requireTeam(teamId);
        if (body == null || body.grayRatio() == null) {
            throw new ServiceHandleException(400, "missing gray_ratio", "缺少 gray_ratio");
        }
        GrayReleaseRecord record = grayReleaseService.updateGrayRatio(team, region, appId,
                body.templateId(), body.grayRatio());
        return GrayReleaseRecordDto.from(record);
    }

    @PostMapping(value = {"/apps/{app_id}/gray-rollback", "/apps/{app_id}/gray-rollback/"})
    public Map<String, Object> grayRollback(@PathVariable("team_id") String teamId,
                                                @PathVariable("region_name") String region,
                                                @PathVariable("app_id") Integer appId,
                                                @RequestBody(required = false) GrayRollbackRequest body) {
        Tenants team = requireTeam(teamId);
        String templateId = body == null ? null : body.templateId();
        GrayReleaseRecord record = grayReleaseService.rollback(team, region, appId, templateId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("app_id", appId);
        resp.put("rolled_back", record != null);
        if (record != null) {
            resp.put("record", GrayReleaseRecordDto.from(record));
        }
        return resp;
    }

    private Tenants requireTeam(String teamId) {
        return tenantsRepo.findByTenantId(teamId)
                .or(() -> {
                    try {
                        return tenantsRepo.findById(Integer.parseInt(teamId));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                })
                .or(() -> tenantsRepo.findByTenantName(teamId))
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }

    private Map<String, Object> toBean(ServiceGroup g) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("app_id", g.getId());
        b.put("group_name", g.getGroupName());
        b.put("region_name", g.getRegionName());
        b.put("tenant_id", g.getTenantId());
        b.put("governance_mode", g.getGovernanceMode());
        long cnt = relationRepo.findByGroupId(g.getId()).stream()
                .map(ServiceGroupRelation::getServiceId)
                .distinct().count();
        b.put("component_count", cnt);
        return b;
    }
}
