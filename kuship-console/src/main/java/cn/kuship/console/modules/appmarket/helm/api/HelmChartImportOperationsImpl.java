package cn.kuship.console.modules.appmarket.helm.api;

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
public class HelmChartImportOperationsImpl implements HelmChartImportOperations {

    private static final String API_TYPE = "helm-chart-import";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public HelmChartImportOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> importUploadChartResource(String regionName, Map<String, Object> body) {
        String url = "/v2/helm/import_upload_chart_resource";
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
