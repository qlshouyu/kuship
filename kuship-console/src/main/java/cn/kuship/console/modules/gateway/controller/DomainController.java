package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceDomain;
import cn.kuship.console.modules.application.repository.ServiceDomainRepository;
import cn.kuship.console.modules.gateway.service.GatewayContextLoader;
import org.springframework.web.bind.annotation.*;

/**
 * 单条域名查询。
 * rainbond 锚点: {@code urls.py:649 DomainView}
 * 路径: {@code /console/teams/{team_name}/domain}
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/domain",
        "/console/teams/{team_name}/domain/"
})
public class DomainController {

    private final ServiceDomainRepository domainRepo;
    private final GatewayContextLoader loader;

    public DomainController(ServiceDomainRepository domainRepo,
                             GatewayContextLoader loader) {
        this.domainRepo = domainRepo;
        this.loader = loader;
    }

    /** GET 按 http_rule_id 或 domain_name 查单条。 */
    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ServiceDomain get(@PathVariable("team_name") String teamName,
                              @RequestParam(required = false) String http_rule_id,
                              @RequestParam(required = false) String domain_name) {
        if (http_rule_id != null) {
            return domainRepo.findByHttpRuleId(http_rule_id).orElse(null);
        }
        if (domain_name != null) {
            return domainRepo.findAll().stream()
                    .filter(d -> domain_name.equals(d.getDomainName()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}
