package cn.kuship.console.modules.thirdparty.api;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

/**
 * 第三方组件运行时管理 region API 实现。{@code @Primary} 覆盖默认（接口未提供 default 占位，本类直接为唯一实现）。
 *
 * <p>POST/PUT/DELETE endpoints 三个 method 在 RestClient 链上显式 {@code .header("Resource-Validation","true")}，
 * 与 rainbond Python {@code regionapi.py:_set_headers(token, resource_validation="true")} 一致。
 */
@Service
@Primary
public class ThirdPartyServiceOperationsImpl implements ThirdPartyServiceOperations {

    private static final String API_TYPE = "third_party_service";
    private static final String RESOURCE_VALIDATION_HEADER = "Resource-Validation";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;
    private final TenantsRepository tenantsRepository;

    public ThirdPartyServiceOperationsImpl(RegionClientFactory clientFactory,
                                            RegionApiResponseProcessor processor,
                                            TenantsRepository tenantsRepository) {
        this.clientFactory = clientFactory;
        this.processor = processor;
        this.tenantsRepository = tenantsRepository;
    }

    @Override
    public Map<String, Object> getEndpoints(String regionName, String tenantName, String serviceAlias) {
        String url = endpointsUrl(tenantName, serviceAlias);
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> postEndpoints(String regionName, String tenantName, String serviceAlias,
                                              Map<String, Object> body) {
        String url = endpointsUrl(tenantName, serviceAlias);
        ResponseEntity<String> resp = exchange(regionName, "POST", url,
                c -> c.post().uri(url)
                        .header(RESOURCE_VALIDATION_HEADER, "true")
                        .contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> putEndpoints(String regionName, String tenantName, String serviceAlias,
                                             Map<String, Object> body) {
        String url = endpointsUrl(tenantName, serviceAlias);
        ResponseEntity<String> resp = exchange(regionName, "PUT", url,
                c -> c.put().uri(url)
                        .header(RESOURCE_VALIDATION_HEADER, "true")
                        .contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public Map<String, Object> deleteEndpoints(String regionName, String tenantName, String serviceAlias,
                                                Map<String, Object> body) {
        String url = endpointsUrl(tenantName, serviceAlias);
        ResponseEntity<String> resp = exchange(regionName, "DELETE", url,
                c -> c.method(HttpMethod.DELETE).uri(url)
                        .header(RESOURCE_VALIDATION_HEADER, "true")
                        .contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "DELETE"));
    }

    @Override
    public Map<String, Object> getHealth(String regionName, String tenantName, String serviceAlias) {
        String url = healthUrl(tenantName, serviceAlias);
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> putHealth(String regionName, String tenantName, String serviceAlias,
                                          Map<String, Object> body) {
        String url = healthUrl(tenantName, serviceAlias);
        ResponseEntity<String> resp = exchange(regionName, "PUT", url,
                c -> c.put().uri(url)
                        .contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    private String endpointsUrl(String tenantName, String serviceAlias) {
        return "/v2/tenants/" + encode(resolveNamespace(tenantName))
                + "/services/" + encode(serviceAlias) + "/endpoints";
    }

    private String healthUrl(String tenantName, String serviceAlias) {
        return "/v2/tenants/" + encode(resolveNamespace(tenantName))
                + "/services/" + encode(serviceAlias) + "/3rd-party/probe";
    }

    private String resolveNamespace(String tenantName) {
        Tenants t = tenantsRepository.findByTenantName(tenantName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        return (t.getNamespace() != null && !t.getNamespace().isBlank())
                ? t.getNamespace()
                : t.getTenantName();
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
