package cn.kuship.console.modules.gateway.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.stereotype.Component;

/**
 * 网关域 公共上下文加载器（从 teamName + serviceAlias 解析出 Tenants + TenantService）。
 */
@Component
public class GatewayContextLoader {

    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public GatewayContextLoader(TenantsRepository tenantsRepo,
                                 TenantServiceRepository serviceRepo) {
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    public Tenants requireTenant(String teamName) {
        return tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }

    public TenantService requireService(String tenantId, String serviceAlias) {
        return serviceRepo.findByTenantIdAndServiceAlias(tenantId, serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    public record GatewayCtx(Tenants tenant, TenantService service) {}

    public GatewayCtx require(String teamName, String serviceAlias) {
        Tenants tenant = requireTenant(teamName);
        TenantService service = requireService(tenant.getTenantId(), serviceAlias);
        return new GatewayCtx(tenant, service);
    }
}
