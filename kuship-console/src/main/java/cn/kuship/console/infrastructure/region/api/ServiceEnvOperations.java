package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Service 环境变量域。<b>实现 change：{@code migrate-console-application-core}</b>。
 * 对应 Python {@code add_service_env}/{@code update_service_env}/{@code delete_service_env}。
 */
public interface ServiceEnvOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-application-core";

    default Map<String, Object> addEnv(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> updateEnv(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default void deleteEnv(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> addBuildEnv(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }
}
