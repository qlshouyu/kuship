package cn.kuship.console.modules.misc.kubeblocks.api;

import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@link KubeBlocksOperations} @Primary 实现：13 method 全部 1:1 透传 region API。
 *
 * <p>遵循 {@code modules/<domain>/api/} 业务自治接口约定：
 * {@code clientFactory.getClient(regionName, "")} 拿默认 mTLS 客户端，
 * 异常一律抛 {@link cn.kuship.console.infrastructure.region.exception.RegionApiException}
 * 由 {@code GlobalExceptionHandler} 自动映射为 general_message 形状。
 */
@Service
@Primary
public class KubeBlocksOperationsImpl implements KubeBlocksOperations {

    private static final String API_TYPE = "kubeblocks";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public KubeBlocksOperationsImpl(RegionClientFactory clientFactory,
                                     RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> listSupportedDatabases(String regionName) {
        String url = "/v2/cluster/kubeblocks/supported-databases";
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> listStorageClasses(String regionName) {
        String url = "/v2/cluster/kubeblocks/storage-classes";
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> listBackupRepos(String regionName) {
        String url = "/v2/cluster/kubeblocks/backup-repos";
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getClusterDetail(String regionName, String serviceId) {
        String url = clusterUrl(serviceId);
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> listClusterParameters(String regionName, String serviceId,
                                                       int page, int pageSize, String keyword) {
        StringBuilder qs = new StringBuilder();
        qs.append("?page=").append(page);
        qs.append("&page_size=").append(pageSize);
        if (StringUtils.hasText(keyword)) {
            qs.append("&keyword=").append(encode(keyword));
        }
        String url = clusterUrl(serviceId) + "/parameters" + qs;
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> listClusterBackups(String regionName, String serviceId,
                                                    int page, int pageSize) {
        String url = clusterUrl(serviceId) + "/backups?page=" + page + "&page_size=" + pageSize;
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getClusterPodDetail(String regionName, String serviceId, String podName) {
        String url = clusterUrl(serviceId) + "/pods/" + encode(podName) + "/details";
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> createCluster(String regionName, Map<String, Object> body) {
        String url = "/v2/cluster/kubeblocks/clusters";
        ResponseEntity<String> resp = exchange(regionName, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> expansionCluster(String regionName, String serviceId, Map<String, Object> body) {
        String url = clusterUrl(serviceId);
        ResponseEntity<String> resp = exchange(regionName, "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public Map<String, Object> deleteCluster(String regionName, Map<String, Object> body) {
        String url = "/v2/cluster/kubeblocks/clusters";
        ResponseEntity<String> resp = exchange(regionName, "DELETE", url,
                c -> c.method(HttpMethod.DELETE).uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "DELETE"));
    }

    @Override
    public Map<String, Object> deleteClusterBackups(String regionName, String serviceId, List<String> backups) {
        String url = clusterUrl(serviceId) + "/backups";
        Map<String, Object> body = Map.of("backups", backups == null ? List.of() : backups);
        ResponseEntity<String> resp = exchange(regionName, "DELETE", url,
                c -> c.method(HttpMethod.DELETE).uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "DELETE"));
    }

    @Override
    public Map<String, Object> updateBackupConfig(String regionName, String serviceId, Map<String, Object> body) {
        String url = clusterUrl(serviceId) + "/backup-schedules";
        ResponseEntity<String> resp = exchange(regionName, "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public Map<String, Object> createManualBackup(String regionName, String serviceId) {
        String url = clusterUrl(serviceId) + "/backups";
        ResponseEntity<String> resp = exchange(regionName, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of())
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> updateClusterParameters(String regionName, String serviceId, Map<String, Object> body) {
        String url = clusterUrl(serviceId) + "/parameters";
        ResponseEntity<String> resp = exchange(regionName, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    private static String clusterUrl(String serviceId) {
        return "/v2/cluster/kubeblocks/clusters/" + encode(serviceId);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private ResponseEntity<String> exchange(String regionName, String httpMethod, String url,
                                              Function<RestClient, ResponseEntity<String>> caller) {
        RegionClient regionClient = clientFactory.getClient(regionName, "");
        try {
            return caller.apply(regionClient.restClient());
        } catch (ResourceAccessException socketErr) {
            try {
                return caller.apply(regionClient.restClient());
            } catch (ResourceAccessException retryErr) {
                throw new RegionApiSocketException(API_TYPE, url, httpMethod, retryErr);
            }
        }
    }

    private static ResponseEntity<String> readResponse(org.springframework.http.client.ClientHttpResponse r) {
        try {
            return ResponseEntity.status(r.getStatusCode())
                    .headers(r.getHeaders())
                    .body(new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
