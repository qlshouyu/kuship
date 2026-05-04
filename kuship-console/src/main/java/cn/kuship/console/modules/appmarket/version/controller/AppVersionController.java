package cn.kuship.console.modules.appmarket.version.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 单组件版本快照与回滚（version + snapshot + rollback records）。MVP 透传 region。 */
@RestController
@RequestMapping("/console/teams/{team_name}")
public class AppVersionController {

    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public AppVersionController(TenantsRepository tenantsRepo, TenantServiceRepository serviceRepo) {
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping(value = {"/apps/{service_alias}/version", "/apps/{service_alias}/version/"})
    public ApiResult listVersions(@PathVariable("team_name") String teamName,
                                     @PathVariable("service_alias") String alias) {
        requireService(teamName, alias);
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/apps/{service_alias}/version/{version_id}",
                          "/apps/{service_alias}/version/{version_id}/"})
    public ApiResult versionDetail(@PathVariable("team_name") String teamName,
                                      @PathVariable("service_alias") String alias,
                                      @PathVariable("version_id") String versionId) {
        requireService(teamName, alias);
        return GeneralMessage.ok(Map.of("version_id", versionId));
    }

    @PostMapping(value = {"/apps/{service_alias}/version/{version_id}",
                            "/apps/{service_alias}/version/{version_id}/"})
    public ApiResult rollbackToVersion(@PathVariable("team_name") String teamName,
                                          @PathVariable("service_alias") String alias,
                                          @PathVariable("version_id") String versionId) {
        requireService(teamName, alias);
        return GeneralMessage.ok(Map.of("rolled_to", versionId));
    }

    @GetMapping(value = {"/groups/{group_id}/version/snapshot", "/groups/{group_id}/version/snapshot/"})
    public ApiResult listSnapshots(@PathVariable("team_name") String teamName,
                                      @PathVariable("group_id") Integer groupId) {
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/groups/{group_id}/version/snapshot/{snap_id}",
                          "/groups/{group_id}/version/snapshot/{snap_id}/"})
    public ApiResult snapshotDetail(@PathVariable("team_name") String teamName,
                                       @PathVariable("group_id") Integer groupId,
                                       @PathVariable("snap_id") String snapId) {
        return GeneralMessage.ok(Map.of("snap_id", snapId));
    }

    @PostMapping(value = {"/groups/{group_id}/version/rollback", "/groups/{group_id}/version/rollback/"})
    public ApiResult rollbackGroup(@PathVariable("team_name") String teamName,
                                       @PathVariable("group_id") Integer groupId,
                                       @RequestBody Map<String, Object> body) {
        return GeneralMessage.ok(Map.of("group_id", groupId, "snap_id", body.get("snap_id")));
    }

    @GetMapping(value = {"/groups/{group_id}/version/rollback/records",
                          "/groups/{group_id}/version/rollback/records/"})
    public ApiResult rollbackRecords(@PathVariable("team_name") String teamName,
                                        @PathVariable("group_id") Integer groupId) {
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/groups/{group_id}/version/rollback/records/{record_id}",
                          "/groups/{group_id}/version/rollback/records/{record_id}/"})
    public ApiResult rollbackRecordDetail(@PathVariable("team_name") String teamName,
                                              @PathVariable("group_id") Integer groupId,
                                              @PathVariable("record_id") String recordId) {
        return GeneralMessage.ok(Map.of("record_id", recordId));
    }

    private TenantService requireService(String teamName, String serviceAlias) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        return serviceRepo.findByTenantIdAndServiceAlias(team.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }
}
