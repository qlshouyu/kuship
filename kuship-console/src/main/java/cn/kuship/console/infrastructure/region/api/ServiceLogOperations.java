package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Service 日志域。<b>实现 change：{@code migrate-console-app-runtime}</b>。
 * 对应 Python {@code get_service_logs}/{@code get_service_log_files}/{@code get_docker_log_instance}。
 *
 * <p>实时日志流（WebSocket）不在本接口范围；由 {@code migrate-console-app-runtime} 单独引入 WS 接口。
 */
public interface ServiceLogOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-runtime";

    default Map<String, Object> getServiceLogs(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getServiceLogFiles(String regionName, String tenantName, String serviceAlias) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getDockerLogInstance(String regionName, String tenantName, String serviceAlias) { return unsupported(IMPLEMENTING_CHANGE); }
}
