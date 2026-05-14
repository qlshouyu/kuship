package cn.kuship.console.modules.appmarket.backup.api;

import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.appmarket.api.RegionApiSupport;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
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

    // ===== 既有 method（URL 修正：与 rainbond region `/groupapp/backups` 真实路径对齐） =====

    @Override
    public Map<String, Object> backup(String regionName, String tenantName, String groupId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/backups";
        Map<String, Object> safe = new HashMap<>(body == null ? Map.of() : body);
        if (groupId != null && !safe.containsKey("group_id")) {
            safe.put("group_id", groupId);
        }
        return post(regionName, url, safe);
    }

    @Override
    public Map<String, Object> backupStatus(String regionName, String tenantName, String backupId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/backups/" + encode(backupId);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    @Deprecated
    public Map<String, Object> restore(String regionName, String tenantName, Map<String, Object> body) {
        throw new UnsupportedOperationException(
                "BackupOperations.restore() deprecated; use startMigrate(regionName, tenantName, backupId, body) instead");
    }

    @Override
    @Deprecated
    public Map<String, Object> export(String regionName, String tenantName, String backupId) {
        throw new UnsupportedOperationException(
                "BackupOperations.export() deprecated; export logic should serialize local ServiceGroupBackup, not call region");
    }

    // ===== 新增 5 method =====

    @Override
    public Map<String, Object> deleteBackup(String regionName, String tenantName, String backupId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/backups/" + encode(backupId);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.method(HttpMethod.DELETE).uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "DELETE"));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Map<String, Object>> listBackupsByGroupUuid(String regionName, String tenantName, String groupUuid) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/backups?group_id=" + encode(groupUuid);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        Object bean = processor.extractBean(resp, Map.class, API_TYPE, url, "GET");
        if (bean instanceof Map<?, ?> m) {
            Object list = m.get("list");
            if (list instanceof List l) {
                return l;
            }
        }
        return List.of();
    }

    @Override
    public Map<String, Object> startMigrate(String regionName, String tenantName, String backupId,
                                             Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/backups/" + encode(backupId) + "/restore";
        return post(regionName, url, body);
    }

    @Override
    public Map<String, Object> getMigrateStatus(String regionName, String tenantName, String backupId, String restoreId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/backups/" + encode(backupId)
                + "/restore/" + encode(restoreId);
        try {
            ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                    c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
            return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
        } catch (RegionApiException e) {
            if (e.getHttpStatus() == 404) {
                return Map.of("status", "not_found");
            }
            throw e;
        }
    }

    @Override
    public Map<String, Object> copyBackupData(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/groupapp/backupcopy";
        return post(regionName, url, body);
    }

    // ===== helpers =====

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
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
