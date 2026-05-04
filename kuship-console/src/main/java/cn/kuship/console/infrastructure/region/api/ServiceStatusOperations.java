package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Service 运行状态 / Pod 信息域。<b>实现 change：{@code migrate-console-app-runtime}</b>。
 * 对应 Python {@code service_status}/{@code check_service_status}/{@code get_service_pods}/
 * {@code pod_detail}/{@code get_dynamic_services_pods}。
 */
public interface ServiceStatusOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-runtime";

    default Map<String, Object> serviceStatus(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> checkServiceStatus(String regionName, String tenantName, String serviceAlias) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getServicePods(String regionName, String tenantName, String serviceAlias, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> podDetail(String regionName, String tenantName, String serviceAlias, String podName) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getDynamicServicesPods(String regionName, String tenantName, String serviceIds) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getUserServiceAbnormalStatus(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }
}
