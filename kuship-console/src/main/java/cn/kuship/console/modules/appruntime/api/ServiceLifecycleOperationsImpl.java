package cn.kuship.console.modules.appruntime.api;

import cn.kuship.console.infrastructure.region.api.ServiceLifecycleOperations;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** {@link ServiceLifecycleOperations} 实现：组件启停 / 部署 / 升级 / 回滚 / 暂停 / 扩缩容。 */
@Service
@Primary
public class ServiceLifecycleOperationsImpl implements ServiceLifecycleOperations {

    private static final String API_TYPE = "service_lifecycle";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ServiceLifecycleOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override public Map<String, Object> startService(String r, String t, String s, Map<String, Object> b) { return post(r, t, s, b, "start"); }
    @Override public Map<String, Object> stopService(String r, String t, String s, Map<String, Object> b) { return post(r, t, s, b, "stop"); }
    @Override public Map<String, Object> restartService(String r, String t, String s, Map<String, Object> b) { return post(r, t, s, b, "restart"); }
    @Override public Map<String, Object> upgradeService(String r, String t, String s, Map<String, Object> b) { return post(r, t, s, b, "upgrade"); }
    @Override public Map<String, Object> rollback(String r, String t, String s, Map<String, Object> b) { return post(r, t, s, b, "rollback"); }
    @Override public Map<String, Object> horizontalUpgrade(String r, String t, String s, Map<String, Object> b) { return post(r, t, s, b, "horizontal"); }
    @Override public Map<String, Object> verticalUpgrade(String r, String t, String s, Map<String, Object> b) { return post(r, t, s, b, "vertical"); }
    @Override public Map<String, Object> changeMemory(String r, String t, String s, Map<String, Object> b) { return put(r, t, s, b, "deploytype"); }
    @Override public Map<String, Object> pauseService(String r, String t, String s, Map<String, Object> b) { return post(r, t, s, b, "pause"); }
    @Override public Map<String, Object> unpauseService(String r, String t, String s, Map<String, Object> b) { return post(r, t, s, b, "un_pause"); }

    private Map<String, Object> post(String regionName, String tenantName, String serviceAlias, Map<String, Object> body, String action) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/" + action;
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safeBody)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    private Map<String, Object> put(String regionName, String tenantName, String serviceAlias, Map<String, Object> body, String action) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/" + action;
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(safeBody)
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
