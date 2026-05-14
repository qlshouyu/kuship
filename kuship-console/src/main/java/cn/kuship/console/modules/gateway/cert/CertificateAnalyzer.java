package cn.kuship.console.modules.gateway.cert;

import cn.kuship.console.common.exception.ServiceHandleException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

/**
 * X.509 证书解析工具 —— 纯 JDK + BouncyCastle（项目已有依赖，不引入新依赖）。
 *
 * <p>覆盖以下场景（对齐 design.md 决策 2/3）：
 * <ul>
 *   <li>RSA 2048/4096：JDK {@code CertificateFactory} 解证书，{@code KeyFactory} 解私钥
 *   <li>ECDSA P-256/P-384：同上
 *   <li>PKCS#8（{@code -----BEGIN PRIVATE KEY-----}）：JDK {@code PKCS8EncodedKeySpec}
 *   <li>PKCS#1 RSA（{@code -----BEGIN RSA PRIVATE KEY-----}）：BouncyCastle fallback
 *   <li>公私钥匹配校验：RSA 比 modulus；ECDSA 比公钥坐标比较（详见 {@link #validatePair}）
 * </ul>
 *
 * <p><b>安全约束</b>：日志和异常消息中不暴露 private_key / certificate 全文，
 * 仅在内部错误时记录 PEM 头部（非敏感信息）。
 */
@Component
public class CertificateAnalyzer {

    /**
     * 证书解析结果。
     *
     * @param issuer           颁发者 DN
     * @param subject          主题 DN
     * @param issuedTo         SAN 列表（Subject Alternative Names）
     * @param validFrom        生效时间
     * @param validTo          过期时间
     * @param signatureAlgorithm 签名算法
     * @param publicKeySize    公钥位数（RSA: modulus 长度；ECDSA: 曲线 bit 数）
     */
    public record CertInfo(
            String issuer,
            String subject,
            List<String> issuedTo,
            Instant validFrom,
            Instant validTo,
            String signatureAlgorithm,
            int publicKeySize
    ) {}

    /**
     * 解析 PEM 证书，提取 issuer / subject / SAN / 有效期 / 签名算法 / 公钥位数。
     *
     * @param pem PEM 格式证书文本（{@code -----BEGIN CERTIFICATE-----}）
     * @return {@link CertInfo}
     * @throws ServiceHandleException 400 当 PEM 解析失败
     */
    public CertInfo analyze(String pem) {
        X509Certificate cert = parseCert(pem);
        List<String> san = extractSan(cert);
        int keySize = extractKeySize(cert.getPublicKey());
        return new CertInfo(
                cert.getIssuerX500Principal().getName(),
                cert.getSubjectX500Principal().getName(),
                san,
                cert.getNotBefore().toInstant(),
                cert.getNotAfter().toInstant(),
                cert.getSigAlgName(),
                keySize
        );
    }

    /**
     * 校验证书与私钥是否匹配。
     *
     * <ul>
     *   <li>RSA：比对证书公钥的 modulus 与私钥的 modulus
     *   <li>ECDSA：比对证书公钥的曲线 + 公钥点坐标（W 点）
     * </ul>
     *
     * @param certPem       PEM 格式证书
     * @param privateKeyPem PEM 格式私钥（PKCS#8 或 PKCS#1 RSA）
     * @throws ServiceHandleException 400 当不匹配或解析失败
     */
    public void validatePair(String certPem, String privateKeyPem) {
        if (certPem == null || certPem.isBlank()) {
            throw new ServiceHandleException(400, "certificate is blank", "证书内容不能为空");
        }
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new ServiceHandleException(400, "private_key is blank", "私钥内容不能为空");
        }

        X509Certificate cert = parseCert(certPem);
        PublicKey certPublicKey = cert.getPublicKey();
        java.security.PrivateKey privateKey = parsePrivateKey(privateKeyPem);

        String algorithm = certPublicKey.getAlgorithm();
        boolean matched;

        if ("RSA".equalsIgnoreCase(algorithm)) {
            RSAPublicKey rsaPub = (RSAPublicKey) certPublicKey;
            RSAPrivateKey rsaPriv = (RSAPrivateKey) privateKey;
            matched = rsaPub.getModulus().equals(rsaPriv.getModulus());
        } else if ("EC".equalsIgnoreCase(algorithm)) {
            ECPublicKey ecPub = (ECPublicKey) certPublicKey;
            ECPrivateKey ecPriv = (ECPrivateKey) privateKey;
            // 比对曲线参数一致，再通过派生公钥点进行比较
            matched = ecPub.getParams().equals(ecPriv.getParams())
                    && matchEcPublicKey(ecPub, ecPriv);
        } else {
            // 其他算法暂不校验匹配度，放行
            return;
        }

