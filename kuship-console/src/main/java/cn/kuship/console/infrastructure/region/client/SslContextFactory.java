package cn.kuship.console.infrastructure.region.client;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * 为指定 region 装配 {@link SSLContext}。
 *
 * <p>策略：
 * <ul>
 *   <li>客户端证书 + 私钥：用 {@link KeyStoreFactory#createClientKeyStore} 构造，必须存在</li>
 *   <li>{@link RegionProperties#sslVerify()} = false → 用 trust-all {@code X509TrustManager}（与 Python 端默认一致）</li>
 *   <li>{@link RegionProperties#sslVerify()} = true → 用 {@link KeyStoreFactory#createTrustStore} 构造的 trust store
 *       做严格校验（生产建议）</li>
 * </ul>
 */
public final class SslContextFactory {

    private SslContextFactory() {
    }

    public static SSLContext build(RegionInfoDto region, RegionProperties props) throws IOException {
        try {
            String certPem = PemMaterialResolver.resolve(region.certFile());
            String keyPem = PemMaterialResolver.resolve(region.keyFile());
            String caPem = PemMaterialResolver.resolve(region.sslCaCert());

            KeyStore clientKs = KeyStoreFactory.createClientKeyStore(certPem, keyPem);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(clientKs, KeyStoreFactory.internalPassword());

            TrustManager[] trustManagers;
            if (props.sslVerify()) {
                if (caPem == null) {
                    throw new KeyStoreFactory.GeneralSecurityException(
                            "ssl-verify=true but region '" + region.regionName() + "' has no ssl_ca_cert");
                }
                KeyStore trustKs = KeyStoreFactory.createTrustStore(caPem);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustKs);
                trustManagers = tmf.getTrustManagers();
            } else {
                trustManagers = new TrustManager[]{trustAll()};
            }

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), trustManagers, new SecureRandom());
            return ctx;
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("failed to build SSLContext for region '"
                    + region.regionName() + "': " + e.getMessage(), e);
        }
    }

    private static X509TrustManager trustAll() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // trust all (kuship.region.ssl-verify=false, dev only)
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // trust all
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
