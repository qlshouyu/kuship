package cn.kuship.console.modules.gateway.api;

import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

/**
 * {@link GatewayRouteOperations} 完整实现。
 *
 * <p>URL 前缀: {@code /v2/proxy-pass/gateway/{tenantName}/{kind}*}
 */
@Service
@Primary
public class GatewayRouteOperationsImpl implements GatewayRouteOperations {

    private static final String API_TYPE = "gateway-route";
    private static final String KIND = "HTTPRoute";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public GatewayRouteOperationsImpl(RegionClientFactory clientFactory,
                                       RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> listGatewayRoutes(String regionName, String enterpriseId,
                                                   String tenantName, Map<String, Object> params) {
        String url = baseUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getGatewayRoute(String regionName, String enterpriseId,
                                                 String tenantName, String routeName) {
        String url = baseUrl(tenantName) + "/" + enc(routeName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> addGatewayRoute(String regionName, String enterpriseId,
                                                 String tenantName, Map<String, Object> body) {
        String url = baseUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> updateGatewayRoute(String regionName, String enterpriseId,
                                                    String tenantName, String routeName,
                                                    Map<String, Object> body) {
        String url = baseUrl(tenantName) + "/" + enc(routeName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public void deleteGatewayRoute(String regionName, String enterpriseId,
                                    String tenantName, String routeName) {
        String url = baseUrl(tenantName) + "/" + enc(routeName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "DELETE", url,
                c -> c.method(HttpMethod.DELETE).uri(url)
                        .exchange((req, r) -> readString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    // ─── 工具 ─────────────────────────────────────────────────────────────────

    private static String baseUrl(String tenantName) {
        return "/v2/proxy-pass/gateway/" + enc(tenantName) + "/" + KIND;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, Object> safe(Map<String, Object> body) {
        return body == null ? Map.of() : body;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static ResponseEntity<String> readString(org.springframework.http.client.ClientHttpResponse r) {
        try {
            return ResponseEntity.status(r.getStatusCode())
                    .headers(r.getHeaders())
                    .body(new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseEntity<String> exchange(String regionName, String enterpriseId,
                                             String method, String url,
                                             Function<RestClient, ResponseEntity<String>> caller) {
        RegionClient regionClient = clientFactory.getClient(regionName, enterpriseId);
        try {
            return caller.apply(regionClient.restClient());
        } catch (ResourceAccessException socketErr) {
            try {
                return caller.apply(regionClient.restClient());
            } catch (ResourceAccessException retryErr) {
                throw new RegionApiSocketException(API_TYPE, url, method, retryErr);
            }
        }
    }
}
