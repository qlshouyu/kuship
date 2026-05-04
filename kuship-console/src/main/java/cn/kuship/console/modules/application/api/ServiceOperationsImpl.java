package cn.kuship.console.modules.application.api;

import cn.kuship.console.infrastructure.region.api.ServiceOperations;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@link ServiceOperations} 完整实现：
 *
 * <ul>
 *   <li>application-core change：getServiceInfo</li>
 *   <li>app-create change：createService / updateService / deleteService / buildService / codeCheck / getServiceLanguage</li>
 * </ul>
 */
@Service
@Primary
public class ServiceOperationsImpl implements ServiceOperations {

    private static final String API_TYPE = "service";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ServiceOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> getServiceInfo(String regionName, String tenantName, String serviceAlias) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> createService(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> updateService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public void deleteService(String regionName, String tenantName, String serviceAlias,
                                String enterpriseId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "DELETE",
                c -> c.method(org.springframework.http.HttpMethod.DELETE).uri(url)
                        .contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    @Override
    public Map<String, Object> buildService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/build";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> codeCheck(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/code-check";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> getServiceLanguage(String regionName, String serviceId, String tenantName) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceId) + "/language";
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
