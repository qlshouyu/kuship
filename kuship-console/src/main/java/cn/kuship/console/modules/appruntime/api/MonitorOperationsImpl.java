package cn.kuship.console.modules.appruntime.api;

import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Primary
public class MonitorOperationsImpl implements MonitorOperations {

    private static final String API_TYPE = "monitor";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public MonitorOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> query(String regionName, String tenantName, Map<String, String> queryParams) {
        return passthrough(regionName, tenantName, queryParams, "query");
    }

    @Override
    public Map<String, Object> queryRange(String regionName, String tenantName, Map<String, String> queryParams) {
        return passthrough(regionName, tenantName, queryParams, "query_range");
    }

    @Override
    public Map<String, Object> batchQuery(String regionName, String tenantName, Map<String, String> queryParams) {
        return passthrough(regionName, tenantName, queryParams, "batch_query");
    }

    @Override
    public Map<String, Object> getServiceResources(String regionName, String tenantName, String serviceAlias) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/resources";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getMonitorMetrics(String regionName, String tenantId,
                                                   String target, String appId, String componentId) {
        StringBuilder qs = new StringBuilder();
        qs.append("?target=").append(encode(safe(target)));
        qs.append("&tenant=").append(encode(safe(tenantId)));
        qs.append("&app=").append(encode(safe(appId)));
        qs.append("&component=").append(encode(safe(componentId)));
        String url = "/v2/monitor/metrics" + qs;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getResourceCenterEvents(String regionName, String tenantName,
                                                         Map<String, String> queryParams) {
        String url = "/v2/tenants/" + encode(tenantName) + "/resource-center/events"
                + buildSortedQuery(queryParams);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> queryDomainAccess(String regionName, String tenantName,
                                                   Map<String, String> queryParams) {
        String url = "/api/v1/query" + buildSortedQuery(queryParams);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> queryServiceAccess(String regionName, String tenantName,
                                                    Map<String, String> queryParams) {
        String url = "/api/v1/query" + buildSortedQuery(queryParams);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    private static String buildSortedQuery(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) return "";
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>(queryParams);
        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            String v = e.getValue();
            if (v == null || v.isEmpty()) continue;
            qs.append(qs.length() == 0 ? "?" : "&");
            qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append("=").append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        }
        return qs.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private Map<String, Object> passthrough(String regionName, String tenantName, Map<String, String> queryParams, String suffix) {
        StringBuilder qs = new StringBuilder();
        if (queryParams != null) {
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                if (e.getValue() == null) continue;
                qs.append(qs.length() == 0 ? "?" : "&");
                qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append("=").append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
        }
        String url = "/v2/tenants/" + encode(tenantName) + "/monitor/" + suffix + qs;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
