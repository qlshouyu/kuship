package cn.kuship.console.modules.application.governance.api;

import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.appmarket.api.RegionApiSupport;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@Primary
public class GovernanceModeOperationsImpl implements GovernanceModeOperations {

    private static final String API_TYPE = "governance-mode";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public GovernanceModeOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Map<String, Object>> listGovernanceMode(String regionName, String tenantName) {
        String url = "/v2/cluster/governance-mode";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        Object bean = processor.extractBean(resp, Map.class, API_TYPE, url, "GET");
        if (bean instanceof Map<?, ?> m) {
            Object list = m.get("list");
            if (list instanceof List l) {
                return l;
            }
        }
        return List.of();
    }

    @Override
    public Map<String, Object> checkAppGovernanceMode(String regionName, String tenantName,
                                                      String regionAppId, String governanceMode) {
        String url = "/v2/tenants/" + encode(tenantName) + "/apps/" + encode(regionAppId)
                + "/governance/check?governance_mode=" + encode(governanceMode);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> createGovernanceCr(String regionName, String tenantName,
                                                   String regionAppId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/apps/" + encode(regionAppId) + "/governance-cr";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> updateGovernanceCr(String regionName, String tenantName,
                                                   String regionAppId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/apps/" + encode(regionAppId) + "/governance-cr";
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public Map<String, Object> deleteGovernanceCr(String regionName, String tenantName, String regionAppId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/apps/" + encode(regionAppId) + "/governance-cr";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.method(HttpMethod.DELETE).uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "DELETE"));
    }

    private Map<String, Object> post(String regionName, String url, Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }
}
