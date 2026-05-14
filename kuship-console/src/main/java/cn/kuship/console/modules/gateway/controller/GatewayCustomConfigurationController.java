package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.gateway.service.GatewayContextLoader;
import cn.kuship.console.modules.gateway.service.GatewayCustomConfigurationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 5.1 高级路由参数（GET 读 / PUT 写）。
 * rainbond 锚点: {@code urls.py:671 GatewayCustomConfigurationView}
 * 路径: {@code /console/teams/{team_name}/domain/{rule_id}/put_gateway}
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/domain/{rule_id}/put_gateway",
        "/console/teams/{team_name}/domain/{rule_id}/put_gateway/"
})
public class GatewayCustomConfigurationController {

    private final GatewayContextLoader loader;
    private final GatewayCustomConfigurationService configService;
    private final RequestContext ctx;

    public GatewayCustomConfigurationController(GatewayContextLoader loader,
                                                 GatewayCustomConfigurationService configService,
                                                 RequestContext ctx) {
        this.loader = loader;
        this.configService = configService;
        this.ctx = ctx;
    }

    /** GET 获取高级路由配置 Map。 */
    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public Map<String, Object> get(@PathVariable("team_name") String teamName,
                                    @PathVariable("rule_id") String ruleId) {
        return configService.getValue(ruleId);
    }

    /** PUT 写入高级路由配置，同时调 region upgradeConfiguration 下发。 */
    @PutMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public Map<String, Object> put(@PathVariable("team_name") String teamName,
                                    @PathVariable("rule_id") String ruleId,
                                    @RequestBody Map<String, Object> body) {
        var tenant = loader.requireTenant(teamName);
        String regionName = (String) body.getOrDefault("region_name", "");
        configService.setValue(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), ruleId, body);
        return configService.getValue(ruleId);
    }
}
