package cn.kuship.console.infrastructure.region.client;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试 PEM → KeyStore 装配。
 *
 * <p>使用 BouncyCastle 在测试启动时生成临时 self-signed CA + client cert/key（PKCS#8 PEM），
 * 避免引入测试 fixture 文件。
 */
class KeyStoreFactoryTest {

    private static String caCertPem;
    private static String clientCertPem;
    private static String clientKeyPem; // PKCS#8 unencrypted

    @BeforeAll
    static void setUp() throws Exception {
        // 用 BouncyCastle 生成自签 CA 与 client 证书
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        java.security.KeyPair caKp = kpg.generateKeyPair();
        java.security.KeyPair clientKp = kpg.generateKeyPair();

        // CA 自签
        org.bouncycastle.asn1.x500.X500Name caName = new org.bouncycastle.asn1.x500.X500Name("CN=Test CA");
        java.math.BigInteger serial = new java.math.BigInteger(64, new java.security.SecureRandom());
        java.util.Date notBefore = new java.util.Date(System.currentTimeMillis() - 1_000L);
        java.util.Date notAfter = new java.util.Date(System.currentTimeMillis() + 86_400_000L);
        org.bouncycastle.cert.X509v3CertificateBuilder caBuilder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(caName, serial, notBefore, notAfter, caName, caKp.getPublic());
        org.bouncycastle.operator.ContentSigner caSigner = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(caKp.getPrivate());
        org.bouncycastle.cert.X509CertificateHolder caHolder = caBuilder.build(caSigner);
        X509Certificate caCert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(caHolder);

        // Client cert by CA
        org.bouncycastle.asn1.x500.X500Name clientName = new org.bouncycastle.asn1.x500.X500Name("CN=Test Client");
        org.bouncycastle.cert.X509v3CertificateBuilder clientBuilder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(caName,
                        new java.math.BigInteger(64, new java.security.SecureRandom()),
                        notBefore, notAfter, clientName, clientKp.getPublic());
        org.bouncycastle.cert.X509CertificateHolder clientHolder = clientBuilder.build(caSigner);
        X509Certificate clientCert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(clientHolder);

        caCertPem = toPem("CERTIFICATE", caCert.getEncoded());
        clientCertPem = toPem("CERTIFICATE", clientCert.getEncoded());

        // 私钥转成 PKCS#8 PEM（OpenSSL 风格 BEGIN PRIVATE KEY）
        java.security.spec.PKCS8EncodedKeySpec p8 = new java.security.spec.PKCS8EncodedKeySpec(clientKp.getPrivate().getEncoded());
        clientKeyPem = toPem("PRIVATE KEY", p8.getEncoded());
    }

    private static String toPem(String label, byte[] der) {
        String b64 = java.util.Base64.getMimeEncoder(64,
                System.lineSeparator().getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + b64 + "\n-----END " + label + "-----\n";
    }

    @Test
    void inlinePem_buildsClientKeyStore() throws Exception {
        KeyStore ks = KeyStoreFactory.createClientKeyStore(clientCertPem, clientKeyPem);
        assertNotNull(ks.getCertificate("client"));
        assertEquals("X.509", ks.getCertificate("client").getType());
    }

    @Test
    void inlinePem_buildsTrustStore() throws Exception {
        KeyStore ts = KeyStoreFactory.createTrustStore(caCertPem);
        assertTrue(ts.aliases().asIterator().hasNext());
    }

    @Test
    void filePathPem_resolvedAndAssembled(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        Path certFile = tempDir.resolve("client.pem");
        Path keyFile = tempDir.resolve("client.key");
        Files.writeString(certFile, clientCertPem);
        Files.writeString(keyFile, clientKeyPem);

        String resolvedCert = PemMaterialResolver.resolve(certFile.toAbsolutePath().toString());
        String resolvedKey = PemMaterialResolver.resolve(keyFile.toAbsolutePath().toString());
        assertEquals(clientCertPem, resolvedCert);
        assertEquals(clientKeyPem, resolvedKey);

        KeyStore ks = KeyStoreFactory.createClientKeyStore(resolvedCert, resolvedKey);
        assertNotNull(ks.getCertificate("client"));
    }

    @Test
    void blankInput_returnsNull() throws IOException {
        assertEquals(null, PemMaterialResolver.resolve(null));
        assertEquals(null, PemMaterialResolver.resolve(""));
    }

    @Test
    void emptyKey_throws() {
        assertThrows(KeyStoreFactory.GeneralSecurityException.class,
                () -> KeyStoreFactory.createClientKeyStore(clientCertPem, null));
    }
}
