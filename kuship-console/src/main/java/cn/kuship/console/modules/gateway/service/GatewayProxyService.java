package cn.kuship.console.modules.gateway.service;

import cn.kuship.console.infrastructure.region.api.GatewayOperations;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * API Gateway 透传代理服务（对齐 rainbond Python {@code AppApiGatewayView}）。
 */
@Service
public class GatewayProxyService {

    private final GatewayOperations gatewayOps;

    public GatewayProxyService(GatewayOperations gatewayOps) {
        this.gatewayOps = gatewayOps;
    }

    public Map<String, Object> proxyGet(String regionName, String enterpriseId,
                                         String tenantName, String path) {
        return gatewayOps.apiGatewayGet(regionName, enterpriseId, tenantName, path);
    }

    public Map<String, Object> proxyPost(String regionName, String enterpriseId,
                                          String tenantName, String path, Map<String, Object> body) {
        return gatewayOps.apiGatewayProxy(regionName, enterpriseId, tenantName, path, body);
    }

    public Map<String, Object> proxyPut(String regionName, String enterpriseId,
                                         String tenantName, String path, Map<String, Object> body) {
        return gatewayOps.apiGatewayPut(regionName, enterpriseId, tenantName, path, body);
    }

    public Map<String, Object> proxyDelete(String regionName, String enterpriseId,
                                            String tenantName, String path, Map<String, Object> body) {
        return gatewayOps.apiGatewayDelete(regionName, enterpriseId, tenantName, path, body);
    }

    public Map<String, Object> convert(String regionName, String enterpriseId, Map<String, Object> body) {
        return gatewayOps.apiGatewayBindHttpDomainConvert(regionName, enterpriseId, body);
    }
}
