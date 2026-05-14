package cn.kuship.console.modules.gateway.api;

import java.util.Map;

/**
 * Gateway API 路由操作（HTTP Route CRUD）。
 *
 * <p>对应 rainbond Python {@code GatewayRoute} / {@code GatewayRouteBatch} view。
 * URL 前缀：{@code /v2/proxy-pass/gateway/{tenant_name}/{kind}*}
 */
public interface GatewayRouteOperations {

    /**
     * 列出 Gateway HTTP 路由。
     * URL: GET {@code /v2/proxy-pass/gateway/{tenantName}/HTTPRoute}
     */
    Map<String, Object> listGatewayRoutes(String regionName, String enterpriseId,
                                           String tenantName, Map<String, Object> params);

    /**
     * 获取单条 Gateway HTTP 路由。
     * URL: GET {@code /v2/proxy-pass/gateway/{tenantName}/HTTPRoute/{routeName}}
     */
    Map<String, Object> getGatewayRoute(String regionName, String enterpriseId,
                                         String tenantName, String routeName);

    /**
     * 创建 Gateway HTTP 路由。
     * URL: POST {@code /v2/proxy-pass/gateway/{tenantName}/HTTPRoute}
     */
    Map<String, Object> addGatewayRoute(String regionName, String enterpriseId,
                                         String tenantName, Map<String, Object> body);

    /**
     * 更新 Gateway HTTP 路由。
     * URL: PUT {@code /v2/proxy-pass/gateway/{tenantName}/HTTPRoute/{routeName}}
     */
    Map<String, Object> updateGatewayRoute(String regionName, String enterpriseId,
                                            String tenantName, String routeName,
                                            Map<String, Object> body);

    /**
     * 删除 Gateway HTTP 路由。
     * URL: DELETE {@code /v2/proxy-pass/gateway/{tenantName}/HTTPRoute/{routeName}}
     */
    void deleteGatewayRoute(String regionName, String enterpriseId,
                             String tenantName, String routeName);
}
