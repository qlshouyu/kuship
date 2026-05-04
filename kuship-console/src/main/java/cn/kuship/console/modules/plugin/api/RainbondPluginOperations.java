package cn.kuship.console.modules.plugin.api;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/** Rainbond 平台插件 region API（8 method，非 14 接口骨架，本 change 新增）。 */
public interface RainbondPluginOperations {

    Map<String, Object> listPlugins(String regionName);

    Map<String, Object> listPlatformPlugins(String regionName);

    Map<String, Object> listOfficialPlugins(String regionName);

    Map<String, Object> listObservablePlugins(String regionName);

    Map<String, Object> installPlatformPlugin(String regionName, String pluginId, Map<String, Object> body);

    Map<String, Object> getPluginStatus(String regionName, String pluginName);

    /** 静态资源透传：返回 byte[] + Content-Type；超过 10MB 触发 413。 */
    ResponseEntity<byte[]> proxyStaticResource(String regionName, String pluginName);

    /** 后端反向代理：透传任意 method + path + body 至 region；30s timeout。 */
    ResponseEntity<byte[]> proxyBackend(String regionName, String pluginName, String filePath,
                                          String httpMethod, byte[] body, String contentType);
}
