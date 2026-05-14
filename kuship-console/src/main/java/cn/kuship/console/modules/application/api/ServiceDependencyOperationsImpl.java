package cn.kuship.console.modules.application.api;

import cn.kuship.console.infrastructure.region.api.ServiceDependencyOperations;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Primary
public class ServiceDependencyOperationsImpl implements ServiceDependencyOperations {

    private static final String API_TYPE = "service_dependency";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ServiceDependencyOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> addDependency(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/dependency";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, Object> bean = (Map) processor.extractBean(resp, Map.class, API_TYPE, url, "POST");
        return bean != null ? bean : Map.of();
    }

    @Override
    public void deleteDependency(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/dependency";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.method(org.springframework.http.HttpMethod.DELETE).uri(url)
                        .contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    /**
     * 批量添加依赖 —— region 端路径保留 rainbond 历史拼写 {@code dependencys}（不是 dependencies）。
     *
     * <p>rainbond 锚点：{@code regionapi.py:242-265}
     */
    @Override
    public Map<String, Object> addDependencies(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        // 注意：路径保留 rainbond 历史拼写 dependencys（非 dependencies）
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/dependencys";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, Object> bean = (Map) processor.extractBean(resp, Map.class, API_TYPE, url, "POST");
        return bean != null ? bean : Map.of();
    }

    /**
     * 旧版持久化挂载依赖（5.0 之前）—— 仅供 helm-install / app-import 子 change 内部调用。
     *
     * <p>rainbond 锚点：{@code regionapi.py:811-820}
     */
    @Override
    public Map<String, Object> addVolumeDependency(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/volume-dependency";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, Object> bean = (Map) processor.extractBean(resp, Map.class, API_TYPE, url, "POST");
        return bean != null ? bean : Map.of();
    }

    /**
     * 旧版删除持久化挂载依赖（5.0 之前）—— 仅供 helm-install / app-import 子 change 内部调用。
     *
     * <p>rainbond 锚点：{@code regionapi.py:822-832}
     */
    @Override
    public void deleteVolumeDependency(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/volume-dependency";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "DELETE",
                c -> c.method(org.springframework.http.HttpMethod.DELETE).uri(url)
                        .contentType(MediaType.APPLICATION_JSON).body(body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
