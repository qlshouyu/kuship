package cn.kuship.console.modules.application.controller.version;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应用构建版本管理：列表 / 详情 / 更新 / 删除 / 部署版本 / 源码检测 / 构建状态。
 *
 * <p>对齐 rainbond {@code views/app_config/build_version.py} 等 view。本 change 不读本地表，
 * 全部 region 透传；后续若 deploy_version 列表性能问题，可由 hardening change
 * {@code add-component-list-deploy-version-cache} 引入本地缓存。
 */
@RestController
public class AppVersionsController {

    private final ServiceOperations serviceOps;
    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;
    private final RequestContext requestContext;

    public AppVersionsController(ServiceOperations serviceOps,
                                   TenantsRepository tenantsRepo,
                                   TenantServiceRepository serviceRepo,
                                   RequestContext requestContext) {
        this.serviceOps = serviceOps;
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/build-versions",
                          "/console/teams/{team_name}/apps/{service_alias}/build-versions/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult listVersions(@PathVariable("team_name") String teamName,
                                       @PathVariable("service_alias") String alias) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(serviceOps.getBuildVersions(svc.getServiceRegion(), teamName, alias));
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/build-versions/{version_id}",
                          "/console/teams/{team_name}/apps/{service_alias}/build-versions/{version_id}/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult getVersion(@PathVariable("team_name") String teamName,
                                    @PathVariable("service_alias") String alias,
                                    @PathVariable("version_id") String versionId) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(serviceOps.getBuildVersionById(svc.getServiceRegion(), teamName, alias, versionId));
    }

    @PutMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/build-versions/{version_id}",
                          "/console/teams/{team_name}/apps/{service_alias}/build-versions/{version_id}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult updateVersion(@PathVariable("team_name") String teamName,
                                       @PathVariable("service_alias") String alias,
                                       @PathVariable("version_id") String versionId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(serviceOps.updateBuildVersion(
                svc.getServiceRegion(), teamName, alias, versionId, body));
    }

    @DeleteMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/build-versions/{version_id}",
                              "/console/teams/{team_name}/apps/{service_alias}/build-versions/{version_id}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult deleteVersion(@PathVariable("team_name") String teamName,
                                       @PathVariable("service_alias") String alias,
                                       @PathVariable("version_id") String versionId) {
        TenantService svc = resolveService(teamName, alias);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operator", requestContext.getUsername() != null ? requestContext.getUsername() : "system");
        serviceOps.deleteBuildVersion(svc.getServiceRegion(), teamName, alias, versionId, body);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/deploy-version",
                          "/console/teams/{team_name}/apps/{service_alias}/deploy-version/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult deployVersion(@PathVariable("team_name") String teamName,
                                       @PathVariable("service_alias") String alias) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(serviceOps.getServiceDeployVersion(svc.getServiceRegion(), teamName, alias));
    }

    @PostMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/source-check",
                            "/console/teams/{team_name}/apps/{service_alias}/source-check/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult sourceCheck(@PathVariable("team_name") String teamName,
                                      @PathVariable("service_alias") String alias,
                                      @RequestBody(required = false) Map<String, Object> body) {
        resolveService(teamName, alias);
        return GeneralMessage.ok(serviceOps.serviceSourceCheck(resolveRegion(teamName, alias), teamName, body));
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/source-check/{uuid}",
                          "/console/teams/{team_name}/apps/{service_alias}/source-check/{uuid}/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult sourceCheckResult(@PathVariable("team_name") String teamName,
                                           @PathVariable("service_alias") String alias,
                                           @PathVariable("uuid") String uuid) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(serviceOps.getServiceCheckInfo(svc.getServiceRegion(), teamName, uuid));
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/build-status",
                          "/console/teams/{team_name}/apps/{service_alias}/build-status/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult buildStatus(@PathVariable("team_name") String teamName,
                                      @PathVariable("service_alias") String alias,
                                      @RequestParam("plugin_id") String pluginId,
                                      @RequestParam("build_version") String buildVersion) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(serviceOps.getBuildStatus(
                svc.getServiceRegion(), teamName, pluginId, buildVersion));
    }

    private TenantService resolveService(String teamName, String serviceAlias) {
        Tenants tenant = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        return serviceRepo.findByTenantIdAndServiceAlias(tenant.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    private String resolveRegion(String teamName, String serviceAlias) {
        return resolveService(teamName, serviceAlias).getServiceRegion();
    }
}
