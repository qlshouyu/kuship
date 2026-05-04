package cn.kuship.console.modules.plugin.api;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Primary
public class RainbondPluginOperationsImpl implements RainbondPluginOperations {

    private static final String API_TYPE = "rainbond_plugin";
    private static final long MAX_STATIC_BYTES = 10L * 1024 * 1024;

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public RainbondPluginOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override public Map<String, Object> listPlugins(String regionName) { return getJson(regionName, "/v2/rbd-plugins"); }
    @Override public Map<String, Object> listPlatformPlugins(String regionName) { return getJson(regionName, "/v2/rbd-platform-plugins"); }
    @Override public Map<String, Object> listOfficialPlugins(String regionName) { return getJson(regionName, "/v2/rbd-official-plugins"); }
    @Override public Map<String, Object> listObservablePlugins(String regionName) { return getJson(regionName, "/v2/rbd-observable-plugins"); }

    @Override
    public Map<String, Object> installPlatformPlugin(String regionName, String pluginId, Map<String, Object> body) {
        String url = "/v2/rbd-platform-plugins/" + encode(pluginId) + "/install";
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> getPluginStatus(String regionName, String pluginName) {
        return getJson(regionName, "/v2/rbd-plugins/" + encode(pluginName) + "/status");
    }

    @Override
    public ResponseEntity<byte[]> proxyStaticResource(String regionName, String pluginName) {
        String url = "/static/plugins/" + encode(pluginName);
        return proxyRaw(regionName, url, "GET", null, null);
    }

    @Override
    public ResponseEntity<byte[]> proxyBackend(String regionName, String pluginName, String filePath,
                                                  String httpMethod, byte[] body, String contentType) {
        String url = "/backend/plugins/" + encode(pluginName) + "/" + filePath;
        return proxyRaw(regionName, url, httpMethod, body, contentType);
    }

    private Map<String, Object> getJson(String regionName, String url) {
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    private ResponseEntity<byte[]> proxyRaw(String regionName, String url, String httpMethod,
                                                byte[] body, String contentType) {
        RegionClient regionClient = clientFactory.getClient(regionName, "");
        try {
            HttpMethod method = HttpMethod.valueOf(httpMethod == null ? "GET" : httpMethod.toUpperCase());
            var spec = regionClient.restClient().method(method).uri(url);
            if (body != null && body.length > 0) {
                if (contentType != null) {
                    spec = spec.contentType(MediaType.parseMediaType(contentType));
                }
                spec = spec.body(body);
            }
            ResponseEntity<byte[]> resp = spec.retrieve().toEntity(byte[].class);
            byte[] payload = resp.getBody();
            if (payload != null && payload.length > MAX_STATIC_BYTES) {
                throw new ServiceHandleException(413, "plugin static too large", "插件静态资源过大（>10MB）");
            }
            HttpHeaders headers = new HttpHeaders();
            MediaType mt = resp.getHeaders().getContentType();
            if (mt != null) headers.setContentType(mt);
            return new ResponseEntity<>(payload == null ? new byte[0] : payload, headers, resp.getStatusCode());
        } catch (ResourceAccessException socketErr) {
            throw new RegionApiSocketException(API_TYPE, url, httpMethod, socketErr);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
