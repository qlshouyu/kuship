package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import cn.kuship.console.infrastructure.region.api.dto.TenantLimitReq;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 集群命名空间 / 资源 / tenant 管理端点。 */
@RestController
@RequestMapping("/console")
public class ClusterNamespacesController {

    private final ClusterOperations clusterOperations;
    private final RegionInfoEntityRepository regionRepo;
    private final RequestContext requestContext;

    public ClusterNamespacesController(ClusterOperations clusterOperations,
                                         RegionInfoEntityRepository regionRepo,
                                         RequestContext requestContext) {
        this.clusterOperations = clusterOperations;
        this.regionRepo = regionRepo;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/teams/cluster/namespaces", "/teams/cluster/namespaces/"})
    public ApiResult clusterNamespaces(@RequestParam(value = "region_name", required = false) String regionName) {
        // 不带 region_name 时，使用 enterprise 内首个 region（与 rainbond 默认行为一致）
        String enterpriseId = requireEnterpriseId();
        if (regionName == null || regionName.isBlank()) {
            List<RegionInfo> regions = regionRepo.findByEnterpriseId(enterpriseId);
            if (regions.isEmpty()) {
                throw new ServiceHandleException(400, "no region available", "当前企业无可用集群");
            }
            regionName = regions.get(0).getRegionName();
        }
        var resp = clusterOperations.getRegionNamespaces(regionName, enterpriseId, "all");
        return GeneralMessage.okList(resp.namespaces() != null ? resp.namespaces() : List.of());
    }

    @GetMapping(value = {"/enterprise/{enterprise_id}/regions/{region_id}/namespace",
            "/enterprise/{enterprise_id}/regions/{region_id}/namespace/"})
    public ApiResult enterpriseRegionNamespace(@PathVariable("enterprise_id") String enterpriseId,
                                                  @PathVariable("region_id") String regionId,
                                                  @RequestParam(value = "content", required = false, defaultValue = "all") String content) {
        RegionInfo r = requireRegion(enterpriseId, regionId);
        var resp = clusterOperations.getRegionNamespaces(r.getRegionName(), enterpriseId, content);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("list", resp.namespaces() != null ? resp.namespaces() : List.of());
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/enterprise/{enterprise_id}/regions/{region_id}/resource",
            "/enterprise/{enterprise_id}/regions/{region_id}/resource/"})
    public ApiResult enterpriseRegionResource(@PathVariable("enterprise_id") String enterpriseId,
                                                @PathVariable("region_id") String regionId) {
        RegionInfo r = requireRegion(enterpriseId, regionId);
        var resp = clusterOperations.getRegionResources(r.getRegionName(), enterpriseId);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("region_name", resp.regionName());
        bean.put("cap_cpu", resp.capCpu());
        bean.put("cap_mem", resp.capMem());
        bean.put("req_cpu", resp.reqCpu());
        bean.put("req_mem", resp.reqMem());
        bean.put("cap_disk", resp.capDisk());
        bean.put("req_disk", resp.reqDisk());
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/enterprise/{enterprise_id}/regions/{region_id}/tenants",
            "/enterprise/{enterprise_id}/regions/{region_id}/tenants/"})
    public ApiResult listTenantsInRegion(@PathVariable("enterprise_id") String enterpriseId,
                                          @PathVariable("region_id") String regionId) {
        RegionInfo r = requireRegion(enterpriseId, regionId);
        return GeneralMessage.okList(clusterOperations.listTenantsInRegion(r.getRegionName(), enterpriseId));
    }

    @PostMapping(value = {"/enterprise/{enterprise_id}/regions/{region_id}/tenants/{tenant_name}/limit",
            "/enterprise/{enterprise_id}/regions/{region_id}/tenants/{tenant_name}/limit/"})
    @RequireEnterpriseAdmin
    public ApiResult setTenantLimit(@PathVariable("enterprise_id") String enterpriseId,
                                      @PathVariable("region_id") String regionId,
                                      @PathVariable("tenant_name") String tenantName,
                                      @RequestBody @Valid TenantLimitReq req) {
        RegionInfo r = requireRegion(enterpriseId, regionId);
        clusterOperations.setTenantLimit(r.getRegionName(), enterpriseId, tenantName, req);
        return GeneralMessage.ok();
    }

    private RegionInfo requireRegion(String enterpriseId, String regionId) {
        RegionInfo r = regionRepo.findByRegionId(regionId)
                .orElseThrow(() -> new ServiceHandleException(404, "region not found", "集群不存在"));
        if (!enterpriseId.equals(r.getEnterpriseId())) {
            throw new ServiceHandleException(403, "region not in enterprise", "集群不属于该企业");
        }
        return r;
    }

    private String requireEnterpriseId() {
        String enterpriseId = requestContext.getEnterpriseId();
        if (enterpriseId == null || enterpriseId.isBlank()) {
            throw new ServiceHandleException(401, "missing enterprise context", "缺少企业上下文");
        }
        return enterpriseId;
    }
}
