package cn.kuship.console.infrastructure.region.api;

import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.MapType;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

/**
 * {@link GatewayOperations} 实现。当前仅落地 {@code apiGatewayProxy}（add-gray-release 用），
 * 其余 method 仍走 interface default {@code unsupported}。
 */
@Service
public class GatewayOperationsImpl implements GatewayOperations {

    private static final String API_TYPE = "gateway";

    private final RegionClientFactory clientFactory;
    private final ObjectMapper json;
    private final MapType mapType;

    public GatewayOperationsImpl(RegionClientFactory clientFactory, ObjectMapper json) {
        this.clientFactory = clientFactory;
        this.json = json;
        this.mapType = json.getTypeFactory()
                .constructMapType(java.util.LinkedHashMap.class, String.class, Object.class);
    }

    @Override
    public Map<String, Object> apiGatewayProxy(String regionName, String enterpriseId,
                                                  String tenantName, String path,
                                                  Map<String, Object> body) {
        ResponseEntity<String> resp = exchangeWithRetry(regionName, enterpriseId, "POST", path,
                client -> client.post().uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RegionApiException(resp.getStatusCode().value(),
                    "api-gateway proxy failed: " + truncate(resp.getBody(), 256),
                    "apisix-route 更新失败");
        }
        String text = resp.getBody();
        if (text == null || text.isBlank()) return Map.of();
        return json.readValue(text, mapType);
    }

    private ResponseEntity<String> exchangeWithRetry(String regionName, String enterpriseId,
                                                      String httpMethod, String url,
                                                      Function<RestClient, ResponseEntity<String>> caller) {
        RegionClient regionClient = clientFactory.getClient(regionName, enterpriseId);
        try {
            return caller.apply(regionClient.restClient());
        } catch (ResourceAccessException socketErr) {
            try {
                return caller.apply(regionClient.restClient());
            } catch (ResourceAccessException retryErr) {
                throw new RegionApiSocketException(API_TYPE, url, httpMethod, retryErr);
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
