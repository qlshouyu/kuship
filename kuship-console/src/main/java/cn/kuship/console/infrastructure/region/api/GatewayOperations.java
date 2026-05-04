package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Gateway 路由 / 证书 / ingress 域。
 * <b>实现 change：{@code migrate-console-application-core}（部分）/ {@code migrate-console-region-cluster}（部分）</b>。
 * 对应 Python {@code get_gateway_certificate}/{@code create_gateway_certificate}/
 * {@code update_gateway_certificate}/{@code delete_gateway_certificate}/
 * {@code update_ingresses_by_certificate} 等。
 */
public interface GatewayOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-application-core";

    default Map<String, Object> getCertificate(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> createCertificate(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> updateCertificate(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default void deleteCertificate(String regionName, String tenantName, String namespace, String name) { unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> updateIngressesByCertificate(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    /**
     * 通过 rainbond-go core 的 api-gateway 透传层调用任意路径（add-gray-release 场景下用于
     * 更新 ApisixRoute 后端权重）。
     * <p>POST 实现细节由 {@code GatewayOperationsImpl} 提供；默认实现 {@code unsupported}。
     *
     * @param regionName    区域
     * @param enterpriseId  企业 ID（mTLS 客户端缓存 key）
     * @param tenantName    租户 name（路径变量 {tenant_name}）
     * @param path          完整 sub-path，例如 {@code /api-gateway/v1/{tenant_name}/routes/http?appID=...}
     * @param body          JSON body
     * @return 解析后的 JSON Map
     */
    default Map<String, Object> apiGatewayProxy(String regionName, String enterpriseId,
                                                 String tenantName, String path, Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }
}
