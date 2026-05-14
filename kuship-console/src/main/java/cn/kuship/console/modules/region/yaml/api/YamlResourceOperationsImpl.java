package cn.kuship.console.modules.region.yaml.api;

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
import java.util.Map;

@Service
@Primary
public class YamlResourceOperationsImpl implements YamlResourceOperations {

    private static final String API_TYPE = "yaml-resource";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public YamlResourceOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> yamlResourceName(String enterpriseId, String regionName, Map<String, Object> body) {
        String url = "/v2/cluster/yaml_resource_name?eid=" + encode(enterpriseId);
        return getWithBody(regionName, enterpriseId, url, body);
    }

    @Override
    public Map<String, Object> yamlResourceDetailed(String enterpriseId, String regionName, Map<String, Object> body) {
        String url = "/v2/cluster/yaml_resource_detailed?eid=" + encode(enterpriseId);
        return getWithBody(regionName, enterpriseId, url, body);
    }

    @Override
    public Map<String, Object> yamlResourceImport(String enterpriseId, String regionName, Map<String, Object> body) {
        String url = "/v2/cluster/yaml_resource_import?eid=" + encode(enterpriseId);
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    private Map<String, Object> getWithBody(String regionName, String enterpriseId, String url, Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "GET",
                c -> c.method(HttpMethod.GET).uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }
}
