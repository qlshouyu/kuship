package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceDomain;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.gateway.service.GatewayQueryService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 应用维度 HTTP 域名列表。
 * rainbond 锚点: {@code urls.py:667 AppServiceDomainQueryView}
 * 路径: {@code /console/enterprise/{enterprise_id}/team/{team_name}/app/{app_id}/domain}
 */
@RestController
@RequestMapping({
        "/console/enterprise/{enterprise_id}/team/{team_name}/app/{app_id}/domain",
        "/console/enterprise/{enterprise_id}/team/{team_name}/app/{app_id}/domain/"
})
public class AppServiceDomainQueryController {

    private final ServiceGroupRelationRepository groupRelationRepo;
    private final GatewayQueryService queryService;

    public AppServiceDomainQueryController(ServiceGroupRelationRepository groupRelationRepo,
                                            GatewayQueryService queryService) {
        this.groupRelationRepo = groupRelationRepo;
        this.queryService = queryService;
    }

    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public Page<ServiceDomain> query(@PathVariable("enterprise_id") String enterpriseId,
                                      @PathVariable("team_name") String teamName,
                                      @PathVariable("app_id") Integer appId,
                                      @RequestParam(defaultValue = "") String search,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int page_size) {
        // 取 app 内所有 serviceId
        List<String> serviceIds = groupRelationRepo.findByGroupId(appId).stream()
                .map(ServiceGroupRelation::getServiceId)
                .toList();
        return queryService.getAppHttpDomains(serviceIds, search, page, page_size);
    }
}
