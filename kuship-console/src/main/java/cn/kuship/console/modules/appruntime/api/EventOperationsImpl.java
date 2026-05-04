package cn.kuship.console.modules.appruntime.api;

import cn.kuship.console.infrastructure.region.api.EventOperations;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** {@link EventOperations} 实现：事件列表 / 单事件日志 / 团队级事件聚合。 */
@Service
@Primary
public class EventOperationsImpl implements EventOperations {

    private static final String API_TYPE = "event";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public EventOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> getEventLog(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/event_log";
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safeBody)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> getTargetEventsList(String regionName, String tenantName, Map<String, Object> body) {
        StringBuilder qs = new StringBuilder();
        if (body != null) {
            for (Map.Entry<String, Object> e : body.entrySet()) {
                qs.append(qs.length() == 0 ? "?" : "&");
                qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append("=").append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
            }
        }
        String url = "/v2/tenants/" + encode(tenantName) + "/events" + qs;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getMyteamsEventsList(String regionName, String tenantName, Map<String, Object> body) {
        StringBuilder qs = new StringBuilder();
        if (body != null) {
            for (Map.Entry<String, Object> e : body.entrySet()) {
                qs.append(qs.length() == 0 ? "?" : "&");
                qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append("=").append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
            }
        }
        String url = "/v2/tenants/" + encode(tenantName) + "/myteams_events" + qs;
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
