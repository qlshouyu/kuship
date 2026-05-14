package cn.kuship.console.modules.grayrelease.api;

import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

@Service
@Primary
public class GrayReleaseOperationsImpl implements GrayReleaseOperations {

    private static final String API_TYPE = "gray-release";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;
    private final TenantsRepository tenantsRepository;

    public GrayReleaseOperationsImpl(RegionClientFactory clientFactory,
                                       RegionApiResponseProcessor processor,
                                       TenantsRepository tenantsRepository) {
        this.clientFactory = clientFactory;
        this.processor = processor;
        this.tenantsRepository = tenantsRepository;
    }

    @Override
    public Map<String, Object> createAppGrayRelease(String regionName, String tenantName,
                                                      Integer regionAppId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(resolveNamespace(tenantName))
                + "/apps/" + regionAppId + "/gray_release";
        ResponseEntity<String> resp = exchange(regionName, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> updateAppGrayRelease(String regionName, String tenantName,
                                                      Integer regionAppId, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(resolveNamespace(tenantName))
                + "/apps/" + regionAppId + "/gray_release";
        ResponseEntity<String> resp = exchange(regionName, "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public Map<String, Object> operateAppGrayRelease(String regionName, String tenantName,
                                                       Integer regionAppId, String namespace,
                                                       String operationMethod) {
        String resolvedNs = (namespace == null || namespace.isBlank())
                ? resolveNamespace(tenantName) : namespace;
        String url = "/v2/tenants/" + encode(resolvedNs)
                + "/apps/" + regionAppId + "/operate_gray_release"
                + "?namespace=" + encode(resolvedNs)
                + "&app_id=" + regionAppId
                + "&operation_method=" + encode(operationMethod == null ? "" : operationMethod);
        ResponseEntity<String> resp = exchange(regionName, "PUT", url,
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of())
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    private String resolveNamespace(String tenantName) {
        if (tenantName == null) return "";
        return tenantsRepository.findByTenantName(tenantName)
                .map(t -> (t.getNamespace() != null && !t.getNamespace().isBlank())
                        ? t.getNamespace() : t.getTenantName())
                .orElse(tenantName);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
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
