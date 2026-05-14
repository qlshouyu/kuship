package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.ServiceTcpDomain;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.gateway.service.GatewayQueryService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 应用维度 TCP 域名列表。
 * rainbond 锚点: {@code urls.py:665 AppServiceTcpDomainQueryView}
 * 路径: {@code /console/enterprise/{enterprise_id}/team/{team_name}/app/{app_id}/tcpdomain}
 */
@RestController
@RequestMapping({
        "/console/enterprise/{enterprise_id}/team/{team_name}/app/{app_id}/tcpdomain",
        "/console/enterprise/{enterprise_id}/team/{team_name}/app/{app_id}/tcpdomain/"
})
public class AppServiceTcpDomainQueryController {

    private final ServiceGroupRelationRepository groupRelationRepo;
    private final GatewayQueryService queryService;

    public AppServiceTcpDomainQueryController(ServiceGroupRelationRepository groupRelationRepo,
                                               GatewayQueryService queryService) {
        this.groupRelationRepo = groupRelationRepo;
        this.queryService = queryService;
    }

    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public Page<ServiceTcpDomain> query(@PathVariable("enterprise_id") String enterpriseId,
                                         @PathVariable("team_name") String teamName,
                                         @PathVariable("app_id") Integer appId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int page_size) {
        List<String> serviceIds = groupRelationRepo.findByGroupId(appId).stream()
                .map(ServiceGroupRelation::getServiceId)
                .toList();
        return queryService.getAppTcpDomains(serviceIds, page, page_size);
    }
}
