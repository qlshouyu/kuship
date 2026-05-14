package cn.kuship.console.modules.gateway.cert;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * 测试夹具：使用 BouncyCastle 现场生成 self-signed 证书和密钥对。
 *
 * <p>避免在源码中放真实 PEM 或可能泄露的测试证书。所有生成的密钥/证书仅用于测试，
 * 无任何实际安全价值。
 */
public final class CertGenerator {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private CertGenerator() {}

    /** RSA 2048 密钥对 + 自签名证书。 */
    public record CertAndKey(String certPem, String privateKeyPem, X509Certificate cert, KeyPair keyPair) {}

    /**
     * 生成 RSA 2048 self-signed 证书（有效期 365 天），SAN 为指定 CN。
     *
     * @param cn Common Name，同时作为 SAN dNSName
     * @return {@link CertAndKey}
     */
    public static CertAndKey generateRsa2048(String cn) {
        return generate("RSA", 2048, "SHA256withRSA", cn, 365);
    }

    /**
     * 生成 ECDSA P-256 self-signed 证书（有效期 365 天）。
     *
     * @param cn Common Name
     * @return {@link CertAndKey}
     */
    public static CertAndKey generateEcP256(String cn) {
        return generate("EC", 256, "SHA256withECDSA", cn, 365);
    }

    /**
     * 生成已过期的 RSA 2048 证书（有效期 -10 天到 -1 天，即已过期）。
     *
     * @param cn Common Name
     * @return {@link CertAndKey}
     */
    public static CertAndKey generateExpiredRsa2048(String cn) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            Instant now = Instant.now();
            Date notBefore = Date.from(now.minus(10, ChronoUnit.DAYS));
            Date notAfter = Date.from(now.minus(1, ChronoUnit.DAYS));

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    new X500Name("CN=" + cn),
                    BigInteger.valueOf(System.currentTimeMillis()),
                    notBefore,
                    notAfter,
                    new X500Name("CN=" + cn),
                    kp.getPublic()
            );

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider("BC")
                    .build(kp.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certBuilder.build(signer));

            String certPem = toPem("CERTIFICATE", cert.getEncoded());
            String keyPem = toPem("PRIVATE KEY", kp.getPrivate().getEncoded());
            return new CertAndKey(certPem, keyPem, cert, kp);
        } catch (Exception e) {
            throw new RuntimeException("failed to generate expired cert", e);
        }
    }

    private static CertAndKey generate(String algorithm, int keySize,
                                       String sigAlg, String cn, int validDays) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm, "BC");
            if ("EC".equals(algorithm)) {
                kpg.initialize(new org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec("P-" + keySize));
            } else {
                kpg.initialize(keySize);
            }
            KeyPair kp = kpg.generateKeyPair();

            Instant now = Instant.now();
            Date notBefore = Date.from(now.minus(1, ChronoUnit.DAYS));
            Date notAfter = Date.from(now.plus(validDays, ChronoUnit.DAYS));

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    new X500Name("CN=" + cn),
                    BigInteger.valueOf(System.currentTimeMillis()),
                    notBefore,
                    notAfter,
                    new X500Name("CN=" + cn),
                    kp.getPublic()
            );

            ContentSigner signer = new JcaContentSignerBuilder(sigAlg)
                    .setProvider("BC")
                    .build(kp.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certBuilder.build(signer));

            String certPem = toPem("CERTIFICATE", cert.getEncoded());
            // PKCS#8 格式私钥
            String keyPem = toPem("PRIVATE KEY", kp.getPrivate().getEncoded());
            return new CertAndKey(certPem, keyPem, cert, kp);
        } catch (Exception e) {
            throw new RuntimeException("failed to generate " + algorithm + " cert/key", e);
        }
    }

    /** 将 DER 字节封装为 PEM 字符串。 */
    public static String toPem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
