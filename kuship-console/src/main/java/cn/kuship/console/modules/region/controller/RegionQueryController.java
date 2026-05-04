package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import cn.kuship.console.infrastructure.region.api.TenantOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.region.dto.RegionDto;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Region 元信息查询端点（全局 regions、publickey、features、protocols）。 */
@RestController
@RequestMapping("/console")
public class RegionQueryController {

    private final RegionInfoEntityRepository regionRepo;
    private final TenantsRepository tenantsRepo;
    private final TenantOperations tenantOperations;
    private final ClusterOperations clusterOperations;
    private final RequestContext requestContext;

    public RegionQueryController(RegionInfoEntityRepository regionRepo,
                                   TenantsRepository tenantsRepo,
                                   TenantOperations tenantOperations,
                                   ClusterOperations clusterOperations,
                                   RequestContext requestContext) {
        this.regionRepo = regionRepo;
        this.tenantsRepo = tenantsRepo;
        this.tenantOperations = tenantOperations;
        this.clusterOperations = clusterOperations;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/regions", "/regions/"})
    public ApiResult listAll() {
        List<Map<String, Object>> rows = regionRepo.findAll().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            RegionDto dto = RegionDto.fromSafe(r);
            m.put("region_id", dto.regionId());
            m.put("region_name", dto.regionName());
            m.put("region_alias", dto.regionAlias());
            m.put("status", dto.status());
            m.put("scope", dto.scope());
            return m;
        }).toList();
        return GeneralMessage.okList(rows);
    }

    @GetMapping(value = {"/teams/{team_name}/regions/{region_name}/publickey",
            "/teams/{team_name}/regions/{region_name}/publickey/"})
    public ApiResult publicKey(@PathVariable("team_name") String teamName,
                                 @PathVariable("region_name") String regionName) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        var resp = tenantOperations.getRegionPublickey(regionName, team.getEnterpriseId(),
                team.getTenantName(), team.getTenantId());
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("public_key", resp.publicKey());
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/teams/{team_name}/regions/{region_name}/features",
            "/teams/{team_name}/regions/{region_name}/features/"})
    public ApiResult features(@PathVariable("team_name") String teamName,
                                @PathVariable("region_name") String regionName) {
        var resp = clusterOperations.getRegionFeatures(regionName, teamName);
        return GeneralMessage.okList(resp.features() != null ? resp.features() : List.of());
    }

    @GetMapping(value = {"/teams/{tenant_name}/protocols", "/teams/{tenant_name}/protocols/"})
    public ApiResult protocols(@PathVariable("tenant_name") String tenantName) {
        return GeneralMessage.okList(List.of("HTTP", "HTTPS", "TCP", "UDP", "GRPC"));
    }
}
