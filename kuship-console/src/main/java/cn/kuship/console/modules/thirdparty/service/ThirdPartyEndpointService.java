package cn.kuship.console.modules.thirdparty.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.thirdparty.api.ThirdPartyServiceOperations;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 第三方组件 endpoint / health 业务层 facade。每个 method 先做组件校验
 * （存在 + serviceSource="third_party"），再委派 region API。
 */
@Service
public class ThirdPartyEndpointService {

    private final TenantsRepository tenantsRepository;
    private final TenantServiceRepository serviceRepository;
    private final ThirdPartyServiceOperations regionOps;

    public ThirdPartyEndpointService(TenantsRepository tenantsRepository,
                                      TenantServiceRepository serviceRepository,
                                      ThirdPartyServiceOperations regionOps) {
        this.tenantsRepository = tenantsRepository;
        this.serviceRepository = serviceRepository;
        this.regionOps = regionOps;
    }

    public Map<String, Object> getEndpoints(String teamName, String serviceAlias) {
        TenantService svc = validateThirdPartyService(teamName, serviceAlias);
        return regionOps.getEndpoints(svc.getServiceRegion(), teamName, serviceAlias);
    }

    public Map<String, Object> postEndpoints(String teamName, String serviceAlias, Map<String, Object> body) {
        TenantService svc = validateThirdPartyService(teamName, serviceAlias);
        return regionOps.postEndpoints(svc.getServiceRegion(), teamName, serviceAlias, body);
    }

    public Map<String, Object> putEndpoints(String teamName, String serviceAlias, Map<String, Object> body) {
        TenantService svc = validateThirdPartyService(teamName, serviceAlias);
        return regionOps.putEndpoints(svc.getServiceRegion(), teamName, serviceAlias, body);
    }

    public Map<String, Object> deleteEndpoints(String teamName, String serviceAlias, Map<String, Object> body) {
        TenantService svc = validateThirdPartyService(teamName, serviceAlias);
        return regionOps.deleteEndpoints(svc.getServiceRegion(), teamName, serviceAlias, body);
    }

    public Map<String, Object> getHealth(String teamName, String serviceAlias) {
        TenantService svc = validateThirdPartyService(teamName, serviceAlias);
        return regionOps.getHealth(svc.getServiceRegion(), teamName, serviceAlias);
    }

    public Map<String, Object> putHealth(String teamName, String serviceAlias, Map<String, Object> body) {
        TenantService svc = validateThirdPartyService(teamName, serviceAlias);
        return regionOps.putHealth(svc.getServiceRegion(), teamName, serviceAlias, body);
    }

    /**
     * 校验组件存在 + 是 third_party 类型。比 rainbond 严一档（rainbond 端 view 未做该校验）。
     */
    private TenantService validateThirdPartyService(String teamName, String serviceAlias) {
        Tenants tenant = tenantsRepository.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        TenantService svc = serviceRepository.findByTenantIdAndServiceAlias(tenant.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
        if (!"third_party".equals(svc.getServiceSource())) {
            throw new ServiceHandleException(400, "service is not a third-party service", "组件不是第三方组件");
        }
        return svc;
    }
}
