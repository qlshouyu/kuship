package cn.kuship.console.infrastructure.region.api;

import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.MapType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

/**
 * {@link GatewayOperations} 完整实现。
 *
 * <p>覆盖：
 * <ul>
 *   <li>HTTP 域名规则：bindHttpDomain / updateHttpDomain / deleteHttpDomain</li>
 *   <li>TCP 域名规则：bindTcpDomain / updateTcpDomain / unbindTcpDomain</li>
 *   <li>高级路由配置：upgradeConfiguration</li>
 *   <li>Gateway 信息：listGateways / getApiGateway</li>
 *   <li>API Gateway 透传：apiGatewayProxy / apiGatewayGet / apiGatewayPut / apiGatewayDelete / apiGatewayBindHttpDomainConvert</li>
 * </ul>
 */
@Service
@Primary
public class GatewayOperationsImpl implements GatewayOperations {

    private static final String API_TYPE = "gateway";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;
    private final ObjectMapper json;
    private final MapType mapType;
    private final TenantsRepository tenantsRepository;

    public GatewayOperationsImpl(RegionClientFactory clientFactory,
                                   RegionApiResponseProcessor processor,
                                   ObjectMapper json,
                                   TenantsRepository tenantsRepository) {
        this.clientFactory = clientFactory;
        this.processor = processor;
        this.json = json;
        this.mapType = json.getTypeFactory()
                .constructMapType(java.util.LinkedHashMap.class, String.class, Object.class);
        this.tenantsRepository = tenantsRepository;
    }

