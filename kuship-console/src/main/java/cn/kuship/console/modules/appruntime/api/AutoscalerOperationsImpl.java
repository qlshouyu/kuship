package cn.kuship.console.modules.appruntime.api;

import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Primary
public class AutoscalerOperationsImpl implements AutoscalerOperations {

    private static final String API_TYPE = "autoscaler";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public AutoscalerOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> createRule(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/xparules";
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safeBody)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> updateRule(String regionName, String tenantName, String serviceAlias, String ruleId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/xparules/" + encode(ruleId);
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(safeBody)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public void deleteRule(String regionName, String tenantName, String serviceAlias, String ruleId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/xparules/" + encode(ruleId);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.delete().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    @Override
    public Map<String, Object> listScalingRecords(String regionName, String tenantName, String serviceAlias, Map<String, String> queryParams) {
        StringBuilder qs = new StringBuilder();
        if (queryParams != null) {
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                if (e.getValue() == null) continue;
                qs.append(qs.length() == 0 ? "?" : "&");
                qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append("=").append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
        }
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/xparecords" + qs;
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
