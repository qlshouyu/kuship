package cn.kuship.console.modules.gateway.service;

import cn.kuship.console.modules.gateway.api.GatewayRouteOperations;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Gateway Route 业务层（透传 region API，对齐 rainbond Python {@code GatewayRoute} view）。
 */
@Service
public class GatewayRouteService {

    private final GatewayRouteOperations routeOps;

    public GatewayRouteService(GatewayRouteOperations routeOps) {
        this.routeOps = routeOps;
    }

    public Map<String, Object> listRoutes(String regionName, String enterpriseId,
                                           String tenantName, Map<String, Object> params) {
        return routeOps.listGatewayRoutes(regionName, enterpriseId, tenantName, params);
    }

    public Map<String, Object> getRoute(String regionName, String enterpriseId,
                                         String tenantName, String routeName) {
        return routeOps.getGatewayRoute(regionName, enterpriseId, tenantName, routeName);
    }

    public Map<String, Object> addRoute(String regionName, String enterpriseId,
                                         String tenantName, Map<String, Object> body) {
        return routeOps.addGatewayRoute(regionName, enterpriseId, tenantName, body);
    }

    public Map<String, Object> updateRoute(String regionName, String enterpriseId,
                                            String tenantName, String routeName,
                                            Map<String, Object> body) {
        return routeOps.updateGatewayRoute(regionName, enterpriseId, tenantName, routeName, body);
    }

    public void deleteRoute(String regionName, String enterpriseId,
                             String tenantName, String routeName) {
        routeOps.deleteGatewayRoute(regionName, enterpriseId, tenantName, routeName);
    }
}
