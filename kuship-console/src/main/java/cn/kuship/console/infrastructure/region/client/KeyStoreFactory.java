package cn.kuship.console.infrastructure.region.client;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 PEM 字符串构造成内存 PKCS12 KeyStore，供 SSL 双向认证使用。
 *
 * <p>设计要点：
 * <ul>
 *   <li>不落盘：BouncyCastle {@link PEMParser} 直接从 String 解析私钥（PKCS#8 / PKCS#1 都支持），
 *       JDK {@link CertificateFactory} 解析证书</li>
 *   <li>使用 PKCS12 格式（JDK 9+ 默认）</li>
 *   <li>固定的内部密码 {@link #INTERNAL_PASSWORD} 仅用于内存 KeyStore 的 alias entry，不暴露</li>
 * </ul>
 */
public final class KeyStoreFactory {

    /** 内存 KeyStore 的固定密码——不参与传输，仅 JDK API 形式上要求。 */
    private static final char[] INTERNAL_PASSWORD = "kuship-region-mtls".toCharArray();

    private KeyStoreFactory() {
    }

    /**
     * 构造客户端 KeyStore（含 cert + key），供双向认证 client 使用。
     */
    public static KeyStore createClientKeyStore(String certPem, String keyPem)
            throws GeneralSecurityException {
        if (certPem == null || keyPem == null) {
            throw new GeneralSecurityException("client cert and key are both required");
        }
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, INTERNAL_PASSWORD);

            List<X509Certificate> chain = parseCertificates(certPem);
            if (chain.isEmpty()) {
                throw new GeneralSecurityException("no X.509 certificate found in client cert PEM");
            }
            PrivateKey privateKey = parsePrivateKey(keyPem);

            ks.setKeyEntry("client", privateKey, INTERNAL_PASSWORD,
                    chain.toArray(new Certificate[0]));
            return ks;
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new GeneralSecurityException("failed to build client KeyStore: " + e.getMessage(), e);
        }
    }

    /**
     * 构造 trust store（仅含 CA cert），供校验服务端证书使用。
     */
    public static KeyStore createTrustStore(String caPem) throws GeneralSecurityException {
        if (caPem == null) {
            throw new GeneralSecurityException("CA PEM is required for trust store");
        }
        try {
            KeyStore ts = KeyStore.getInstance("PKCS12");
            ts.load(null, INTERNAL_PASSWORD);
            List<X509Certificate> certs = parseCertificates(caPem);
            int idx = 0;
            for (X509Certificate c : certs) {
                ts.setCertificateEntry("ca-" + (idx++), c);
            }
            return ts;
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new GeneralSecurityException("failed to build trust KeyStore: " + e.getMessage(), e);
        }
    }

    public static char[] internalPassword() {
        return INTERNAL_PASSWORD.clone();
    }

    private static List<X509Certificate> parseCertificates(String pem) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509Certificate> result = new ArrayList<>();
        // CertificateFactory 可以处理多 cert PEM 串
        try (var in = new java.io.ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            for (Certificate c : cf.generateCertificates(in)) {
                if (c instanceof X509Certificate x) {
                    result.add(x);
                }
            }
        } catch (IOException e) {
            throw new CertificateException("failed to read PEM bytes: " + e.getMessage(), e);
        }
        return result;
    }

    private static PrivateKey parsePrivateKey(String keyPem) throws GeneralSecurityException {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        try (PEMParser parser = new PEMParser(new StringReader(keyPem))) {
            Object obj = parser.readObject();
            if (obj == null) {
                throw new GeneralSecurityException("no PEM object found in private key input");
            }
            if (obj instanceof PEMKeyPair kp) {
                // PKCS#1 (RSA / EC traditional format)
                return converter.getPrivateKey(kp.getPrivateKeyInfo());
            }
            if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
                // PKCS#8 unencrypted
                return converter.getPrivateKey(pki);
            }
            if (obj instanceof PKCS8EncryptedPrivateKeyInfo) {
                throw new GeneralSecurityException(
                        "encrypted PKCS#8 private keys are not supported; "
                                + "supply unencrypted key for region mTLS");
            }
            throw new GeneralSecurityException(
                    "unsupported PEM object type for private key: " + obj.getClass().getName());
        } catch (IOException e) {
            throw new GeneralSecurityException("failed to parse private key PEM: " + e.getMessage(), e);
        }
    }

    /**
     * 抛出此异常表示 mTLS 装配失败（证书/私钥解析或 KeyStore 构造问题）。包装 Java 标准异常。
     */
    public static class GeneralSecurityException extends java.security.GeneralSecurityException {
        public GeneralSecurityException(String message) {
            super(message);
        }

        public GeneralSecurityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
