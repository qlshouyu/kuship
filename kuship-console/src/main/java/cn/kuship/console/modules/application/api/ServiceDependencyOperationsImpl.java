package cn.kuship.console.modules.application.api;

import cn.kuship.console.infrastructure.region.api.ServiceDependencyOperations;
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
public class ServiceDependencyOperationsImpl implements ServiceDependencyOperations {

    private static final String API_TYPE = "service_dependency";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ServiceDependencyOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> addDependency(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/dependency";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, Object> bean = (Map) processor.extractBean(resp, Map.class, API_TYPE, url, "POST");
        return bean != null ? bean : Map.of();
    }

    @Override
    public void deleteDependency(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/dependency";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.method(org.springframework.http.HttpMethod.DELETE).uri(url)
                        .contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
