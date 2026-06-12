package cn.kuship.console.infrastructure.region;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单 region-api 的传输骨架（仅传输/认证/连接池/重试与超时，<b>不含任何 /v2/tenants 域方法</b>）。
 *
 * <ul>
 *   <li>地址/凭证：从 {@code region_info} 表解析（{@link RegionEndpoint}）；</li>
 *   <li>认证优先级：企业级 Token → 区域级 token →（双向 TLS，作为后续扩展点）；</li>
 *   <li>可靠性：默认重试 {@code retryCount} 次；按 {@link TimeoutTier} 设置超时梯度；</li>
 *   <li>连接池：按 region url 缓存 {@link CloseableHttpClient}；</li>
 *   <li>多 region：当前仅单 region；以 regionName 解析，天然预留多 region 扩展点。</li>
 * </ul>
 */
@Component
public class RegionClient {

    private static final Logger log = LoggerFactory.getLogger(RegionClient.class);

    private final RegionConfigRepository regionRepository;
    private final RegionProperties properties;
    private final Map<String, CloseableHttpClient> clientCache = new ConcurrentHashMap<>();

    public RegionClient(RegionConfigRepository regionRepository, RegionProperties properties) {
        this.regionRepository = regionRepository;
        this.properties = properties;
    }

    /** 从 region_info 解析连接信息。 */
    public RegionEndpoint resolveEndpoint(String regionName) {
        return regionRepository.findByRegionName(regionName)
                .map(RegionEndpoint::from)
                .orElseThrow(() -> ServiceHandleException.notFound(
                        "region not found: " + regionName, "数据中心不存在"));
    }

    /**
     * 认证优先级：企业级 Token 优先于区域级 token。返回用于 {@code Authorization: Token <token>} 的值（可空）。
     * 双向 TLS（{@link RegionEndpoint#hasMutualTls()}）作为后续扩展点，本骨架暂以 sslVerify 配置占位。
     */
    public String chooseAuthToken(String enterpriseToken, RegionEndpoint endpoint) {
        if (enterpriseToken != null && !enterpriseToken.isBlank()) {
            return enterpriseToken;
        }
        if (endpoint.token() != null && !endpoint.token().isBlank()) {
            return endpoint.token();
        }
        return null;
    }

    /**
     * 通用传输方法（非域方法）：向 region-api 发请求并返回响应体。
     * 具体 {@code /v2/tenants/...} 域封装在后续轮次实现。
     */
    public String exchange(String regionName, String method, String path, String body,
                           TimeoutTier tier, String enterpriseToken) {
        RegionEndpoint endpoint = resolveEndpoint(regionName);
        String token = chooseAuthToken(enterpriseToken, endpoint);
        String url = endpoint.url() + path;

        HttpUriRequestBase request = new HttpUriRequestBase(method, URI.create(url));
        if (token != null) {
            request.setHeader("Authorization", "Token " + token);
        }
        if (body != null && !body.isBlank()) {
            request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        }
        request.setConfig(requestConfig(tier));

        try {
            return clientFor(endpoint).execute(request, response -> {
                int status = response.getCode();
                String respBody = response.getEntity() == null ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (status >= 400) {
                    throw new ServiceHandleException(status, "region error: " + status, "集群请求失败");
                }
                return respBody;
            });
        } catch (ServiceHandleException e) {
            throw e;
        } catch (Exception e) {
            log.error("region exchange failed: {} {}", method, url, e);
            throw new ServiceHandleException(500, "region unreachable: " + e.getMessage(), "无法连接数据中心");
        }
    }

    private RequestConfig requestConfig(TimeoutTier tier) {
        int seconds = (tier == null ? TimeoutTier.NORMAL : tier).seconds();
        return RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(seconds))
                .build();
    }

    /** 按 region url 缓存 HttpClient（含默认重试策略）。 */
    private CloseableHttpClient clientFor(RegionEndpoint endpoint) {
        return clientCache.computeIfAbsent(endpoint.url(), url -> HttpClients.custom()
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(
                        properties.getRetryCount(), TimeValue.ofSeconds(1)))
                .build());
        // NOTE: 双向 TLS / 自签证书信任（ssl_ca_cert / cert_file / key_file，BouncyCastle 解析 PEM）
        //       在对接真实 region-api 时按 endpoint.hasMutualTls() 接入，作为本骨架的扩展点。
    }
}
