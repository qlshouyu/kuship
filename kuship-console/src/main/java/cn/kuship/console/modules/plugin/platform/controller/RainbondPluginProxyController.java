package cn.kuship.console.modules.plugin.platform.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.response.SkipResponseWrapper;
import cn.kuship.console.modules.plugin.api.RainbondPluginOperations;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/** Rainbond 平台插件代理：status 查询 + 静态资源 + 通用反向代理。 */
@RestController
@RequestMapping("/console/regions/{region_name}")
public class RainbondPluginProxyController {

    private final RainbondPluginOperations rbdPluginOps;

    public RainbondPluginProxyController(RainbondPluginOperations rbdPluginOps) {
        this.rbdPluginOps = rbdPluginOps;
    }

    @GetMapping(value = {"/plugins/{plugin_name}/status", "/plugins/{plugin_name}/status/"})
    public ApiResult status(@PathVariable("region_name") String regionName,
                              @PathVariable("plugin_name") String pluginName) {
        return GeneralMessage.ok(rbdPluginOps.getPluginStatus(regionName, pluginName));
    }

    @GetMapping(value = {"/static/plugins/{plugin_name}", "/static/plugins/{plugin_name}/"})
    @SkipResponseWrapper
    public ResponseEntity<byte[]> staticResource(@PathVariable("region_name") String regionName,
                                                     @PathVariable("plugin_name") String pluginName) {
        return rbdPluginOps.proxyStaticResource(regionName, pluginName);
    }

    @RequestMapping("/proxy/plugins/{plugin_name}/**")
    @SkipResponseWrapper
    public ResponseEntity<byte[]> proxy(@PathVariable("region_name") String regionName,
                                            @PathVariable("plugin_name") String pluginName,
                                            HttpServletRequest request) throws IOException {
        String filePath = extractFilePath(request, "/proxy/plugins/" + pluginName + "/");
        byte[] body = request.getInputStream().readAllBytes();
        return rbdPluginOps.proxyBackend(regionName, pluginName, filePath,
                request.getMethod(), body, request.getContentType());
    }

    @RequestMapping("/backend/plugins/{plugin_name}/**")
    @SkipResponseWrapper
    public ResponseEntity<byte[]> backend(@PathVariable("region_name") String regionName,
                                              @PathVariable("plugin_name") String pluginName,
                                              HttpServletRequest request) throws IOException {
        String filePath = extractFilePath(request, "/backend/plugins/" + pluginName + "/");
        byte[] body = request.getInputStream().readAllBytes();
        return rbdPluginOps.proxyBackend(regionName, pluginName, filePath,
                request.getMethod(), body, request.getContentType());
    }

    private static String extractFilePath(HttpServletRequest request, String marker) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf(marker);
        return idx >= 0 ? uri.substring(idx + marker.length()) : "";
    }
}
