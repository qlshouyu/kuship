package cn.kuship.console.modules.application.api;

import cn.kuship.console.infrastructure.region.api.ServiceProbeOperations;
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
public class ServiceProbeOperationsImpl implements ServiceProbeOperations {

    private static final String API_TYPE = "service_probe";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ServiceProbeOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> addProbe(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/probe";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, Object> bean = (Map) processor.extractBean(resp, Map.class, API_TYPE, url, "POST");
        return bean != null ? bean : Map.of();
    }

    @Override
    public void deleteProbe(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/probe";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.method(org.springframework.http.HttpMethod.DELETE).uri(url)
                        .contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    @Override
    public Map<String, Object> updateProbe(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/probe";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, Object> bean = (Map) processor.extractBean(resp, Map.class, API_TYPE, url, "PUT");
        return bean != null ? bean : Map.of();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
