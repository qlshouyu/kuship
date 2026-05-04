package cn.kuship.console.modules.appruntime.api;

import cn.kuship.console.infrastructure.region.api.ServiceStatusOperations;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** {@link ServiceStatusOperations} 实现：状态 / Pod / 异常状态查询。 */
@Service
@Primary
public class ServiceStatusOperationsImpl implements ServiceStatusOperations {

    private static final String API_TYPE = "service_status";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ServiceStatusOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> serviceStatus(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services_status";
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safeBody)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> checkServiceStatus(String regionName, String tenantName, String serviceAlias) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/status";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getServicePods(String regionName, String tenantName, String serviceAlias, String enterpriseId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/pods";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> podDetail(String regionName, String tenantName, String serviceAlias, String podName) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/pods/" + encode(podName) + "/detail";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getDynamicServicesPods(String regionName, String tenantName, String serviceIds) {
        String url = "/v2/tenants/" + encode(tenantName) + "/pods?service_ids=" + serviceIds;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getUserServiceAbnormalStatus(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/abnormal_status";
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safeBody)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
