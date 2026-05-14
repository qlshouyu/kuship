package cn.kuship.console.modules.appmarket.share.import_.api;

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
public class AppImportOperationsImpl implements AppImportOperations {

    private static final String API_TYPE = "app-import";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public AppImportOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> importApp2Enterprise(String regionName, String enterpriseId, Map<String, Object> body) {
        return post(regionName, enterpriseId, "/v2/app/import", body);
    }

    @Override
    public Map<String, Object> importApp(String regionName, String tenantName, Map<String, Object> body) {
        return post(regionName, "", "/v2/app/import", body);
    }

    @Override
    public Map<String, Object> getImportStatus(String regionName, String tenantName, String eventId) {
        String url = "/v2/app/import/" + encode(eventId);
        return get(regionName, "", url);
    }

    @Override
    public Map<String, Object> getEnterpriseImportStatus(String regionName, String enterpriseId, String eventId) {
        String url = "/v2/app/import/ids/" + encode(eventId);
        return get(regionName, enterpriseId, url);
    }

    @Override
    public Map<String, Object> getEnterpriseImportFileDir(String regionName, String enterpriseId, String eventId) {
        // rainbond region 端 GET /v2/app/import/ids/{event_id} 区分 status 与 file dir 用 body 标识；本实现仅透传，差异由 region 端语义处理
        String url = "/v2/app/import/ids/" + encode(eventId) + "/dir";
        return get(regionName, enterpriseId, url);
    }

    @Override
    public Map<String, Object> getImportFileDir(String regionName, String tenantName, String eventId) {
        String url = "/v2/app/import/" + encode(eventId) + "/dir";
        return get(regionName, "", url);
    }

    @Override
    public Map<String, Object> deleteEnterpriseImport(String regionName, String enterpriseId, String eventId) {
        return delete(regionName, enterpriseId, "/v2/app/import/ids/" + encode(eventId));
    }

    @Override
    public Map<String, Object> deleteImport(String regionName, String tenantName, String eventId) {
        return delete(regionName, "", "/v2/app/import/" + encode(eventId));
    }

    @Override
    public Map<String, Object> createImportFileDir(String regionName, String tenantName, String eventId) {
        return post(regionName, "", "/v2/app/import/" + encode(eventId) + "/dir", Map.of());
    }

    @Override
    public Map<String, Object> deleteImportFileDir(String regionName, String scopeName, String eventId, boolean enterprise) {
        String url = enterprise
                ? "/v2/app/import/ids/" + encode(eventId) + "/dir"
                : "/v2/app/import/" + encode(eventId) + "/dir";
        return delete(regionName, enterprise ? scopeName : "", url);
    }

    private Map<String, Object> post(String regionName, String enterpriseId, String url, Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    private Map<String, Object> get(String regionName, String enterpriseId, String url) {
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    private Map<String, Object> delete(String regionName, String enterpriseId, String url) {
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "DELETE",
                c -> c.method(HttpMethod.DELETE).uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "DELETE"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }
}
