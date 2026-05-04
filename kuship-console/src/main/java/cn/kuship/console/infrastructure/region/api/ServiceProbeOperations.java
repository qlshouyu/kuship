package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Service 健康探针域。<b>实现 change：{@code migrate-console-application-core}</b>。
 * 对应 Python {@code add_service_probe}/{@code update_service_probec}/{@code delete_service_probe}。
 */
public interface ServiceProbeOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-application-core";

    default Map<String, Object> addProbe(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> updateProbe(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default void deleteProbe(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { unsupported(IMPLEMENTING_CHANGE); }
}
