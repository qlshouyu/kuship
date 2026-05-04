package cn.kuship.console.infrastructure.region.api;

import cn.kuship.console.infrastructure.region.api.dto.CreateTenantReq;
import cn.kuship.console.infrastructure.region.api.dto.RegionLabelsResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionPublickeyResp;
import cn.kuship.console.infrastructure.region.api.dto.TenantResourcesResp;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * {@link TenantOperations} 实现。每个 method 走 {@link RegionClientFactory} 拿到
 * mTLS-装配 的 {@link RestClient}，发请求后由 {@link RegionApiResponseProcessor} 完成响应处理。
 *
 * <p>Socket 错误重试一次（与 Python {@code MaxRetryError} 处理对齐）。
 */
@Service
public class TenantOperationsImpl implements TenantOperations {

    private static final String API_TYPE = "tenant";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public TenantOperationsImpl(RegionClientFactory clientFactory,
                                RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public TenantResourcesResp createTenant(String regionName, String enterpriseId, CreateTenantReq req) {
        String url = "/v2/tenants";
        ResponseEntity<String> resp = exchangeWithRetry(regionName, enterpriseId, "POST", url,
                client -> client.post().uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(req)
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))));
        return processor.extractBean(resp, TenantResourcesResp.class, API_TYPE, url, "POST");
    }

    @Override
    public void deleteTenant(String regionName, String enterpriseId, String tenantName) {
        String url = "/v2/tenants/" + encode(tenantName);
        ResponseEntity<String> resp = exchangeWithRetry(regionName, enterpriseId, "DELETE", url,
                client -> client.delete().uri(url)
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))));
        processor.checkStatus(resp, API_TYPE, url, "DELETE");
    }

    @Override
    public TenantResourcesResp getTenantResources(String regionName, String enterpriseId, String tenantName) {
        String url = "/v2/tenants/" + encode(tenantName) + "/res";
        ResponseEntity<String> resp = exchangeWithRetry(regionName, enterpriseId, "GET", url,
                client -> client.get()
                        .uri(uriBuilder -> uriBuilder.path(url)
                                .queryParam("enterprise_id", enterpriseId)
                                .build())
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))));
        return processor.extractBean(resp, TenantResourcesResp.class, API_TYPE, url, "GET");
    }

    @Override
    public RegionPublickeyResp getRegionPublickey(String regionName, String enterpriseId,
                                                  String tenantName, String tenantId) {
        String url = "/v2/tenants/" + encode(tenantName) + "/region-key";
        ResponseEntity<String> resp = exchangeWithRetry(regionName, enterpriseId, "GET", url,
                client -> client.get()
                        .uri(uriBuilder -> uriBuilder.path(url)
                                .queryParam("enterprise_id", enterpriseId)
                                .queryParam("tenant_id", tenantId)
                                .build())
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))));
        return processor.extractBean(resp, RegionPublickeyResp.class, API_TYPE, url, "GET");
    }

    @Override
    public RegionLabelsResp getRegionLabels(String regionName, String enterpriseId, String tenantName) {
        String url = "/v2/tenants/" + encode(tenantName) + "/labels";
        ResponseEntity<String> resp = exchangeWithRetry(regionName, enterpriseId, "GET", url,
                client -> client.get().uri(url)
                        .exchange((req2, resp2) -> ResponseEntity.status(resp2.getStatusCode())
                                .headers(resp2.getHeaders())
                                .body(new String(resp2.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))));
        return processor.extractBean(resp, RegionLabelsResp.class, API_TYPE, url, "GET");
    }

    /**
     * 通用调用 + socket 错误重试一次（与 Python 端 {@code MaxRetryError} 处理一致）。
     */
    private ResponseEntity<String> exchangeWithRetry(String regionName, String enterpriseId,
                                                      String httpMethod, String url,
                                                      java.util.function.Function<RestClient, ResponseEntity<String>> caller) {
        RegionClient regionClient = clientFactory.getClient(regionName, enterpriseId);
        try {
            return caller.apply(regionClient.restClient());
        } catch (ResourceAccessException socketErr) {
            // Spring 把 socket 类异常包装成 ResourceAccessException
            try {
                return caller.apply(regionClient.restClient());
            } catch (ResourceAccessException retryErr) {
                throw new RegionApiSocketException(API_TYPE, url, httpMethod, retryErr);
            }
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
