package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceDomain;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceDomainRepository;
import cn.kuship.console.modules.gateway.service.GatewayContextLoader;
import cn.kuship.console.modules.gateway.service.GatewayDomainService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 组件 HTTP 域名 CRUD。
 * rainbond 锚点: {@code urls.py:641 ServiceDomainView}
 * 路径: {@code /console/teams/{team_name}/apps/{service_alias}/domain}
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/apps/{service_alias}/domain",
        "/console/teams/{team_name}/apps/{service_alias}/domain/"
})
public class ServiceDomainController {

    private final GatewayContextLoader loader;
    private final ServiceDomainRepository domainRepo;
    private final GatewayDomainService domainService;
    private final RequestContext ctx;

    public ServiceDomainController(GatewayContextLoader loader,
                                    ServiceDomainRepository domainRepo,
                                    GatewayDomainService domainService,
                                    RequestContext ctx) {
        this.loader = loader;
        this.domainRepo = domainRepo;
        this.domainService = domainService;
        this.ctx = ctx;
    }

    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public List<ServiceDomain> list(@PathVariable("team_name") String teamName,
                                     @PathVariable("service_alias") String serviceAlias,
                                     @RequestParam(required = false) Integer container_port) {
        GatewayContextLoader.GatewayCtx c = loader.require(teamName, serviceAlias);
        if (container_port != null) {
            return domainRepo.findByServiceIdAndContainerPort(c.service().getServiceId(), container_port);
        }
        return domainRepo.findByServiceIdIn(List.of(c.service().getServiceId()));
    }

    @PostMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ServiceDomain bind(@PathVariable("team_name") String teamName,
                               @PathVariable("service_alias") String serviceAlias,
                               @RequestBody Map<String, Object> body) {
        GatewayContextLoader.GatewayCtx c = loader.require(teamName, serviceAlias);
        TenantService svc = c.service();
        Tenants tenant = c.tenant();

        // 注入 service 信息到 body
        body.put("service_id", svc.getServiceId());
        body.put("service_alias", svc.getServiceAlias());
        body.put("service_name", svc.getServiceCname());
        body.put("tenant_id", tenant.getTenantId());
        body.put("region_id", svc.getServiceRegion());

        String regionName = svc.getServiceRegion();
        return domainService.bindHttpDomain(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), tenant.getTenantId(), body);
    }

    @DeleteMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public Map<String, Object> unbind(@PathVariable("team_name") String teamName,
                                       @PathVariable("service_alias") String serviceAlias,
                                       @RequestBody Map<String, Object> body) {
        GatewayContextLoader.GatewayCtx c = loader.require(teamName, serviceAlias);
        String httpRuleId = (String) body.get("http_rule_id");
        String regionName = c.service().getServiceRegion();
        String tenantName = c.tenant().getTenantName();
        domainService.unbindHttpDomain(regionName, ctx.getEnterpriseId(), tenantName, httpRuleId);
        return Map.of("success", true);
    }
}
