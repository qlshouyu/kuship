package cn.kuship.console.modules.application.k8sattr.api;

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
public class K8sAttributeOperationsImpl implements K8sAttributeOperations {

    private static final String API_TYPE = "k8s-attribute";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public K8sAttributeOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Map<String, Object>> getK8sAttribute(String regionName, String tenantName,
                                                     String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/k8s-attributes";
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.method(HttpMethod.GET).uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
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
    public Map<String, Object> createK8sAttribute(String regionName, String tenantName,
                                                   String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/k8s-attributes";
        return exchangeWithBody(regionName, url, body, "POST");
    }

    @Override
    public Map<String, Object> updateK8sAttribute(String regionName, String tenantName,
                                                   String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/k8s-attributes";
        return exchangeWithBody(regionName, url, body, "PUT");
    }

    @Override
    public Map<String, Object> deleteK8sAttribute(String regionName, String tenantName,
                                                   String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/k8s-attributes";
        return exchangeWithBody(regionName, url, body, "DELETE");
    }

    private Map<String, Object> exchangeWithBody(String regionName, String url,
                                                  Map<String, Object> body, String httpMethod) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        HttpMethod m = HttpMethod.valueOf(httpMethod);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, httpMethod,
                c -> c.method(m).uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, httpMethod));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }
}