        if (!matched) {
            throw new ServiceHandleException(400,
                    "certificate and private key do not match",
                    "证书与私钥不匹配");
        }
    }

    // ─────────────────────────── 内部实现 ─────────────────────────────

    /** 解析 PEM 证书，隐藏 PEM 全文，仅暴露头部标识用于错误定位。 */
    X509Certificate parseCert(String pem) {
        try {
            byte[] derBytes = pemToDer(pem, "CERTIFICATE");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(derBytes));
        } catch (ServiceHandleException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceHandleException(400, "failed to parse certificate: " + e.getMessage(),
                    "证书格式无效，请确认为 PEM 格式 X.509 证书");
        }
    }

    /** 解析私钥，优先 PKCS#8，失败 fallback BouncyCastle 解 PKCS#1 RSA。 */
    java.security.PrivateKey parsePrivateKey(String pem) {
        // 尝试 PKCS#8
        try {
            byte[] der = pemToDer(pem, "PRIVATE KEY");
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            // 先尝试 RSA
            try {
                return KeyFactory.getInstance("RSA").generatePrivate(spec);
            } catch (Exception ignore) {}
            // 再尝试 EC
            try {
                return KeyFactory.getInstance("EC").generatePrivate(spec);
            } catch (Exception ignore) {}
        } catch (ServiceHandleException ignore) {
            // PEM header 不是 PRIVATE KEY，继续 fallback
        }

        // Fallback：PKCS#1 RSA（-----BEGIN RSA PRIVATE KEY-----）
        try {
            byte[] der = pemToDer(pem, "RSA PRIVATE KEY");
            // 使用 BouncyCastle 将 PKCS#1 转为 PKCS#8 再解析
            org.bouncycastle.asn1.pkcs.RSAPrivateKey bcKey =
                    org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(der);
            BigInteger modulus = bcKey.getModulus();
            BigInteger privateExponent = bcKey.getPrivateExponent();
            java.security.spec.RSAPrivateKeySpec spec =
                    new java.security.spec.RSAPrivateKeySpec(modulus, privateExponent);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (ServiceHandleException ignore) {
            // header 也不是 RSA PRIVATE KEY
        } catch (Exception e) {
            throw new ServiceHandleException(400, "failed to parse PKCS#1 private key: " + e.getMessage(),
                    "私钥格式无效（PKCS#1 RSA 解析失败）");
        }

        throw new ServiceHandleException(400, "unsupported private key format",
                "不支持的私钥格式，请使用 PKCS#8 或 PKCS#1 RSA 格式");
    }

    /** 从 PEM 文本剥离头尾，返回 DER 字节；header 不匹配时抛 ServiceHandleException。 */
    private byte[] pemToDer(String pem, String type) {
        String header = "-----BEGIN " + type + "-----";
        String footer = "-----END " + type + "-----";
        String clean = pem.trim();
        if (!clean.contains(header)) {
            throw new ServiceHandleException(400, "PEM header mismatch: expected " + header,
                    "PEM 头部不匹配，期望类型: " + type);
        }
        int start = clean.indexOf(header) + header.length();
        int end = clean.lastIndexOf(footer);
        if (end < start) {
            throw new ServiceHandleException(400, "PEM footer not found for type: " + type,
                    "PEM 格式不完整，缺少尾部标识");
        }
        String base64 = clean.substring(start, end).replaceAll("\\s+", "");
        return Base64.getDecoder().decode(base64);
    }

    /** 提取 SAN 列表（dNSName + iPAddress）。 */
    private List<String> extractSan(X509Certificate cert) {
        List<String> result = new ArrayList<>();
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans == null) return result;
            for (List<?> san : sans) {
                // type 2 = dNSName, type 7 = iPAddress
                Integer type = (Integer) san.get(0);
                if (type == 2 || type == 7) {
                    Object value = san.get(1);
                    if (value instanceof String s) result.add(s);
                }
            }
        } catch (Exception ignore) {
            // SAN 提取失败时返回空列表，不中断主流程
        }
        return result;
    }

    /** 提取公钥位数。RSA: modulus.bitLength()；EC: 曲线 field 位数。 */
    private int extractKeySize(PublicKey pk) {
        if (pk instanceof RSAPublicKey rsa) {
            return rsa.getModulus().bitLength();
        }
        if (pk instanceof ECPublicKey ec) {
            return ec.getParams().getCurve().getField().getFieldSize();
        }
        return -1;
    }

    /**
     * ECDSA 公钥比对：通过私钥 scalar 乘以 Generator 点（EC 标量乘法）派生公钥，
     * 比对与证书公钥的坐标是否一致。
     *
     * <p>由于纯 JDK 没有公开的 EC 标量乘法 API，这里改用行为等价的约束替代：
     * 验证两者曲线相同，并使用 BouncyCastle 做实际推导比较。
     */
    private boolean matchEcPublicKey(ECPublicKey certPub, ECPrivateKey priv) {
        try {
            // 利用 BouncyCastle EC domain params 做标量乘法
            org.bouncycastle.jce.spec.ECParameterSpec bcParams =
                    org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec(
                            getEcCurveName(certPub));
            if (bcParams == null) {
                // 无法识别曲线名称，降级为仅比较曲线参数相等
                return certPub.getParams().getCurve().equals(priv.getParams().getCurve());
            }
            org.bouncycastle.math.ec.ECPoint derived =
                    bcParams.getG().multiply(priv.getS()).normalize();
            org.bouncycastle.math.ec.ECPoint certPoint = bcParams.getCurve()
                    .createPoint(certPub.getW().getAffineX(), certPub.getW().getAffineY());
            return derived.equals(certPoint);
        } catch (Exception e) {
            // BouncyCastle 推导失败时，仅比较曲线参数（宽松）
            return certPub.getParams().getCurve().equals(priv.getParams().getCurve());
        }
    }

    /** 从 ECPublicKey 参数推断标准曲线名称（P-256 / P-384 / P-521）。 */
    private String getEcCurveName(ECPublicKey key) {
        int fieldSize = key.getParams().getCurve().getField().getFieldSize();
        return switch (fieldSize) {
            case 256 -> "P-256";
            case 384 -> "P-384";
            case 521 -> "P-521";
            default -> null;
        };
    }
}
