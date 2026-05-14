package cn.kuship.console.modules.appmarket.helm.api;

import cn.kuship.console.infrastructure.region.api.HelmOperations;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.appmarket.api.RegionApiSupport;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** {@link HelmOperations} 完整实现：Helm chart 信息查询 + helm release 实例 CRUD。 */
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

    // ---- chart 信息（migrate-console-app-market）----

    @Override
    public Map<String, Object> getChartInformation(String regionName, Map<String, Object> body) {
        return post(regionName, "/v2/helm/chart/information", body);
    }

    @Override
    public Map<String, Object> checkHelmApp(String regionName, String tenantName, Map<String, Object> body) {
        return post(regionName, "/v2/tenants/" + encode(tenantName) + "/helm/check", body);
    }

    @Override
    public Map<String, Object> getYamlByChart(String regionName, Map<String, Object> body) {
        return post(regionName, "/v2/helm/chart/yaml", body);
    }

    @Override
    public Map<String, Object> getUploadChartInformation(String regionName, Map<String, Object> body) {
        return post(regionName, "/v2/helm/upload/chart/information", body);
    }

    @Override
    public Map<String, Object> getUploadChartValue(String regionName, Map<String, Object> body) {
        return post(regionName, "/v2/helm/upload/chart/value", body);
    }

    @Override
    public Map<String, Object> importUploadChartResource(String regionName, Map<String, Object> body) {
        return post(regionName, "/v2/helm/upload/chart/resource", body);
    }

    // ---- helm release 实例（migrate-console-helm-release）----

    @Override
    public Map<String, Object> getTenantHelmReleases(String regionName, String tenantName, String namespace) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases";
        return getWithNamespace(regionName, url, namespace);
    }

    @Override
    public Map<String, Object> installTenantHelmRelease(String regionName, String tenantName, Map<String, Object> body) {
        return post(regionName, "/v2/tenants/" + encode(tenantName) + "/helm/releases", body);
    }

    @Override
    public Map<String, Object> previewTenantHelmChart(String regionName, String tenantName, Map<String, Object> body) {
        return post(regionName, "/v2/tenants/" + encode(tenantName) + "/helm/chart-preview", body);
    }

    @Override
    public Map<String, Object> getTenantHelmReleaseDetail(String regionName, String tenantName, String releaseName, String namespace) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases/" + encode(releaseName);
        return getWithNamespace(regionName, url, namespace);
    }

    @Override
    public Map<String, Object> upgradeTenantHelmRelease(String regionName, String tenantName, String releaseName, Map<String, Object> body) {
        return put(regionName, "/v2/tenants/" + encode(tenantName) + "/helm/releases/" + encode(releaseName), body);
    }

    @Override
    public void uninstallTenantHelmRelease(String regionName, String tenantName, String releaseName, String namespace) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases/" + encode(releaseName);
        String fullUrl = appendNamespace(url, namespace);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.delete().uri(fullUrl)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    @Override
    public Map<String, Object> getTenantHelmReleaseHistory(String regionName, String tenantName, String releaseName, String namespace) {
        String url = "/v2/tenants/" + encode(tenantName) + "/helm/releases/" + encode(releaseName) + "/history";
        return getWithNamespace(regionName, url, namespace);
    }

    @Override
    public Map<String, Object> rollbackTenantHelmRelease(String regionName, String tenantName, String releaseName, Map<String, Object> body) {
        return post(regionName, "/v2/tenants/" + encode(tenantName) + "/helm/releases/" + encode(releaseName) + "/rollback", body);
    }

    // ---- 内部 HTTP 模板 ----

    private Map<String, Object> post(String regionName, String url, Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    private Map<String, Object> put(String regionName, String url, Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    private Map<String, Object> getWithNamespace(String regionName, String url, String namespace) {
        String fullUrl = appendNamespace(url, namespace);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(fullUrl)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    private static String appendNamespace(String url, String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return url;
        }
        return UriComponentsBuilder.fromUriString(url).queryParam("namespace", namespace).build().toUriString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
