package cn.kuship.console.modules.appmarket.share.export.api;

import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.appmarket.api.RegionApiSupport;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Primary
public class AppExportOperationsImpl implements AppExportOperations {

    private static final String API_TYPE = "app-export";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public AppExportOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> exportApp(String regionName, String enterpriseId, Map<String, Object> body) {
        String url = "/v2/app/export";
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId,
                API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> getExportStatus(String regionName, String enterpriseId, String eventId) {
        String url = "/v2/app/export/" + URLEncoder.encode(eventId == null ? "" : eventId, StandardCharsets.UTF_8);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId,
                API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }
}
