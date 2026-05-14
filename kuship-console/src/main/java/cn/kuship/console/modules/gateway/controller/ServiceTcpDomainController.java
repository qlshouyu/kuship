package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceTcpDomain;
import cn.kuship.console.modules.application.repository.ServiceTcpDomainRepository;
import cn.kuship.console.modules.gateway.service.GatewayContextLoader;
import cn.kuship.console.modules.gateway.service.GatewayTcpDomainService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 团队 TCP 策略 CRUD。
 * rainbond 锚点: {@code urls.py:663 ServiceTcpDomainView}
 * 路径: {@code /console/teams/{team_name}/tcpdomain}
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/tcpdomain",
        "/console/teams/{team_name}/tcpdomain/"
})
public class ServiceTcpDomainController {

    private final GatewayContextLoader loader;
    private final ServiceTcpDomainRepository tcpDomainRepo;
    private final GatewayTcpDomainService tcpDomainService;
    private final RequestContext ctx;

    public ServiceTcpDomainController(GatewayContextLoader loader,
                                       ServiceTcpDomainRepository tcpDomainRepo,
                                       GatewayTcpDomainService tcpDomainService,
                                       RequestContext ctx) {
        this.loader = loader;
        this.tcpDomainRepo = tcpDomainRepo;
        this.tcpDomainService = tcpDomainService;
        this.ctx = ctx;
    }

    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public List<ServiceTcpDomain> list(@PathVariable("team_name") String teamName,
                                        @RequestParam(required = false) String tcp_rule_id) {
        var tenant = loader.requireTenant(teamName);
        if (tcp_rule_id != null) {
            return tcpDomainRepo.findByTcpRuleId(tcp_rule_id)
                    .map(List::of).orElse(List.of());
        }
        return tcpDomainRepo.findAll().stream()
                .filter(d -> tenant.getTenantId().equals(d.getTenantId()))
                .toList();
    }

    @PostMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ServiceTcpDomain bind(@PathVariable("team_name") String teamName,
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
        return tcpDomainService.bindTcpDomain(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), tenant.getTenantId(), body);
    }

    @PutMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ServiceTcpDomain update(@PathVariable("team_name") String teamName,
                                    @RequestBody Map<String, Object> body) {
        var tenant = loader.requireTenant(teamName);
        String tcpRuleId = (String) body.get("tcp_rule_id");
        String regionName = (String) body.getOrDefault("region_name", "");
        return tcpDomainService.updateTcpDomain(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), tcpRuleId, body);
    }

    @DeleteMapping
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public Map<String, Object> unbind(@PathVariable("team_name") String teamName,
                                       @RequestBody Map<String, Object> body) {
        var tenant = loader.requireTenant(teamName);
        String tcpRuleId = (String) body.get("tcp_rule_id");
        String regionName = (String) body.getOrDefault("region_name", "");
        tcpDomainService.unbindTcpDomain(regionName, ctx.getEnterpriseId(),
                tenant.getTenantName(), tcpRuleId);
        return Map.of("success", true);
    }
}
