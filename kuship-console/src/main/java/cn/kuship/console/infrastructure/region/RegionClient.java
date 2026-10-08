package cn.kuship.console.infrastructure.region;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

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
    /** 仅用于解析 region 错误响应体（RegionCallException.body）。 */
    private static final ObjectMapper ERROR_BODY_MAPPER = new ObjectMapper();

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
        String url = baseUrl(endpoint) + path;

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
                    // 对齐 rainbond CallApiError：携带 url/method/httpcode/body，由全局 handler 渲染。
                    // url 用 region_info 配置值（非 urlOverride 实际值），与 7070 报错体逐字节一致。
                    Object parsedBody;
                    try {
                        parsedBody = ERROR_BODY_MAPPER.readValue(respBody, Object.class);
                    } catch (Exception pe) {
                        parsedBody = respBody;
                    }
                    throw new cn.kuship.console.common.exception.RegionCallException(
                            endpoint.url() + path, method, status, parsedBody);
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

    /** 实际请求基地址：urlOverride 配置非空时优先（开发环境集群内 DNS 不可达），否则 region_info.url。 */
    private String baseUrl(RegionEndpoint endpoint) {
        String override = properties.getUrlOverride();
        return (override != null && !override.isBlank()) ? override : endpoint.url();
    }

    /** 按 region url 缓存 HttpClient（含默认重试策略 + 双向 TLS）。 */
    private CloseableHttpClient clientFor(RegionEndpoint endpoint) {
        return clientCache.computeIfAbsent(baseUrl(endpoint), url -> {
            var builder = HttpClients.custom().setRetryStrategy(
                    new DefaultHttpRequestRetryStrategy(properties.getRetryCount(), TimeValue.ofSeconds(1)));
            if (endpoint.hasMutualTls()) {
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                        buildSslContext(endpoint),
                        properties.isSslVerify() ? new DefaultHostnameVerifier()
                                : NoopHostnameVerifier.INSTANCE);
                builder.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslsf).build());
            }
            return builder.build();
        });
    }

    /**
     * 由 region_info 的 PEM（ssl_ca_cert / cert_file / key_file）构建双向 TLS SSLContext：
     * 客户端证书+私钥（PKCS1，BouncyCastle 解析）放入 KeyStore；sslVerify=false 时信任所有服务端证书（自签）。
     */
    private SSLContext buildSslContext(RegionEndpoint endpoint) {
        try {
            X509Certificate clientCert = parseCert(endpoint.certFile());
            PrivateKey clientKey = parseKey(endpoint.keyFile());
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setKeyEntry("client", clientKey, new char[0], new X509Certificate[]{clientCert});
            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                    javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, new char[0]);

            TrustManager[] trust;
            if (properties.isSslVerify()) {
                KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
                ts.load(null, null);
                ts.setCertificateEntry("ca", parseCert(endpoint.sslCaCert()));
                javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ts);
                trust = tmf.getTrustManagers();
            } else {
                trust = new TrustManager[]{TRUST_ALL};
            }
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), trust, null);
            return ctx;
        } catch (Exception e) {
            throw new ServiceHandleException(500, "region tls init failed: " + e.getMessage(), "集群证书初始化失败");
        }
    }

    private static X509Certificate parseCert(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object o = parser.readObject();
            return new JcaX509CertificateConverter().getCertificate((X509CertificateHolder) o);
        }
    }

    private static PrivateKey parseKey(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object o = parser.readObject();
            JcaPEMKeyConverter conv = new JcaPEMKeyConverter();
            if (o instanceof PEMKeyPair kp) {
                return conv.getKeyPair(kp).getPrivate();
            }
            return conv.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) o);
        }
    }

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
