package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Gateway 路由 / 证书 / ingress / TCP 域 / API Gateway 透传 域。
 *
 * <p>实现 change：{@code migrate-console-application-core}（部分）/
 * {@code migrate-console-gateway-certificate}（证书）/
 * {@code migrate-console-gateway-domain}（HTTP/TCP 域 + 高级路由 + API Gateway 透传）。
 *
 * <p>对应 Python {@code get_gateway_certificate}/{@code bind_http_domain}/{@code bind_tcp_domain} 等。
 */
public interface GatewayOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-gateway-domain";

    // ─── 证书管理（migrate-console-gateway-certificate 实现）───────────────────

    default Map<String, Object> getCertificate(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> createCertificate(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> updateCertificate(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default void deleteCertificate(String regionName, String tenantName, String namespace, String name) { unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> updateIngressesByCertificate(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    // ─── HTTP 域名规则（POST/PUT/DELETE /v2/tenants/{tenant_name}/http-rule）────

    /**
     * 绑定 HTTP 域名规则（rainbond Python {@code bind_http_domain}）。
     * URL: {@code POST /v2/tenants/{tenantName}/http-rule}
     */
    default Map<String, Object> bindHttpDomain(String regionName, String enterpriseId,
                                                String tenantName, Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * 更新 HTTP 域名规则（rainbond Python {@code update_http_domain}）。
     * URL: {@code PUT /v2/tenants/{tenantName}/http-rule}
     */
    default Map<String, Object> updateHttpDomain(String regionName, String enterpriseId,
                                                  String tenantName, Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * 删除 HTTP 域名规则（rainbond Python {@code delete_http_domain}）。
     * URL: {@code DELETE /v2/tenants/{tenantName}/http-rule}
     */
    default void deleteHttpDomain(String regionName, String enterpriseId,
                                   String tenantName, Map<String, Object> body) {
        unsupported(IMPLEMENTING_CHANGE);
    }

    // ─── TCP 域名规则（POST/PUT/DELETE /v2/tenants/{tenant_name}/tcp-rule）──────

    /**
     * 绑定 TCP 域名规则（rainbond Python {@code bind_tcp_domain}）。
     * URL: {@code POST /v2/tenants/{tenantName}/tcp-rule}
     */
    default Map<String, Object> bindTcpDomain(String regionName, String enterpriseId,
                                               String tenantName, Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * 更新 TCP 域名规则（rainbond Python {@code update_tcp_domain}）。
     * URL: {@code PUT /v2/tenants/{tenantName}/tcp-rule}
     */
    default Map<String, Object> updateTcpDomain(String regionName, String enterpriseId,
                                                 String tenantName, Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * 解绑 TCP 域名规则（rainbond Python {@code unbind_tcp_domain}）。
     * URL: {@code DELETE /v2/tenants/{tenantName}/tcp-rule}
     */
    default void unbindTcpDomain(String regionName, String enterpriseId,
                                  String tenantName, Map<String, Object> body) {
        unsupported(IMPLEMENTING_CHANGE);
    }

    // ─── 高级路由配置（PUT /v2/tenants/{tenant_name}/http-rule/{rule_id}/configurations）

    /**
     * 下发高级路由配置（rainbond Python {@code upgrade_configuration}）。
     * URL: {@code PUT /v2/tenants/{tenantName}/http-rule/{ruleId}/configurations}
     */
    default Map<String, Object> upgradeConfiguration(String regionName, String enterpriseId,
                                                      String tenantName, String ruleId,
                                                      Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    // ─── Gateway 信息查询 ──────────────────────────────────────────────────────

    /**
     * 获取 Gateway 列表。
     * URL: {@code GET /v2/tenants/{tenantName}/gateways}
     */
    default Map<String, Object> listGateways(String regionName, String enterpriseId,
                                              String tenantName) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * 获取 API Gateway 信息。
     * URL: {@code GET /v2/tenants/{tenantName}/api-gateway}
     */
    default Map<String, Object> getApiGateway(String regionName, String enterpriseId,
                                               String tenantName) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    // ─── API Gateway 透传（/api-gateway/v1/{tenant_name}/...）────────────────

    /**
     * 通用 API Gateway 代理（POST）—— add-gray-release 与 migrate-console-gateway-domain 共用。
     *
     * @param regionName    区域
     * @param enterpriseId  企业 ID
     * @param tenantName    租户 name
     * @param path          完整 sub-path，例如 {@code /api-gateway/v1/{tenant_name}/routes/http}
     * @param body          JSON body
     * @return 解析后的 JSON Map
     */
    default Map<String, Object> apiGatewayProxy(String regionName, String enterpriseId,
                                                 String tenantName, String path,
                                                 Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * API Gateway 绑定 HTTP 域（GET 透传）。
     */
    default Map<String, Object> apiGatewayGet(String regionName, String enterpriseId,
                                               String tenantName, String path) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * API Gateway 路由 PUT 透传。
     */
    default Map<String, Object> apiGatewayPut(String regionName, String enterpriseId,
                                               String tenantName, String path,
                                               Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * API Gateway 路由 DELETE 透传。
     */
    default Map<String, Object> apiGatewayDelete(String regionName, String enterpriseId,
                                                  String tenantName, String path,
                                                  Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * API Gateway 旧域名 → ApisixRoute 格式转换（POST /api-gateway/convert）。
     */
    default Map<String, Object> apiGatewayBindHttpDomainConvert(String regionName, String enterpriseId,
                                                                  Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }
}
