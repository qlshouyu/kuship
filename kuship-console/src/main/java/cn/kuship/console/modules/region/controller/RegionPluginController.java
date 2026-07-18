package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.region.service.PlatformPluginService;
import cn.kuship.console.modules.region.service.RainbondPluginService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 集群插件查询（对齐 rainbond PlatformPluginLView / RainbondOfficialPluginLView）。
 */
@RestController
public class RegionPluginController {

    private final PlatformPluginService platformPluginService;
    private final RainbondPluginService rainbondPluginService;

    public RegionPluginController(PlatformPluginService platformPluginService,
                                  RainbondPluginService rainbondPluginService) {
        this.platformPluginService = platformPluginService;
        this.rainbondPluginService = rainbondPluginService;
    }

    /** GET /console/enterprise/{enterprise_id}/regions/{region_name}/plugins —— 集群插件（official=false，仅返回 list）。 */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/plugins")
    public ApiResult plugins(@PathVariable("enterprise_id") String enterpriseId,
                             @PathVariable("region_name") String regionName) {
        RainbondPluginService.PluginResult r =
                rainbondPluginService.listPlugins(enterpriseId, regionName, false);
        return GeneralMessage.list(200, "success", "查询成功", r.plugins());
    }

    /** GET /console/enterprise/{enterprise_id}/regions/{region_name}/platform-plugins */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/platform-plugins")
    public ApiResult platformPlugins(@PathVariable("enterprise_id") String enterpriseId,
                                     @PathVariable("region_name") String regionName) {
        return GeneralMessage.list(200, "success", "查询成功",
                platformPluginService.listPlatformPlugins(enterpriseId, regionName));
    }

    /** GET /console/enterprise/{enterprise_id}/regions/{region_name}/officialplugins */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/officialplugins")
    public ApiResult officialPlugins(@PathVariable("enterprise_id") String enterpriseId,
                                     @PathVariable("region_name") String regionName) {
        RainbondPluginService.PluginResult r =
                rainbondPluginService.listOfficialPlugins(enterpriseId, regionName);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("need_authz", r.needAuthz());
        return ApiResult.of(200, "success", "查询成功", bean, r.plugins());
    }
}
