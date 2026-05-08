package cn.kuship.console.modules.application.api;

import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/** application 模块下 Operations Impl 共享的 region 调用 helper（沿用 TenantOperationsImpl 模式）。 */
public final class RegionApiSupport {

    private RegionApiSupport() {}

    public static ResponseEntity<String> exchange(RegionClientFactory factory, String regionName, String enterpriseId,
                                              String apiType, String url, String httpMethod,
                                              Function<RestClient, ResponseEntity<String>> caller) {
        RegionClient regionClient = factory.getClient(regionName, enterpriseId);
        try {
            return caller.apply(regionClient.restClient());
        } catch (ResourceAccessException socketErr) {
            try {
                return caller.apply(regionClient.restClient());
            } catch (ResourceAccessException retryErr) {
                throw new RegionApiSocketException(apiType, url, httpMethod, retryErr);
            }
        }
    }

    public static ResponseEntity<String> readAsString(org.springframework.http.client.ClientHttpResponse resp) {
        try {
            return ResponseEntity.status(resp.getStatusCode())
                    .headers(resp.getHeaders())
                    .body(new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
