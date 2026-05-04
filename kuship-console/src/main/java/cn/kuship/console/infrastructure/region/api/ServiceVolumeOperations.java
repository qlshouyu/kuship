package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Service 存储域。<b>实现 change：{@code migrate-console-application-core}</b>。
 * 对应 Python {@code add_service_volumes}/{@code add_service_dep_volumes}/
 * {@code get_service_volumes}/{@code get_volume_options} 等。
 */
public interface ServiceVolumeOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-application-core";

    default Map<String, Object> getVolumeOptions(String regionName, String tenantName) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getVolumes(String regionName, String tenantName, String serviceAlias) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getVolumeStatus(String regionName, String tenantName, String serviceAlias) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> addVolumes(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default void deleteVolumes(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> upgradeVolumes(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getDepVolumes(String regionName, String tenantName, String serviceAlias) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> addDepVolumes(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default void deleteDepVolumes(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { unsupported(IMPLEMENTING_CHANGE); }
}
