package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceTcpDomain;
import cn.kuship.console.modules.gateway.service.GatewayContextLoader;
import cn.kuship.console.modules.gateway.service.GatewayQueryService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * TCP 域名列表（搜索 + 分页）。
 * rainbond 锚点: {@code urls.py:659 ServiceTcpDomainQueryView}
 * 路径: {@code /console/teams/{team_name}/tcpdomain/query}
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/tcpdomain/query",
        "/console/teams/{team_name}/tcpdomain/query/"
})
public class ServiceTcpDomainQueryController {

    private final GatewayContextLoader loader;
    private final GatewayQueryService queryService;

    public ServiceTcpDomainQueryController(GatewayContextLoader loader,
                                            GatewayQueryService queryService) {
        this.loader = loader;
        this.queryService = queryService;
    }

    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public Page<ServiceTcpDomain> query(@PathVariable("team_name") String teamName,
                                         @RequestParam(defaultValue = "") String search,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int page_size) {
        var tenant = loader.requireTenant(teamName);
        return queryService.getTeamTcpDomains(tenant.getTenantId(), search, page, page_size);
    }
}
