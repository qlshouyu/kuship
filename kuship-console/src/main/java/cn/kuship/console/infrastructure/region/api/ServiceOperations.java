package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Service（应用组件）域 region API 操作。
 *
 * <p><b>实现 change：{@code migrate-console-app-create}</b>。
 * 每个 method 默认抛 {@link UnsupportedOperationException}，待该 change 在 @Service 实现类中
 * override 自己用到的 method。
 *
 * <p>对应 Python {@code regionapi.py} 中的 service CRUD / build / code_check 相关方法。
 */
public interface ServiceOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-create";

    /** {@code POST /v2/tenants/{tenant_name}/services} —— create_service */
    default Map<String, Object> createService(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    /** {@code GET /v2/tenants/{tenant_name}/services/{service_alias}} —— get_service_info */
    default Map<String, Object> getServiceInfo(String regionName, String tenantName, String serviceAlias) { return unsupported(IMPLEMENTING_CHANGE); }

    /** {@code PUT /v2/tenants/{tenant_name}/services/{service_alias}} —— update_service */
    default Map<String, Object> updateService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    /** {@code DELETE /v2/tenants/{tenant_name}/services/{service_alias}} —— delete_service */
    default void deleteService(String regionName, String tenantName, String serviceAlias, String enterpriseId, Map<String, Object> body) { unsupported(IMPLEMENTING_CHANGE); }

    /** {@code POST /v2/tenants/{tenant_name}/services/{service_alias}/build} —— build_service */
    default Map<String, Object> buildService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    /** {@code POST /v2/tenants/{tenant_name}/code-check} —— code_check */
    default Map<String, Object> codeCheck(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    /** {@code GET /v2/tenants/{tenant_name}/services/{service_alias}/language} —— get_service_language */
    default Map<String, Object> getServiceLanguage(String regionName, String serviceId, String tenantName) { return unsupported(IMPLEMENTING_CHANGE); }
}
