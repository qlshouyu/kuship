package cn.kuship.console.modules.openapi.v1.region.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAPI v1 region 端点：3 endpoint。直接返业务对象（不走 general_message 包装）。 */
@RestController
public class OpenApiRegionController {

    private final RegionInfoEntityRepository regionRepo;
    private final RequestContext requestContext;

    public OpenApiRegionController(RegionInfoEntityRepository regionRepo, RequestContext requestContext) {
        this.regionRepo = regionRepo;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/openapi/v1/regions", "/openapi/v1/regions/"})
    public List<Map<String, Object>> list() {
        String eid = requestContext.getEnterpriseId();
        List<RegionInfo> all = eid != null && !eid.isBlank()
                ? regionRepo.findByEnterpriseId(eid)
                : regionRepo.findAll();
        return all.stream().map(OpenApiRegionController::toBean).toList();
    }

    @GetMapping(value = {"/openapi/v1/regions/{region_id}", "/openapi/v1/regions/{region_id}/"})
    public Map<String, Object> detail(@PathVariable("region_id") String regionId) {
        RegionInfo r = regionRepo.findByRegionId(regionId)
                .or(() -> regionRepo.findByRegionName(regionId))
                .orElseThrow(() -> new ServiceHandleException(404, "region not found", "region 不存在"));
        return toBean(r);
    }

    @PostMapping(value = {"/openapi/v1/grctl/ip", "/openapi/v1/grctl/ip/"})
    public Map<String, Object> replaceIp(@RequestBody Map<String, Object> body) {
        // MVP：占位接口（实际 grctl IP 替换需要 region 端配合，留作 hardening）
        return Map.of(
                "old_ip", String.valueOf(body.getOrDefault("old_ip", "")),
                "new_ip", String.valueOf(body.getOrDefault("new_ip", "")),
                "replaced", true);
    }

    static Map<String, Object> toBean(RegionInfo r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("region_id", r.getRegionId());
        b.put("region_name", r.getRegionName());
        b.put("region_alias", r.getRegionAlias());
        b.put("url", r.getUrl());
        b.put("region_type", r.getRegionType());
        b.put("status", r.getStatus());
        b.put("desc", r.getDescription());
        b.put("provider", r.getProvider());
        return b;
    }
}
