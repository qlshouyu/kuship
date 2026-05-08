package cn.kuship.console.modules.plugin.platform.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.plugin.api.RainbondPluginOperations;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Rainbond 平台插件列表与安装：4 endpoint。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}/regions/{region_name}")
public class RegionPluginController {

    private final RainbondPluginOperations rbdPluginOps;

    public RegionPluginController(RainbondPluginOperations rbdPluginOps) {
        this.rbdPluginOps = rbdPluginOps;
    }

    @GetMapping(value = {"/plugins", "/plugins/"})
    public ApiResult listPlugins(@PathVariable("region_name") String regionName) {
        return GeneralMessage.ok(rbdPluginOps.listPlugins(regionName));
    }

    @GetMapping(value = {"/platform-plugins", "/platform-plugins/"})
    public ApiResult listPlatformPlugins(@PathVariable("region_name") String regionName) {
        return GeneralMessage.ok(rbdPluginOps.listPlatformPlugins(regionName));
    }

    @PostMapping(value = {"/platform-plugins/{plugin_id}/install", "/platform-plugins/{plugin_id}/install/"})
    public ApiResult installPlatformPlugin(@PathVariable("region_name") String regionName,
                                              @PathVariable("plugin_id") String pluginId,
                                              @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(rbdPluginOps.installPlatformPlugin(regionName, pluginId, body));
    }

    @GetMapping(value = {"/officialplugins", "/officialplugins/"})
    public ApiResult listOfficialPlugins(@PathVariable("region_name") String regionName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> wrapped = rbdPluginOps.listOfficialPlugins(regionName);
        java.util.List<?> list = wrapped.get("list") instanceof java.util.List<?> l ? l : java.util.List.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> bean = wrapped.get("bean") instanceof Map ? (Map<String, Object>) wrapped.get("bean") : Map.of();
        return GeneralMessage.okWithExtras(bean, list, null);
    }
}
