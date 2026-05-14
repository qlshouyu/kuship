package cn.kuship.console.modules.region.resource.api;

import cn.kuship.console.infrastructure.region.api.ResourceCenterOperations;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

/**
 * {@link ResourceCenterOperations} 真实实现：转发 Rainbond region API。
 *
 * <p>URL 对齐 rainbond {@code apiclient/regionapi.py} 3670-3847 行。
 */
@Service
@Primary
public class ResourceCenterOperationsImpl implements ResourceCenterOperations {

    private static final String API_TYPE = "resource-center";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ResourceCenterOperationsImpl(RegionClientFactory clientFactory,
                                         RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    // ---- NS 资源 ----

    @Override
    public Map<String, Object> getNsResourceTypes(String regionName, String tenantName) {
        String url = "/v2/tenants/" + encode(tenantName) + "/ns-resource-types";
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getNsResources(String regionName, String tenantName,
                                               Map<String, String> params) {
        String url = "/v2/tenants/" + encode(tenantName) + "/ns-resources";
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(u -> {
                    var b = u.path(url);
                    if (params != null) params.forEach(b::queryParam);
                    return b.build();
                }).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getNsResource(String regionName, String tenantName, String name,
                                              Map<String, String> params) {
        String url = "/v2/tenants/" + encode(tenantName) + "/ns-resources/" + encode(name);
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(u -> {
                    var b = u.path(url);
                    if (params != null) params.forEach(b::queryParam);
                    return b.build();
                }).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public ResponseEntity<byte[]> postNsResource(String regionName, String tenantName, byte[] body,
                                                  Map<String, String> params, String contentType) {
        String url = "/v2/tenants/" + encode(tenantName) + "/ns-resources";
        return exchange(regionName, "", "POST", url, c -> {
            String ct = (contentType != null && !contentType.isBlank())
                    ? contentType : MediaType.APPLICATION_JSON_VALUE;
            var uri = url;
            if (params != null && !params.isEmpty()) {
                StringBuilder sb = new StringBuilder(url).append("?");
                params.forEach((k, v) -> sb.append(encode(k)).append("=").append(encode(v)).append("&"));
                uri = sb.substring(0, sb.length() - 1);
            }
            final String finalUri = uri;
            ResponseEntity<String> str = c.post().uri(finalUri)
                    .contentType(MediaType.parseMediaType(ct))
                    .body(body)
                    .exchange((req, r) -> readString(r));
            // 返回 byte[] ResponseEntity（保留状态码）
            return ResponseEntity.status(str.getStatusCode())
                    .headers(str.getHeaders())
                    .body(str.getBody() != null ? str.getBody().getBytes(StandardCharsets.UTF_8) : new byte[0]);
        });
    }

    @Override
    public Map<String, Object> putNsResource(String regionName, String tenantName, String name,
                                              byte[] body, Map<String, String> params, String contentType) {
        String url = "/v2/tenants/" + encode(tenantName) + "/ns-resources/" + encode(name);
        ResponseEntity<String> resp = exchange(regionName, "", "PUT", url, c -> {
            String ct = (contentType != null && !contentType.isBlank())
                    ? contentType : MediaType.APPLICATION_JSON_VALUE;
            var reqUri = url;
            if (params != null && !params.isEmpty()) {
                StringBuilder sb = new StringBuilder(url).append("?");
                params.forEach((k, v) -> sb.append(encode(k)).append("=").append(encode(v)).append("&"));
                reqUri = sb.substring(0, sb.length() - 1);
            }
            return c.put().uri(reqUri)
                    .contentType(MediaType.parseMediaType(ct))
                    .body(body)
                    .exchange((req, r) -> readString(r));
        });
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public void deleteNsResource(String regionName, String tenantName, String name,
                                  Map<String, String> params) {
        String url = "/v2/tenants/" + encode(tenantName) + "/ns-resources/" + encode(name);
        ResponseEntity<String> resp = exchange(regionName, "", "DELETE", url,
                c -> c.delete().uri(u -> {
                    var b = u.path(url);
                    if (params != null) params.forEach(b::queryParam);
                    return b.build();
                }).exchange((req, r) -> readString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    // ---- Helm Release ----

    @Override
    public Map<String, Object> getHelmReleases(String regionName, String tenantName, String namespace) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases";
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(u -> {
                    var b = u.path(url);
                    if (namespace != null && !namespace.isBlank()) b.queryParam("namespace", namespace);
                    return b.build();
                }).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> installHelmRelease(String regionName, String tenantName,
                                                   Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases";
        ResponseEntity<String> resp = exchange(regionName, "", "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> previewHelmChart(String regionName, String tenantName,
                                                 Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/chart-preview";
        ResponseEntity<String> resp = exchange(regionName, "", "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> getHelmReleaseHistory(String regionName, String tenantName,
                                                      String releaseName, String namespace) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases/" + encode(releaseName) + "/history";
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(u -> {
                    var b = u.path(url);
                    if (namespace != null && !namespace.isBlank()) b.queryParam("namespace", namespace);
                    return b.build();
                }).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getHelmReleaseDetail(String regionName, String tenantName,
                                                     String releaseName, String namespace) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases/" + encode(releaseName);
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(u -> {
                    var b = u.path(url);
                    if (namespace != null && !namespace.isBlank()) b.queryParam("namespace", namespace);
                    return b.build();
                }).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> upgradeHelmRelease(String regionName, String tenantName,
                                                   String releaseName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases/" + encode(releaseName);
        ResponseEntity<String> resp = exchange(regionName, "", "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public Map<String, Object> rollbackHelmRelease(String regionName, String tenantName,
                                                    String releaseName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases/" + encode(releaseName) + "/rollback";
        ResponseEntity<String> resp = exchange(regionName, "", "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public void uninstallHelmRelease(String regionName, String tenantName,
                                      String releaseName, String namespace) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases/" + encode(releaseName);
        ResponseEntity<String> resp = exchange(regionName, "", "DELETE", url,
                c -> c.delete().uri(u -> {
                    var b = u.path(url);
                    if (namespace != null && !namespace.isBlank()) b.queryParam("namespace", namespace);
                    return b.build();
                }).exchange((req, r) -> readString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    // ---- 资源中心 ----

    @Override
    public Map<String, Object> getWorkloadDetail(String regionName, String tenantName,
                                                  String resource, String name,
                                                  Map<String, String> params) {
        String url = "/v2/tenants/" + encode(tenantName)
                + "/resource-center/workloads/" + encode(resource) + "/" + encode(name);
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(u -> {
                    var b = u.path(url);
                    if (params != null) params.forEach(b::queryParam);
                    return b.build();
                }).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getPodDetail(String regionName, String tenantName, String podName) {
        String url = "/v2/tenants/" + encode(tenantName) + "/resource-center/pods/" + encode(podName);
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getEvents(String regionName, String tenantName,
                                         Map<String, String> params) {
        String url = "/v2/tenants/" + encode(tenantName) + "/resource-center/events";
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(u -> {
                    var b = u.path(url);
                    if (params != null) params.forEach(b::queryParam);
                    return b.build();
                }).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public InputStream getPodLogStream(String regionName, String tenantName,
                                        String podName, Map<String, String> params) {
        String url = "/v2/tenants/" + encode(tenantName) + "/resource-center/pods/" + encode(podName) + "/logs";
        RegionClient regionClient = clientFactory.getClient(regionName, "");
        try {
            return regionClient.restClient().get()
                    .uri(u -> {
                        var b = u.path(url);
                        if (params != null) params.forEach(b::queryParam);
                        return b.build();
                    })
                    .exchange((req, r) -> {
                        try {
                            // 把 body bytes 拉到内存（简化实现；大日志需 streaming 重构）
                            byte[] bytes = r.getBody().readAllBytes();
                            return new java.io.ByteArrayInputStream(bytes);
                        } catch (IOException e) {
                            throw new RuntimeException("read pod log stream failed", e);
                        }
                    });
        } catch (ResourceAccessException socketErr) {
            try {
                return regionClient.restClient().get()
                        .uri(u -> {
                            var b = u.path(url);
                            if (params != null) params.forEach(b::queryParam);
                            return b.build();
                        })
                        .exchange((req, r) -> {
                            try {
                                return new java.io.ByteArrayInputStream(r.getBody().readAllBytes());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (ResourceAccessException retryErr) {
                throw new RegionApiSocketException(API_TYPE, url, "GET", retryErr);
            }
        }
    }

    // ---- 工具方法 ----

    private <T> T exchange(String regionName, String enterpriseId,
                            String httpMethod, String url,
                            Function<RestClient, T> caller) {
        RegionClient regionClient = clientFactory.getClient(regionName, enterpriseId);
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

    private static ResponseEntity<String> readString(org.springframework.http.client.ClientHttpResponse r) {
        try {
            return ResponseEntity.status(r.getStatusCode())
                    .headers(r.getHeaders())
                    .body(new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
