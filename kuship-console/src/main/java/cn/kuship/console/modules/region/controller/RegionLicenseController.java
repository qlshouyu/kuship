package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** License 相关端点。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}")
public class RegionLicenseController {

    private final ClusterOperations clusterOperations;
    private final RegionInfoEntityRepository regionRepo;

    public RegionLicenseController(ClusterOperations clusterOperations,
                                     RegionInfoEntityRepository regionRepo) {
        this.clusterOperations = clusterOperations;
        this.regionRepo = regionRepo;
    }

    @GetMapping(value = {"/licenses", "/licenses/"})
    public ApiResult listLicenses(@PathVariable("enterprise_id") String enterpriseId) {
        // 聚合 N 个 region 的 license status（best-effort，单 region 失败不影响整体）
        List<RegionInfo> regions = regionRepo.findByEnterpriseId(enterpriseId);
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (RegionInfo r : regions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("region_id", r.getRegionId());
            m.put("region_name", r.getRegionName());
            m.put("region_alias", r.getRegionAlias());
            try {
                var status = clusterOperations.getLicenseStatus(r.getRegionName(), enterpriseId);
                m.put("active", status.active());
                m.put("expire_time", status.expireTime());
                m.put("max_node", status.maxNode());
                m.put("max_memory", status.maxMemory());
            } catch (Exception ex) {
                m.put("error", ex.getMessage());
                m.put("active", false);
            }
            rows.add(m);
        }
        return GeneralMessage.okList(rows);
    }

    @GetMapping(value = {"/regions/{region_name}/license/cluster-id", "/regions/{region_name}/license/cluster-id/"})
    public ApiResult clusterId(@PathVariable("enterprise_id") String enterpriseId,
                                 @PathVariable("region_name") String regionName) {
        var resp = clusterOperations.getClusterId(regionName, enterpriseId);
        return GeneralMessage.ok(Map.of("cluster_id", resp.clusterId()));
    }

    @PostMapping(value = {"/regions/{region_name}/license/activate", "/regions/{region_name}/license/activate/"})
    @RequireEnterpriseAdmin
    public ApiResult activate(@PathVariable("enterprise_id") String enterpriseId,
                                @PathVariable("region_name") String regionName,
                                @RequestBody Map<String, Object> body) {
        clusterOperations.activateLicense(regionName, enterpriseId, body == null ? new HashMap<>() : body);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/regions/{region_name}/license/status", "/regions/{region_name}/license/status/"})
    public ApiResult status(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("region_name") String regionName) {
        var resp = clusterOperations.getLicenseStatus(regionName, enterpriseId);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("active", resp.active());
        bean.put("expire_time", resp.expireTime());
        bean.put("max_node", resp.maxNode());
        bean.put("max_memory", resp.maxMemory());
        return GeneralMessage.ok(bean);
    }
}
