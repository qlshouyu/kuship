package cn.kuship.console.modules.region.api;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import cn.kuship.console.infrastructure.region.api.dto.ClusterIdResp;
import cn.kuship.console.infrastructure.region.api.dto.LicenseStatusResp;
import cn.kuship.console.infrastructure.region.api.dto.NamespaceListResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionFeaturesResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionResourceResp;
import cn.kuship.console.infrastructure.region.api.dto.TenantLimitReq;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
    private final TenantsRepository tenantsRepository;
    private final RegionInfoEntityRepository regionInfoRepository;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public ClusterOperationsImpl(RegionClientFactory clientFactory,
                                  RegionApiResponseProcessor processor,
                                  TenantsRepository tenantsRepository,
                                  RegionInfoEntityRepository regionInfoRepository,
                                  tools.jackson.databind.ObjectMapper objectMapper) {
        this.clientFactory = clientFactory;
        this.processor = processor;
        this.tenantsRepository = tenantsRepository;
        this.regionInfoRepository = regionInfoRepository;
        this.objectMapper = objectMapper;
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

    // ---- migrate-console-cluster-extras 落地的 5 method ----

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> getResources(String regionName, String tenantName, String enterpriseId) {
        Tenants tenant = tenantsRepository.findByTenantName(tenantName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        String namespace = tenant.getNamespace() != null && !tenant.getNamespace().isBlank()
                ? tenant.getNamespace()
                : tenant.getTenantName();
        String url = "/v2/tenants/" + encode(namespace) + "/resources";
        String fullUrl = url + (enterpriseId != null && !enterpriseId.isBlank()
                ? "?enterprise_id=" + encode(enterpriseId)
                : "");
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", fullUrl,
                c -> c.get().uri(fullUrl).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                        .headers(resp2.getHeaders())
                        .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        Map result = processor.extractBean(resp, Map.class, API_TYPE, fullUrl, "GET");
        return result == null ? Map.of() : (Map<String, Object>) result;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> getClusterInfo(String regionName) {
        String url = "/v2/cluster/info";
        try {
            ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                    c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                            .headers(resp2.getHeaders())
                            .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
            Map result = processor.extractBean(resp, Map.class, API_TYPE, url, "GET");
            return result == null ? Map.of() : (Map<String, Object>) result;
        } catch (RegionApiException e) {
            // region 端不支持 /v2/cluster/info → 降级为读本地 region_info entity
            if (e.getHttpStatus() == 404) {
                return localRegionInfoFallback(regionName);
            }
            throw e;
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> getClusterEvents(String regionName, Map<String, Object> body) {
        String queryString = buildQueryString(body);
        String url = "/v2/cluster/events" + (queryString.isEmpty() ? "" : "?" + queryString);
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                        .headers(resp2.getHeaders())
                        .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        tools.jackson.databind.JsonNode root = processor.checkStatus(resp, API_TYPE, url, "GET");
        // 响应 shape：可能是 data.bean / data.list / data；统一返 Map 透传给 controller
        tools.jackson.databind.JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return Map.of();
        }
        Map result = objectMapper.convertValue(data, Map.class);
        return result == null ? Map.of() : (Map<String, Object>) result;
    }

    /** body map → URL query string；按 key 字典序排序，全部 URL encode；空值 / null value 跳过。 */
    private static String buildQueryString(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : new TreeMap<>(body).entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encode(e.getKey())).append('=').append(encode(String.valueOf(e.getValue())));
        }
        return sb.toString();
    }

    /** /v2/cluster/info 不存在时降级为读本地 region_info entity。 */
    private Map<String, Object> localRegionInfoFallback(String regionName) {
        RegionInfo ri = regionInfoRepository.findByRegionName(regionName)
                .orElseThrow(() -> new ServiceHandleException(404, "region not found", "集群不存在"));
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("region_name", ri.getRegionName());
        bean.put("region_alias", ri.getRegionAlias());
        bean.put("region_type", ri.getRegionType());
        bean.put("url", ri.getUrl());
        bean.put("wsurl", ri.getWsurl());
        bean.put("tcpdomain", ri.getTcpdomain());
        bean.put("httpdomain", ri.getHttpdomain());
        bean.put("status", ri.getStatus());
        bean.put("scope", ri.getScope());
        bean.put("provider", ri.getProvider());
        return bean;
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

    // ---- migrate-console-cluster-nodes：7 个节点管理 method ----

    @Override
    public Map<String, Object> getClusterNodes(String regionName, String enterpriseId) {
        String url = "/v2/cluster/nodes";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                        .headers(resp2.getHeaders())
                        .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        tools.jackson.databind.JsonNode body = processor.checkStatus(resp, API_TYPE, url, "GET");
        // 返回包含 list 节点的原始数据 map，供 service 层解析
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        tools.jackson.databind.JsonNode listNode = body.path("list");
        if (listNode.isMissingNode() || listNode.isNull()) {
            listNode = body.path("data").path("list");
        }
        result.put("list", listNode);
        return result;
    }

    @Override
    public Map<String, Object> getNodeDetail(String regionName, String enterpriseId, String nodeName) {
        String url = "/v2/cluster/nodes/" + encode(nodeName) + "/detail";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                        .headers(resp2.getHeaders())
                        .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        tools.jackson.databind.JsonNode body = processor.checkStatus(resp, API_TYPE, url, "GET");
        // 返回 bean 节点原始数据
        tools.jackson.databind.JsonNode beanNode = body.path("bean");
        if (beanNode.isMissingNode() || beanNode.isNull()) {
            beanNode = body.path("data").path("bean");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> beanMap = objectMapper.treeToValue(beanNode, Map.class);
            return beanMap != null ? beanMap : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> operateNodeAction(String regionName, String enterpriseId, String nodeName, String action) {
        String url = "/v2/cluster/nodes/" + encode(nodeName) + "/action/" + encode(action);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        tools.jackson.databind.JsonNode body = processor.checkStatus(resp, API_TYPE, url, "POST");
        tools.jackson.databind.JsonNode beanNode = body.path("bean");
        if (beanNode.isMissingNode() || beanNode.isNull()) {
            beanNode = body.path("data").path("bean");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> beanMap = objectMapper.treeToValue(beanNode, Map.class);
            return beanMap != null ? beanMap : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> getNodeLabels(String regionName, String enterpriseId, String nodeName) {
        String url = "/v2/cluster/nodes/" + encode(nodeName) + "/labels";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                        .headers(resp2.getHeaders())
                        .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        tools.jackson.databind.JsonNode body = processor.checkStatus(resp, API_TYPE, url, "GET");
        tools.jackson.databind.JsonNode beanNode = body.path("bean");
        if (beanNode.isMissingNode() || beanNode.isNull()) {
            beanNode = body.path("data").path("bean");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> beanMap = objectMapper.treeToValue(beanNode, Map.class);
            return beanMap != null ? beanMap : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> updateNodeLabels(String regionName, String enterpriseId, String nodeName,
                                                  Map<String, Object> labels) {
        String url = "/v2/cluster/nodes/" + encode(nodeName) + "/labels";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(labels)
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        tools.jackson.databind.JsonNode body = processor.checkStatus(resp, API_TYPE, url, "PUT");
        tools.jackson.databind.JsonNode beanNode = body.path("bean");
        if (beanNode.isMissingNode() || beanNode.isNull()) {
            beanNode = body.path("data").path("bean");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> beanMap = objectMapper.treeToValue(beanNode, Map.class);
            return beanMap != null ? beanMap : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public List<Object> getNodeTaints(String regionName, String enterpriseId, String nodeName) {
        String url = "/v2/cluster/nodes/" + encode(nodeName) + "/taints";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                        .headers(resp2.getHeaders())
                        .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        tools.jackson.databind.JsonNode body = processor.checkStatus(resp, API_TYPE, url, "GET");
        tools.jackson.databind.JsonNode listNode = body.path("list");
        if (listNode.isMissingNode() || listNode.isNull()) {
            listNode = body.path("data").path("list");
        }
        try {
            @SuppressWarnings("unchecked")
            List<Object> list = objectMapper.treeToValue(listNode, List.class);
            return list != null ? list : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<Object> updateNodeTaints(String regionName, String enterpriseId, String nodeName,
                                           List<Object> taints) {
        String url = "/v2/cluster/nodes/" + encode(nodeName) + "/taints";
        Map<String, Object> body2 = Map.of("taints", taints);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(body2)
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), StandardCharsets.UTF_8))));
        tools.jackson.databind.JsonNode body = processor.checkStatus(resp, API_TYPE, url, "PUT");
        tools.jackson.databind.JsonNode listNode = body.path("list");
        if (listNode.isMissingNode() || listNode.isNull()) {
            listNode = body.path("data").path("list");
        }
        try {
            @SuppressWarnings("unchecked")
            List<Object> list = objectMapper.treeToValue(listNode, List.class);
            return list != null ? list : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
