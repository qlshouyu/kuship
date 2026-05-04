package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Service 端口域。<b>实现 change：{@code migrate-console-application-core}</b>。
 * 对应 Python {@code add_service_port}/{@code manage_inner_port}/{@code manage_outer_port}/
 * {@code api_gateway_manage_outer_port} 等。
 */
public interface ServicePortOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-application-core";

    default Map<String, Object> addPort(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> updatePort(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default void deletePort(String regionName, String tenantName, String serviceAlias, int port, String enterpriseId, Map<String, Object> body) { unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> manageInnerPort(String regionName, String tenantName, String serviceAlias, int port, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> manageOuterPort(String regionName, String tenantName, String serviceAlias, int port, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> apiGatewayManageOuterPort(String regionName, String tenantName, String serviceAlias, int port, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }
}
