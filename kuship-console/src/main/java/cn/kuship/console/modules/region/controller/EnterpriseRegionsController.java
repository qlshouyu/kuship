package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import cn.kuship.console.modules.region.dto.AddRegionReq;
import cn.kuship.console.modules.region.dto.RegionDto;
import cn.kuship.console.modules.region.dto.UpdateRegionReq;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.service.RegionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** {@code /console/enterprise/{enterprise_id}/regions} CRUD。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}/regions")
public class EnterpriseRegionsController {

    private final RegionService regionService;
    private final RequestContext requestContext;

    public EnterpriseRegionsController(RegionService regionService, RequestContext requestContext) {
        this.regionService = regionService;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"", "/"})
    public ApiResult list(@PathVariable("enterprise_id") String enterpriseId,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "check_status", required = false) String checkStatus) {
        java.util.List<java.util.Map<String, Object>> rows = regionService.listByEnterprise(enterpriseId, status).stream()
                .map(this::serialize).toList();
        return GeneralMessage.okList(rows);
    }

    @PostMapping(value = {"", "/"})
    @RequireEnterpriseAdmin
    public ApiResult add(@PathVariable("enterprise_id") String enterpriseId,
                          @RequestBody @Valid AddRegionReq req) {
        RegionInfo r = regionService.addRegion(enterpriseId, req);
        return GeneralMessage.ok(serialize(r));
    }

    @GetMapping(value = {"/{region_id}", "/{region_id}/"})
    public ApiResult get(@PathVariable("enterprise_id") String enterpriseId,
                          @PathVariable("region_id") String regionId) {
        RegionInfo r = regionService.requireRegion(enterpriseId, regionId);
        return GeneralMessage.ok(serialize(r));
    }

    @PutMapping(value = {"/{region_id}", "/{region_id}/"})
    @RequireEnterpriseAdmin
    public ApiResult update(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("region_id") String regionId,
                              @RequestBody @Valid UpdateRegionReq req) {
        RegionInfo r = regionService.updateRegion(enterpriseId, regionId, req);
        return GeneralMessage.ok(serialize(r));
    }

    @DeleteMapping(value = {"/{region_id}", "/{region_id}/"})
    @RequireEnterpriseAdmin
    public ApiResult delete(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("region_id") String regionId) {
        regionService.deleteRegion(enterpriseId, regionId);
        return GeneralMessage.ok();
    }

    private java.util.Map<String, Object> serialize(RegionInfo r) {
        // sys_admin 可看 cert 详情；其他用户脱敏
        boolean full = requestContext.isSysAdmin();
        RegionDto dto = full ? RegionDto.fromFull(r) : RegionDto.fromSafe(r);
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("region_id", dto.regionId());
        m.put("region_name", dto.regionName());
        m.put("region_alias", dto.regionAlias());
        m.put("region_type", dto.regionType());
        m.put("url", dto.url());
        m.put("wsurl", dto.wsurl());
        m.put("httpdomain", dto.httpdomain());
        m.put("tcpdomain", dto.tcpdomain());
        m.put("status", dto.status());
        m.put("create_time", dto.createTime());
        m.put("desc", dto.desc());
        m.put("scope", dto.scope());
        m.put("enterprise_id", dto.enterpriseId());
        m.put("provider", dto.provider());
        m.put("provider_cluster_id", dto.providerClusterId());
        m.put("ssl_ca_cert", dto.sslCaCert());
        m.put("cert_file", dto.certFile());
        m.put("key_file", dto.keyFile());
        return m;
    }
}
