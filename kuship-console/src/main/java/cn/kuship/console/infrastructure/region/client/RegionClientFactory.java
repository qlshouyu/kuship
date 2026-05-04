package cn.kuship.console.infrastructure.region.client;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.infrastructure.region.repository.RegionInfoRepository;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 {@code (enterpriseId, regionName)} 缓存 {@link RestClient}（含 mTLS + 连接池）。
 *
 * <p>策略：
 * <ul>
 *   <li>懒加载：首次访问触发 {@link RegionInfoRepository} 查询 + mTLS 装配</li>
 *   <li>线程安全：{@link ConcurrentHashMap#computeIfAbsent}</li>
 *   <li>主动失效：调用 {@link #evict} 移除缓存项</li>
 *   <li>region 不存在：抛 {@code RegionApiException(500, "region not found", "集群配置不存在")}</li>
 * </ul>
 */
@Component
public class RegionClientFactory {

    private static final Logger log = LoggerFactory.getLogger(RegionClientFactory.class);

    private final RegionInfoRepository regionInfoRepository;
    private final RegionProperties properties;
    private final ConcurrentHashMap<RegionClientKey, RegionClient> cache = new ConcurrentHashMap<>();

    public RegionClientFactory(RegionInfoRepository regionInfoRepository, RegionProperties properties) {
        this.regionInfoRepository = regionInfoRepository;
        this.properties = properties;
    }

    public RegionClient getClient(String regionName, String enterpriseId) {
        RegionClientKey key = new RegionClientKey(enterpriseId, regionName);
        return cache.computeIfAbsent(key, k -> build(k));
    }

    public void evict(String regionName, String enterpriseId) {
        RegionClientKey key = new RegionClientKey(enterpriseId, regionName);
        RegionClient removed = cache.remove(key);
        if (removed != null) {
            try {
                removed.close();
            } catch (Exception e) {
                log.warn("failed to close region client during evict for {}: {}", key, e.getMessage());
            }
        }
    }

    private RegionClient build(RegionClientKey key) {
        Optional<RegionInfoDto> opt = regionInfoRepository.findByEnterpriseAndName(
                key.enterpriseId(), key.regionName());
        RegionInfoDto region = opt.orElseThrow(() -> new RegionApiException(
                500, "region not found: " + key.regionName(), "集群配置不存在"));

        try {
            CloseableHttpClient httpClient = buildHttpClient(region);
            RestClient restClient = RestClient.builder()
                    .baseUrl(region.url())
                    .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                    .build();
            return new RegionClient(region, restClient, httpClient);
        } catch (java.io.IOException e) {
            throw new RegionApiSocketException("region-client-build", region.url(), null, e);
        }
    }

    private CloseableHttpClient buildHttpClient(RegionInfoDto region) throws java.io.IOException {
        SSLContext sslContext = SslContextFactory.build(region, properties);
        SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .setHostnameVerifier(properties.sslVerify()
                        ? null  // null = use default (strict)
                        : (hostname, session) -> true)
                .build();

        Timeout timeout = Timeout.ofSeconds(properties.timeoutSeconds());
        PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .setMaxConnPerRoute(properties.effectiveMaxPerRoute())
                .setMaxConnTotal(properties.effectiveMaxPerRoute() * 2)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(timeout)
                        .setSocketTimeout(timeout)
                        .build())
                .build();

        RequestConfig reqConfig = RequestConfig.custom()
                .setResponseTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .build();

        return HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(reqConfig)
                .build();
    }
}
