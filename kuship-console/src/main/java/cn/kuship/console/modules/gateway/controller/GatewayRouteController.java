package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.gateway.service.GatewayContextLoader;
import cn.kuship.console.modules.gateway.service.GatewayRouteService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Gateway API 路由 CRUD。
 * rainbond 锚点: {@code urls.py:647 GatewayRoute}
 * 路径: {@code /console/teams/{team_name}/gateway-http-route}
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/gateway-http-route",
        "/console/teams/{team_name}/gateway-http-route/"
})
public class GatewayRouteController {

    private final GatewayContextLoader loader;
    private final GatewayRouteService routeService;
    private final RequestContext ctx;

    public GatewayRouteController(GatewayContextLoader loader,
                                   GatewayRouteService routeService,
                                   RequestContext ctx) {
        this.loader = loader;
        this.routeService = routeService;
        this.ctx = ctx;
    }

    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public Map<String, Object> list(@PathVariable("team_name") String teamName,
                                     @RequestParam(required = false) Map<String, Object> params) {
        var tenant = loader.requireTenant(teamName);
        String regionName = (String) (params != null ? params.getOrDefault("region_name", "") : "");
        return routeService.listRoutes(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), params);
    }

    @PostMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public Map<String, Object> create(@PathVariable("team_name") String teamName,
                                       @RequestBody Map<String, Object> body) {
        var tenant = loader.requireTenant(teamName);
        String regionName = (String) body.getOrDefault("region_name", "");
        return routeService.addRoute(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), body);
    }

    @PutMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public Map<String, Object> update(@PathVariable("team_name") String teamName,
                                       @RequestBody Map<String, Object> body) {
        var tenant = loader.requireTenant(teamName);
        String regionName = (String) body.getOrDefault("region_name", "");
        String routeName = (String) body.get("name");
        return routeService.updateRoute(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), routeName, body);
    }

    @DeleteMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public Map<String, Object> delete(@PathVariable("team_name") String teamName,
                                       @RequestBody Map<String, Object> body) {
        var tenant = loader.requireTenant(teamName);
        String regionName = (String) body.getOrDefault("region_name", "");
        String routeName = (String) body.get("name");
        routeService.deleteRoute(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), routeName);
        return Map.of("success", true);
    }
}
