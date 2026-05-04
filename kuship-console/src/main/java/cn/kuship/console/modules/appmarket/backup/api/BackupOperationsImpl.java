package cn.kuship.console.modules.appmarket.backup.api;

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
public class BackupOperationsImpl implements BackupOperations {

    private static final String API_TYPE = "backup";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public BackupOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> backup(String regionName, String tenantName, String groupId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/" + encode(groupId) + "/backup";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> backupStatus(String regionName, String tenantName, String backupId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/backup/" + encode(backupId);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> restore(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/backup/restore";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> export(String regionName, String tenantName, String backupId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/backup/" + encode(backupId) + "/export";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
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