    // ─── HTTP 域名规则 ────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> bindHttpDomain(String regionName, String enterpriseId,
                                               String tenantName, Map<String, Object> body) {
        String url = httpRuleUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> updateHttpDomain(String regionName, String enterpriseId,
                                                 String tenantName, Map<String, Object> body) {
        String url = httpRuleUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public void deleteHttpDomain(String regionName, String enterpriseId,
                                  String tenantName, Map<String, Object> body) {
        String url = httpRuleUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "DELETE", url,
                c -> c.method(HttpMethod.DELETE).uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    // ─── TCP 域名规则 ────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> bindTcpDomain(String regionName, String enterpriseId,
                                              String tenantName, Map<String, Object> body) {
        String url = tcpRuleUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> updateTcpDomain(String regionName, String enterpriseId,
                                                String tenantName, Map<String, Object> body) {
        String url = tcpRuleUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public void unbindTcpDomain(String regionName, String enterpriseId,
                                 String tenantName, Map<String, Object> body) {
        String url = tcpRuleUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "DELETE", url,
                c -> c.method(HttpMethod.DELETE).uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    // ─── 高级路由配置 ─────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> upgradeConfiguration(String regionName, String enterpriseId,
                                                     String tenantName, String ruleId,
                                                     Map<String, Object> body) {
        String url = "/v2/tenants/" + enc(tenantName) + "/http-rule/" + enc(ruleId) + "/configurations";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    // ─── Gateway 信息查询 ─────────────────────────────────────────────────────

    @Override
    public Map<String, Object> listGateways(String regionName, String enterpriseId, String tenantName) {
        String url = "/v2/tenants/" + enc(tenantName) + "/gateways";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getApiGateway(String regionName, String enterpriseId, String tenantName) {
        String url = "/v2/tenants/" + enc(tenantName) + "/api-gateway";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    // ─── API Gateway 透传 ─────────────────────────────────────────────────────

    @Override
    public Map<String, Object> apiGatewayProxy(String regionName, String enterpriseId,
                                                String tenantName, String path,
                                                Map<String, Object> body) {
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "POST", path,
                c -> c.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readString(r)));
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RegionApiException(resp.getStatusCode().value(),
                    "api-gateway proxy failed: " + truncate(resp.getBody(), 256),
                    "apisix-route 更新失败");
        }
        String text = resp.getBody();
        if (text == null || text.isBlank()) return Map.of();
        return json.readValue(text, mapType);
    }

    @Override
    public Map<String, Object> apiGatewayGet(String regionName, String enterpriseId,
                                              String tenantName, String path) {
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "GET", path,
                c -> c.get().uri(path).exchange((req, r) -> readString(r)));
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RegionApiException(resp.getStatusCode().value(),
                    "api-gateway get failed: " + truncate(resp.getBody(), 256),
                    "api-gateway 查询失败");
        }
        String text = resp.getBody();
        if (text == null || text.isBlank()) return Map.of();
        return json.readValue(text, mapType);
    }

    @Override
    public Map<String, Object> apiGatewayPut(String regionName, String enterpriseId,
                                              String tenantName, String path,
                                              Map<String, Object> body) {
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "PUT", path,
                c -> c.put().uri(path).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readString(r)));
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RegionApiException(resp.getStatusCode().value(),
                    "api-gateway put failed: " + truncate(resp.getBody(), 256),
                    "api-gateway 更新失败");
        }
        String text = resp.getBody();
        if (text == null || text.isBlank()) return Map.of();
        return json.readValue(text, mapType);
    }

    @Override
    public Map<String, Object> apiGatewayDelete(String regionName, String enterpriseId,
                                                  String tenantName, String path,
                                                  Map<String, Object> body) {
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "DELETE", path,
                c -> c.method(HttpMethod.DELETE).uri(path).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readString(r)));
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RegionApiException(resp.getStatusCode().value(),
                    "api-gateway delete failed: " + truncate(resp.getBody(), 256),
                    "api-gateway 删除失败");
        }
        String text = resp.getBody();
        if (text == null || text.isBlank()) return Map.of();
        return json.readValue(text, mapType);
    }

    @Override
    public Map<String, Object> apiGatewayBindHttpDomainConvert(String regionName, String enterpriseId,
                                                                 Map<String, Object> body) {
        String url = "/api-gateway/convert";
        ResponseEntity<String> resp = exchange(regionName, enterpriseId, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readString(r)));
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RegionApiException(resp.getStatusCode().value(),
                    "api-gateway convert failed: " + truncate(resp.getBody(), 256),
                    "域名格式转换失败");
        }
        String text = resp.getBody();
        if (text == null || text.isBlank()) return Map.of();
        return json.readValue(text, mapType);
    }

    // ─── 证书管理（migrate-console-gateway-certificate）───────────────────────

    @Override
    public Map<String, Object> getCertificate(String regionName, String tenantName,
                                                Map<String, Object> body) {
        String url = certificateUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, "", "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> createCertificate(String regionName, String tenantName,
                                                   Map<String, Object> body) {
        String url = certificateUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, "", "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> updateCertificate(String regionName, String tenantName,
                                                   Map<String, Object> body) {
        String url = certificateUrl(tenantName);
        ResponseEntity<String> resp = exchange(regionName, "", "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public void deleteCertificate(String regionName, String tenantName,
                                    String namespace, String name) {
        String url = certificateUrl(tenantName)
                + "?namespace=" + enc(namespace) + "&name=" + enc(name);
        ResponseEntity<String> resp = exchange(regionName, "", "DELETE", url,
                c -> c.method(HttpMethod.DELETE).uri(url)
                        .exchange((req, r) -> readString(r)));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    @Override
    public Map<String, Object> updateIngressesByCertificate(String regionName, String tenantName,
                                                              Map<String, Object> body) {
        // rainbond regionapi.py:1953 用 region_tenant_name (= namespace)；fallback tenantName
        String regionTenantName = tenantsRepository.findByTenantName(tenantName)
                .map(t -> t.getNamespace() != null && !t.getNamespace().isBlank()
                        ? t.getNamespace() : tenantName)
                .orElse(tenantName);
        String url = "/v2/tenants/" + enc(regionTenantName) + "/gateway/certificate";
        ResponseEntity<String> resp = exchange(regionName, "", "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(safe(body))
                        .exchange((req, r) -> readString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    private String certificateUrl(String tenantName) {
        return "/v2/tenants/" + enc(tenantName) + "/gateway-certificate";
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

    private String httpRuleUrl(String tenantName) {
        return "/v2/tenants/" + enc(tenantName) + "/http-rule";
    }

    private String tcpRuleUrl(String tenantName) {
        return "/v2/tenants/" + enc(tenantName) + "/tcp-rule";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, Object> safe(Map<String, Object> body) {
        return body == null ? Map.of() : body;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static ResponseEntity<String> readString(org.springframework.http.client.ClientHttpResponse r) {
        try {
            return ResponseEntity.status(r.getStatusCode())
                    .headers(r.getHeaders())
                    .body(new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseEntity<String> exchange(String regionName, String enterpriseId,
                                             String method, String url,
                                             Function<RestClient, ResponseEntity<String>> caller) {
        RegionClient regionClient = clientFactory.getClient(regionName, enterpriseId);
        try {
            return caller.apply(regionClient.restClient());
        } catch (ResourceAccessException socketErr) {
            try {
                return caller.apply(regionClient.restClient());
            } catch (ResourceAccessException retryErr) {
                throw new RegionApiSocketException(API_TYPE, url, method, retryErr);
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
