package cn.kuship.console.modules.appmarket.helm.api;

import cn.kuship.console.infrastructure.region.api.HelmOperations;
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

/** {@link HelmOperations} 完整实现：Helm chart 信息查询 / 检查 / values 拉取 / 上传资源导入。 */
@Service
@Primary
public class HelmOperationsImpl implements HelmOperations {

    private static final String API_TYPE = "helm";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public HelmOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> getChartInformation(String regionName, Map<String, Object> body) {
        String url = "/v2/helm/chart/information";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> checkHelmApp(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/check";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> getYamlByChart(String regionName, Map<String, Object> body) {
        String url = "/v2/helm/chart/yaml";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> getUploadChartInformation(String regionName, Map<String, Object> body) {
        String url = "/v2/helm/upload/chart/information";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> getUploadChartValue(String regionName, Map<String, Object> body) {
        String url = "/v2/helm/upload/chart/value";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> importUploadChartResource(String regionName, Map<String, Object> body) {
        String url = "/v2/helm/upload/chart/resource";
        return post(regionName, url, body);
    }

    private Map<String, Object> post(String regionName, String url, Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
