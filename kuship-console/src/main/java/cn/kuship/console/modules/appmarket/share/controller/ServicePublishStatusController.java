package cn.kuship.console.modules.appmarket.share.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.appmarket.share.api.ShareOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用发布状态查询（{@code GET /console/teams/{team_name}/apps/{service_alias}/publish/status}）。
 *
 * <p>UI 分享前置校验：根据 {@code service_key + app_version} 查 region 端发布状态。
 * region URL 不含 namespace 段（{@link ShareOperations#getServicePublishStatus} 是 7 个 method 中
 * 唯一例外）。
 */
@RestController
public class ServicePublishStatusController {

    private final ShareOperations shareOps;
    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public ServicePublishStatusController(ShareOperations shareOps,
                                            TenantsRepository tenantsRepo,
                                            TenantServiceRepository serviceRepo) {
        this.shareOps = shareOps;
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/publish/status",
                          "/console/teams/{team_name}/apps/{service_alias}/publish/status/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult status(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @RequestParam("service_key") String serviceKey,
                              @RequestParam("app_version") String appVersion) {
        Tenants tenant = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        TenantService svc = serviceRepo.findByTenantIdAndServiceAlias(tenant.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
        return GeneralMessage.ok(shareOps.getServicePublishStatus(
                svc.getServiceRegion(), teamName, serviceKey, appVersion));
    }
}
