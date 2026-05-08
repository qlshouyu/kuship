package cn.kuship.console.modules.region.api;

import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import cn.kuship.console.infrastructure.region.api.dto.ClusterIdResp;
import cn.kuship.console.infrastructure.region.api.dto.LicenseStatusResp;
import cn.kuship.console.infrastructure.region.api.dto.NamespaceListResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionFeaturesResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionResourceResp;
import cn.kuship.console.infrastructure.region.api.dto.TenantLimitReq;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@link ClusterOperations} 真实实现：转发 region API。
 *
 * <p>由 {@code @Primary} 覆盖原 default 占位 bean。
 */
@Service
@Primary
public class ClusterOperationsImpl implements ClusterOperations {

    private static final String API_TYPE = "cluster";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ClusterOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public ClusterIdResp getClusterId(String regionName, String enterpriseId) {
        String url = "/v2/cluster/cluster-id";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        return processor.extractBean(resp, ClusterIdResp.class, API_TYPE, url, "GET");
    }

    @Override
    public void activateLicense(String regionName, String enterpriseId, Map<String, Object> body) {
        String url = "/v2/cluster/license-activate";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        processor.checkStatus(resp, API_TYPE, url, "POST");
    }

    @Override
    public LicenseStatusResp getLicenseStatus(String regionName, String enterpriseId) {
        String url = "/v2/cluster/license-status";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        return processor.extractBean(resp, LicenseStatusResp.class, API_TYPE, url, "GET");
    }

    @Override
    public RegionFeaturesResp getRegionFeatures(String regionName, String tenantName) {
        // path 对齐 rainbond Python：regionapi.py:2431 url + "/license/features"
        // 响应 shape：rbd-api 返回顶层 list（rainbond services region_services.py:182 body["list"]），
        // 不是 data.bean，所以这里手动解析顶层 list 而非 extractBean。
        String url = "/license/features";
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        tools.jackson.databind.JsonNode body = processor.checkStatus(resp, API_TYPE, url, "GET");
        tools.jackson.databind.JsonNode listNode = body.path("list");
        if (listNode.isMissingNode() || listNode.isNull() || !listNode.isArray()) {
            // 兜底兼容 data.list 形态
            listNode = body.path("data").path("list");
        }
        List<String> features = new java.util.ArrayList<>();
        if (listNode.isArray()) {
            for (tools.jackson.databind.JsonNode n : listNode) {
                features.add(n.asText());
            }
        }
        return new RegionFeaturesResp(features, null, Map.of());
    }

    @Override
    public NamespaceListResp getRegionNamespaces(String regionName, String enterpriseId, String content) {
        String url = "/v2/cluster/namespaces";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(uri -> uri.path(url)
                                .queryParamIfPresent("content", java.util.Optional.ofNullable(content))
                                .build())
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        return processor.extractBean(resp, NamespaceListResp.class, API_TYPE, url, "GET");
    }

    @Override
    public RegionResourceResp getRegionResources(String regionName, String enterpriseId) {
        String url = "/v2/cluster/resource";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        return processor.extractBean(resp, RegionResourceResp.class, API_TYPE, url, "GET");
    }

    @Override
    public void setTenantLimit(String regionName, String enterpriseId, String tenantName, TenantLimitReq req) {
        String url = "/v2/cluster/tenants/" + encode(tenantName) + "/limit";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(req)
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        processor.checkStatus(resp, API_TYPE, url, "POST");
    }

    @Override
    public List<Map<String, Object>> listTenantsInRegion(String regionName, String enterpriseId) {
        String url = "/v2/cluster/tenants";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Map<String, Object>> out = (List) processor.extractList(resp, Map.class, API_TYPE, url, "GET");
        return out;
    }

    private ResponseEntity<String> exchange(String regionName, String enterpriseId,
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

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
