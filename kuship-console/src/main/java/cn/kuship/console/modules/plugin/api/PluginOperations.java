package cn.kuship.console.modules.plugin.api;

import java.util.Map;

/** Plugin 域 region API（10 method，非 14 接口骨架，本 change 新增）。 */
public interface PluginOperations {

    Map<String, Object> createPlugin(String regionName, String tenantName, Map<String, Object> body);

    Map<String, Object> updatePlugin(String regionName, String tenantName, String pluginId, Map<String, Object> body);

    void deletePlugin(String regionName, String tenantName, String pluginId);

    Map<String, Object> buildPlugin(String regionName, String tenantName, String pluginId, Map<String, Object> body);

    Map<String, Object> getPluginBuildStatus(String regionName, String tenantName, String pluginId, String buildVersion);

    Map<String, Object> installToService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body);

    void uninstallFromService(String regionName, String tenantName, String serviceAlias, String pluginId);

    Map<String, Object> openOnService(String regionName, String tenantName, String serviceAlias, String pluginId, Map<String, Object> body);

    Map<String, Object> syncFromMarket(String regionName, String tenantName, Map<String, Object> body);

    Map<String, Object> installFromMarket(String regionName, String tenantName, Map<String, Object> body);
}
