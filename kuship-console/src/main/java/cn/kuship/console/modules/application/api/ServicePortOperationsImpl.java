package cn.kuship.console.modules.application.api;

import cn.kuship.console.infrastructure.region.api.ServicePortOperations;
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
public class ServicePortOperationsImpl implements ServicePortOperations {

    private static final String API_TYPE = "service_port";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ServicePortOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> addPort(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/ports";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> updatePort(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/ports";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public void deletePort(String regionName, String tenantName, String serviceAlias, int port,
                            String enterpriseId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/ports/" + port;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "DELETE",
                c -> c.delete().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    @Override
    public Map<String, Object> manageInnerPort(String regionName, String tenantName, String serviceAlias, int port, Map<String, Object> body) {
        return managePort(regionName, tenantName, serviceAlias, port, body, "inner");
    }

    @Override
    public Map<String, Object> manageOuterPort(String regionName, String tenantName, String serviceAlias, int port, Map<String, Object> body) {
        return managePort(regionName, tenantName, serviceAlias, port, body, "outer");
    }

    private Map<String, Object> managePort(String regionName, String tenantName, String serviceAlias,
                                            int port, Map<String, Object> body, String scope) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias)
                + "/ports/" + port + "/" + scope;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
