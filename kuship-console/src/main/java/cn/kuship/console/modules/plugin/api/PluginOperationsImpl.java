package cn.kuship.console.modules.plugin.api;

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
public class PluginOperationsImpl implements PluginOperations {

    private static final String API_TYPE = "plugin";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public PluginOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> createPlugin(String regionName, String tenantName, Map<String, Object> body) {
        return post(regionName, "/v2/tenants/" + encode(tenantName) + "/plugin", body);
    }

    @Override
    public Map<String, Object> updatePlugin(String regionName, String tenantName, String pluginId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/plugin/" + encode(pluginId);
        return put(regionName, url, body);
    }

    @Override
    public void deletePlugin(String regionName, String tenantName, String pluginId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/plugin/" + encode(pluginId);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.delete().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    @Override
    public Map<String, Object> buildPlugin(String regionName, String tenantName, String pluginId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/plugin/" + encode(pluginId) + "/build";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> getPluginBuildStatus(String regionName, String tenantName, String pluginId, String buildVersion) {
        String url = "/v2/tenants/" + encode(tenantName) + "/plugin/" + encode(pluginId) + "/build-version/" + encode(buildVersion);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> installToService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/plugin";
        return post(regionName, url, body);
    }

    @Override
    public void uninstallFromService(String regionName, String tenantName, String serviceAlias, String pluginId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/plugin/" + encode(pluginId);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.delete().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    @Override
    public Map<String, Object> openOnService(String regionName, String tenantName, String serviceAlias, String pluginId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/plugin/" + encode(pluginId) + "/open";
        return put(regionName, url, body);
    }

    @Override
    public Map<String, Object> syncFromMarket(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/plugin/sync";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> installFromMarket(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/plugin/install";
        return post(regionName, url, body);
    }

    private Map<String, Object> post(String regionName, String url, Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    private Map<String, Object> put(String regionName, String url, Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
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
