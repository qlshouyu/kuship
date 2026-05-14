package cn.kuship.console.modules.application.api;

import cn.kuship.console.infrastructure.region.api.ServiceVolumeOperations;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * ServiceVolumeOperations 完整实现。
 * <p>rainbond 锚点：{@code www/apiclient/regionapi.py}
 * get_volume_options / get_service_volumes / get_service_volumes_status /
 * get_service_dep_volumes / add_service_dep_volumes / delete_service_dep_volumes
 */
@Service
@Primary
public class ServiceVolumeOperationsImpl implements ServiceVolumeOperations {

    private static final String API_TYPE = "service_volume";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ServiceVolumeOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 已有 method（migrate-console-application-core）
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> addVolumes(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/volumes";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public void deleteVolumes(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/volumes";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.method(HttpMethod.DELETE).uri(url)
                        .contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    @Override
    public Map<String, Object> upgradeVolumes(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/volumes";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 新增 method（migrate-console-volume-extras）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 获取 region 支持的存储驱动选项。
     * Python 锚点：{@code regionapi.get_volume_options} → {@code GET /v2/volume-options}
     * 注意：此端点无 /tenants/{t}/services/{a} 前缀。
     */
    @Override
    public Map<String, Object> getVolumeOptions(String regionName, String tenantName) {
        String url = "/v2/volume-options";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    /**
     * 从 region 获取组件的存储卷真实状态。
     * Python 锚点：{@code regionapi.get_service_volumes} →
     * {@code GET /v2/tenants/{t}/services/{a}/volumes?enterprise_id={e}}
     */
    @Override
    public Map<String, Object> getVolumes(String regionName, String tenantName, String serviceAlias) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/volumes";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    /**
     * 获取 region 组件存储卷挂载状态。
     * Python 锚点：{@code regionapi.get_service_volumes_status} →
     * {@code GET /v2/tenants/{t}/services/{a}/volumes-status}
     */
    @Override
    public Map<String, Object> getVolumeStatus(String regionName, String tenantName, String serviceAlias) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/volumes-status";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    /**
     * 从 region 获取组件已挂载的依赖存储。
     * Python 锚点：{@code regionapi.get_service_dep_volumes} →
     * {@code GET /v2/tenants/{t}/services/{a}/depvolumes}
     */
    @Override
    public Map<String, Object> getDepVolumes(String regionName, String tenantName, String serviceAlias) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/depvolumes";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    /**
     * 向 region 添加挂载依赖存储关系。
     * Python 锚点：{@code regionapi.add_service_dep_volumes} →
     * {@code POST /v2/tenants/{t}/services/{a}/depvolumes}
     */
    @Override
    public Map<String, Object> addDepVolumes(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/depvolumes";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    /**
     * 从 region 删除挂载依赖存储关系。
     * Python 锚点：{@code regionapi.delete_service_dep_volumes} →
     * {@code DELETE /v2/tenants/{t}/services/{a}/depvolumes} + body
     */
    @Override
    public void deleteDepVolumes(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/depvolumes";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.method(HttpMethod.DELETE).uri(url)
                        .contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部工具
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
