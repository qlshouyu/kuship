package cn.kuship.console.modules.grayrelease.service;

import cn.kuship.console.infrastructure.region.api.GatewayOperations;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 (originalService, grayService, ratio) 转成 rainbond-go core 的
 * {@code POST /api-gateway/v1/{tenant_name}/routes/http} 调用，由 go core 落到 ApisixRoute CRD。
 *
 * <p>本组件不直接持有 K8s client；与 rainbond-console Python 端
 * {@code _update_apisix_route_weights} 行为对齐：失败抛 {@link RuntimeException} 让上层决定回滚 / WARN。
 */
@Component
public class ApisixRouteWeightUpdater {

    private final GatewayOperations gateway;

    public ApisixRouteWeightUpdater(GatewayOperations gateway) {
        this.gateway = gateway;
    }

    /**
     * @param domainConfig 既有 ApisixRoute 序列化后的字段（hosts / rules / plugins / authentication 等
     *                     12 字段），将原样透传，仅替换 backends；可为空 Map（兜底）。
     */
    public Map<String, Object> update(String regionName, String enterpriseId, String tenantName,
                                       Integer appId, String originalServiceAlias, Integer port,
                                       String originalServiceName, String grayServiceName,
                                       int ratio, Map<String, Object> domainConfig) {
        if (ratio < 0 || ratio > 100) {
            throw new IllegalArgumentException("ratio must be 0-100, got " + ratio);
        }
        Map<String, Object> body = buildBody(originalServiceName, grayServiceName, port, ratio, appId,
                tenantName, domainConfig);
        String path = String.format(
                "/api-gateway/v1/%s/routes/http?appID=%d&service_alias=%s&port=%d",
                tenantName, appId, originalServiceAlias, port);
        return gateway.apiGatewayProxy(regionName, enterpriseId, tenantName, path, body);
    }

    Map<String, Object> buildBody(String origSvc, String graySvc, int port, int ratio,
                                     Integer appId, String tenantName, Map<String, Object> domain) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> d = domain == null ? Map.of() : domain;
        body.put("name", d.getOrDefault("name", ""));
        body.put("app_id", d.getOrDefault("app_id", appId));
        body.put("namespace", d.getOrDefault("namespace", tenantName));
        body.put("section_name", d.getOrDefault("section_name", "default"));
        body.put("gateway_name", d.getOrDefault("gateway_name", "default"));
        body.put("gateway_namespace", d.getOrDefault("gateway_namespace", "rbd-system"));
        body.put("match", d.getOrDefault("match", Map.of()));
        body.put("rules", d.getOrDefault("rules", List.of()));
        body.put("backends", buildBackends(origSvc, graySvc, port, ratio));
        body.put("plugins", d.getOrDefault("plugins", List.of()));
        body.put("websocket", d.getOrDefault("websocket", Boolean.FALSE));
        body.put("authentication", d.getOrDefault("authentication", Map.of()));
        return body;
    }

    private List<Map<String, Object>> buildBackends(String origSvc, String graySvc, int port, int ratio) {
        Map<String, Object> b1 = new LinkedHashMap<>();
        b1.put("service_name", origSvc);
        b1.put("service_port", port);
        b1.put("weight", 100 - ratio);
        Map<String, Object> b2 = new LinkedHashMap<>();
        b2.put("service_name", graySvc);
        b2.put("service_port", port);
        b2.put("weight", ratio);
        return List.of(b1, b2);
    }
}
