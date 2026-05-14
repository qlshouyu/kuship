package cn.kuship.console.modules.gateway.cert;

import cn.kuship.console.common.exception.ServiceHandleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CertificateAnalyzer} 单元测试。
 *
 * <p>覆盖 design.md 列出的 5 类用例：
 * <ol>
 *   <li>RSA 2048 公私钥匹配</li>
 *   <li>ECDSA P-256 公私钥匹配</li>
 *   <li>公钥不匹配 → {@code ServiceHandleException(400)}</li>
 *   <li>PKCS#1 RSA 私钥格式</li>
 *   <li>过期证书 valid_to 解析正确</li>
 * </ol>
 *
 * <p>证书由 {@link CertGenerator} 现场生成，无泄露风险。
 */
class CertificateAnalyzerTest {

    private final CertificateAnalyzer analyzer = new CertificateAnalyzer();

    // ─────────────── 用例 1：RSA 2048 匹配 ──────────────────────────

    @Test
    void rsa2048_validPair_noException() {
        CertGenerator.CertAndKey ck = CertGenerator.generateRsa2048("test.kuship.io");
        // 不抛异常即通过
        analyzer.validatePair(ck.certPem(), ck.privateKeyPem());
    }

    @Test
    void rsa2048_analyze_returnsCorrectInfo() {
        CertGenerator.CertAndKey ck = CertGenerator.generateRsa2048("test.kuship.io");
        CertificateAnalyzer.CertInfo info = analyzer.analyze(ck.certPem());
        assertNotNull(info);
        assertTrue(info.subject().contains("CN=test.kuship.io"), "subject 应含 CN");
        assertEquals(2048, info.publicKeySize(), "RSA 2048 公钥位数");
        assertNotNull(info.validFrom());
        assertNotNull(info.validTo());
        assertTrue(info.signatureAlgorithm().toUpperCase().contains("RSA"), "签名算法应含 RSA");
    }

    // ─────────────── 用例 2：ECDSA P-256 匹配 ────────────────────────

    @Test
    void ecdsaP256_validPair_noException() {
        CertGenerator.CertAndKey ck = CertGenerator.generateEcP256("ec.kuship.io");
        analyzer.validatePair(ck.certPem(), ck.privateKeyPem());
    }

    @Test
    void ecdsaP256_analyze_returnsCorrectKeySize() {
        CertGenerator.CertAndKey ck = CertGenerator.generateEcP256("ec.kuship.io");
        CertificateAnalyzer.CertInfo info = analyzer.analyze(ck.certPem());
        assertEquals(256, info.publicKeySize(), "EC P-256 字段大小");
    }

    // ─────────────── 用例 3：公钥不匹配 ────────────────────────────────

    @Test
    void rsaMismatch_throws400() {
        CertGenerator.CertAndKey ck1 = CertGenerator.generateRsa2048("a.kuship.io");
        CertGenerator.CertAndKey ck2 = CertGenerator.generateRsa2048("b.kuship.io");
        // 用 ck1 的证书 + ck2 的私钥，应该不匹配
        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> analyzer.validatePair(ck1.certPem(), ck2.privateKeyPem()));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMsgShow().contains("不匹配"), "msg_show 应提示不匹配");
    }

    // ─────────────── 用例 4：PKCS#1 RSA 私钥格式 ───────────────────────

    @Test
    void pkcs1PrivateKey_canBeParsed() throws Exception {
        // 使用 BouncyCastle 将 PKCS#8 转为 PKCS#1 RSA 格式
        CertGenerator.CertAndKey ck = CertGenerator.generateRsa2048("pkcs1.kuship.io");

        // 从 PKCS#8 私钥提取 RSA CRT 私钥结构，转为 PKCS#1 PEM
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
        java.security.interfaces.RSAPrivateCrtKey rsaKey = (java.security.interfaces.RSAPrivateCrtKey)
                kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(
                        java.util.Base64.getMimeDecoder().decode(
                                ck.privateKeyPem()
                                        .replace("-----BEGIN PRIVATE KEY-----", "")
                                        .replace("-----END PRIVATE KEY-----", "")
                                        .replaceAll("\\s+", "")
                        )
                ));

        // 构造 PKCS#1 RSA 私钥 PEM（使用 BouncyCastle）
        org.bouncycastle.asn1.pkcs.RSAPrivateKey bcKey = new org.bouncycastle.asn1.pkcs.RSAPrivateKey(
                rsaKey.getModulus(),
                rsaKey.getPublicExponent(),
                rsaKey.getPrivateExponent(),
                rsaKey.getPrimeP(),
                rsaKey.getPrimeQ(),
                rsaKey.getPrimeExponentP(),
                rsaKey.getPrimeExponentQ(),
                rsaKey.getCrtCoefficient()
        );
        String pkcs1Pem = CertGenerator.toPem("RSA PRIVATE KEY", bcKey.getEncoded());

        // 应该能成功解析 PKCS#1 格式私钥，不抛异常
        java.security.PrivateKey parsed = analyzer.parsePrivateKey(pkcs1Pem);
        assertNotNull(parsed, "PKCS#1 私钥应能被成功解析");
    }

    // ─────────────── 用例 5：过期证书 valid_to 解析 ──────────────────────

    @Test
    void expiredCert_validToInPast() {
        CertGenerator.CertAndKey ck = CertGenerator.generateExpiredRsa2048("expired.kuship.io");
        CertificateAnalyzer.CertInfo info = analyzer.analyze(ck.certPem());
        assertTrue(info.validTo().isBefore(java.time.Instant.now()),
                "过期证书的 valid_to 应早于当前时间");
    }

    // ─────────────── 边界：空输入 ────────────────────────────────────────

    @Test
    void blankCert_throws400() {
        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> analyzer.validatePair("", "some-key"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void blankKey_throws400() {
        CertGenerator.CertAndKey ck = CertGenerator.generateRsa2048("x.kuship.io");
        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> analyzer.validatePair(ck.certPem(), ""));
        assertEquals(400, ex.getCode());
    }
}
