package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.gateway.service.GatewayContextLoader;
import cn.kuship.console.modules.gateway.service.GatewayRouteService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Gateway API 路由批量查询。
 * rainbond 锚点: {@code urls.py:646 GatewayRouteBatch}
 * 路径: {@code /console/teams/{team_name}/batch-gateway-http-route}
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/batch-gateway-http-route",
        "/console/teams/{team_name}/batch-gateway-http-route/"
})
public class GatewayRouteBatchController {

    private final GatewayContextLoader loader;
    private final GatewayRouteService routeService;
    private final RequestContext ctx;

    public GatewayRouteBatchController(GatewayContextLoader loader,
                                        GatewayRouteService routeService,
                                        RequestContext ctx) {
        this.loader = loader;
        this.routeService = routeService;
        this.ctx = ctx;
    }

    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public Map<String, Object> batch(@PathVariable("team_name") String teamName,
                                      @RequestParam(required = false) Map<String, Object> params) {
        var tenant = loader.requireTenant(teamName);
        String regionName = (String) (params != null ? params.getOrDefault("region_name", "") : "");
        return routeService.listRoutes(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), params);
    }
}
