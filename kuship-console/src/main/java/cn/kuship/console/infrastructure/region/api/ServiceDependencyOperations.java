package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Service 依赖关系域 region API 操作。
 *
 * <p><b>实现 change：{@code migrate-console-application-core}</b>。
 *
 * <p>对应 Python {@code add_service_dependency}/{@code add_service_dependencys}/
 * {@code delete_service_dependency} 等。
 */
public interface ServiceDependencyOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-application-core";

    default Map<String, Object> addDependency(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> addDependencies(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default void deleteDependency(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> addVolumeDependency(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default void deleteVolumeDependency(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { unsupported(IMPLEMENTING_CHANGE); }
}
