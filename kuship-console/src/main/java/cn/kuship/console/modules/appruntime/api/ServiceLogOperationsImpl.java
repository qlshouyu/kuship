package cn.kuship.console.modules.appruntime.api;

import cn.kuship.console.infrastructure.region.api.ServiceLogOperations;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** {@link ServiceLogOperations} 实现：日志拉取 / 历史文件 / WS token。 */
@Service
@Primary
public class ServiceLogOperationsImpl implements ServiceLogOperations {

    private static final String API_TYPE = "service_log";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public ServiceLogOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> getServiceLogs(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) {
        StringBuilder qs = new StringBuilder();
        if (body != null) {
            for (Map.Entry<String, Object> e : body.entrySet()) {
                qs.append(qs.length() == 0 ? "?" : "&");
                qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append("=").append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
            }
        }
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/log" + qs;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getServiceLogFiles(String regionName, String tenantName, String serviceAlias) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/log-file";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getDockerLogInstance(String regionName, String tenantName, String serviceAlias) {
        String url = "/v2/tenants/" + encode(tenantName) + "/services/" + encode(serviceAlias) + "/log-instance";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
