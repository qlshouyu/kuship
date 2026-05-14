package cn.kuship.console.modules.region.resource.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 团队组件列表端点（纯本地查询）。
 *
 * <p>对应 rainbond {@code views/team_resources.py TeamComponentsView}（GET）。
 * 不调 region API，从本地 {@code tenant_service} 表查。
 */
@RestController
@RequestMapping("/console/teams/{team_name}/regions/{region_name}")
public class TeamComponentsController {

    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public TeamComponentsController(TenantsRepository tenantsRepo,
                                     TenantServiceRepository serviceRepo) {
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    /** GET /console/teams/{team_name}/regions/{region_name}/components */
    @GetMapping(value = {"/components", "/components/"})
    public ApiResult listComponents(@PathVariable("team_name") String teamName,
                                     @PathVariable("region_name") String regionName) {
        Tenants tenant = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        List<TenantService> services = serviceRepo.findByServiceRegionAndTenantId(
                regionName, tenant.getTenantId());
        List<Map<String, Object>> result = services.stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("service_id", s.getServiceId());
                    m.put("service_cname", s.getServiceCname());
                    m.put("service_alias", s.getServiceAlias());
                    return m;
                })
                .toList();
        return GeneralMessage.okList(result);
    }
}
