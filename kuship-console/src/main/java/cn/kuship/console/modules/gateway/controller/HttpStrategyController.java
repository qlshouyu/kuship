package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceDomain;
import cn.kuship.console.modules.application.repository.ServiceDomainRepository;
import cn.kuship.console.modules.gateway.service.GatewayContextLoader;
import cn.kuship.console.modules.gateway.service.GatewayDomainService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 高级 HTTP 策略（httpdomain）。
 * rainbond 锚点: {@code urls.py:653 HttpStrategyView}
 * 路径: {@code /console/teams/{team_name}/httpdomain}
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/httpdomain",
        "/console/teams/{team_name}/httpdomain/"
})
public class HttpStrategyController {

    private final GatewayContextLoader loader;
    private final ServiceDomainRepository domainRepo;
    private final GatewayDomainService domainService;
    private final RequestContext ctx;

    public HttpStrategyController(GatewayContextLoader loader,
                                   ServiceDomainRepository domainRepo,
                                   GatewayDomainService domainService,
                                   RequestContext ctx) {
        this.loader = loader;
        this.domainRepo = domainRepo;
        this.domainService = domainService;
        this.ctx = ctx;
    }

    /** GET 查询单条域名规则（按 http_rule_id）。 */
    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ServiceDomain get(@PathVariable("team_name") String teamName,
                              @RequestParam(required = false) String http_rule_id,
                              @RequestParam(required = false) String service_alias) {
        if (http_rule_id != null) {
            return domainRepo.findByHttpRuleId(http_rule_id).orElse(null);
        }
        if (service_alias != null) {
            var tenant = loader.requireTenant(teamName);
            List<ServiceDomain> list = domainRepo.findByServiceIdIn(
                    List.of(loader.requireService(tenant.getTenantId(), service_alias).getServiceId()));
            return list.isEmpty() ? null : list.get(0);
        }
        return null;
    }

    /** POST 创建高级策略（与 ServiceDomainView POST 相同，此入口用于指定 serviceAlias）。 */
    @PostMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ServiceDomain create(@PathVariable("team_name") String teamName,
                                  @RequestBody Map<String, Object> body) {
        var tenant = loader.requireTenant(teamName);
        String serviceAlias = (String) body.get("service_alias");
        if (serviceAlias != null) {
            var svc = loader.requireService(tenant.getTenantId(), serviceAlias);
            body.put("service_id", svc.getServiceId());
            body.put("service_name", svc.getServiceCname());
        }
        body.put("tenant_id", tenant.getTenantId());
        String regionName = (String) body.getOrDefault("region_name", "");
        return domainService.bindHttpDomain(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), tenant.getTenantId(), body);
    }

    /** PUT 更新高级策略。 */
    @PutMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ServiceDomain update(@PathVariable("team_name") String teamName,
                                  @RequestBody Map<String, Object> body) {
        var tenant = loader.requireTenant(teamName);
        String httpRuleId = (String) body.get("http_rule_id");
        String regionName = (String) body.getOrDefault("region_name", "");
        return domainService.updateHttpDomain(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), httpRuleId, body);
    }

    /** DELETE 解绑。 */
    @DeleteMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public Map<String, Object> delete(@PathVariable("team_name") String teamName,
                                       @RequestBody Map<String, Object> body) {
        var tenant = loader.requireTenant(teamName);
        String httpRuleId = (String) body.get("http_rule_id");
        String regionName = (String) body.getOrDefault("region_name", "");
        domainService.unbindHttpDomain(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), httpRuleId);
        return Map.of("success", true);
    }
}
