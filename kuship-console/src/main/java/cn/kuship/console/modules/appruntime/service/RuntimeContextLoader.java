package cn.kuship.console.modules.appruntime.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.stereotype.Component;

/** appruntime 共享的"按 team_name + service_alias 取 Tenant + TenantService"辅助。 */
@Component
public class RuntimeContextLoader {

    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public RuntimeContextLoader(TenantsRepository tenantsRepo, TenantServiceRepository serviceRepo) {
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    public Tenants requireTeam(String teamName) {
        return tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }

    public TenantService requireService(String teamName, String serviceAlias) {
        Tenants team = requireTeam(teamName);
        return serviceRepo.findByTenantIdAndServiceAlias(team.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    public record TeamAndService(Tenants team, TenantService service) {}

    public TeamAndService load(String teamName, String serviceAlias) {
        Tenants team = requireTeam(teamName);
        TenantService s = serviceRepo.findByTenantIdAndServiceAlias(team.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
        return new TeamAndService(team, s);
    }
}
