package cn.kuship.console.modules.application.api;

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
public class BatchServiceOperationsImpl implements BatchServiceOperations {

    private static final String API_TYPE = "batch-service";
    private static final String RESOURCE_VALIDATION_HEADER = "Resource-Validation";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public BatchServiceOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> batchOperationService(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + URLEncoder.encode(tenantName, StandardCharsets.UTF_8) + "/batchoperation";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url)
                        .header(RESOURCE_VALIDATION_HEADER, "true")
                        .contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }
}
