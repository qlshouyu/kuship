package cn.kuship.console.modules.appmarket.share.upload.api;

import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.appmarket.api.RegionApiSupport;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Primary
public class LoadTarImageOperationsImpl implements LoadTarImageOperations {

    private static final String API_TYPE = "app-upload";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public LoadTarImageOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> loadTarImage(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/app/load_tar_image";
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        Object bean = processor.extractBean(resp, Map.class, API_TYPE, url, "POST");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, Object> result = bean == null ? Map.of() : (Map) bean;
        return result;
    }
}
