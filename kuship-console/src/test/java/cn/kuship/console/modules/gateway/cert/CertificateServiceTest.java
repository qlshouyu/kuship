package cn.kuship.console.modules.gateway.cert;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.GatewayOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.gateway.cert.entity.ServiceDomainCertificate;
import cn.kuship.console.modules.gateway.cert.entity.ServiceDomainCertificateRepository;
import cn.kuship.console.modules.gateway.cert.service.CertificateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CertificateService} 单元测试（Mockito，无 DB）。
 *
 * <p>测试重点：region 调用次数、body 形状、事务语义（通过 mock 验证）。
 */
@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private ServiceDomainCertificateRepository certRepo;
    @Mock
    private TenantsRepository tenantsRepo;
    @Mock
    private GatewayOperations gatewayOps;
    @Mock
    private CertificateAnalyzer analyzer;

    @InjectMocks
    private CertificateService service;

    private Tenants tenant;
    private CertGenerator.CertAndKey ck;

    @BeforeEach
    void setUp() {
        tenant = new Tenants();
        tenant.setTenantId("tid-001");
        tenant.setTenantName("default");
        tenant.setNamespace("default-ns");

        ck = CertGenerator.generateRsa2048("test.kuship.io");
    }

    // ─────────────── addCertificate：普通类型不调 region ───────────────

    @Test
    void addCertificate_normalType_noRegionCall() {
        when(certRepo.existsByTenantIdAndAlias("tid-001", "my-cert")).thenReturn(false);
        ServiceDomainCertificate saved = new ServiceDomainCertificate();
        saved.setId(1);
        saved.setAlias("my-cert");
        when(certRepo.save(any())).thenReturn(saved);

        service.addCertificate("region-a", tenant, "my-cert",
                ck.certPem(), ck.privateKeyPem(), "服务端证书");

        verify(gatewayOps, never()).createCertificate(anyString(), anyString(), any());
        verify(analyzer, times(1)).validatePair(eq(ck.certPem()), eq(ck.privateKeyPem()));
    }

    // ─────────────── addCertificate：gateway 类型调 region ─────────────

    @Test
    void addCertificate_gatewayType_callsRegionCreate() {
        when(certRepo.existsByTenantIdAndAlias("tid-001", "gw-cert")).thenReturn(false);
        ServiceDomainCertificate saved = new ServiceDomainCertificate();
        saved.setId(2);
        saved.setAlias("gw-cert");
        when(certRepo.save(any())).thenReturn(saved);

        service.addCertificate("region-a", tenant, "gw-cert",
                ck.certPem(), ck.privateKeyPem(), "gateway");

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(gatewayOps, times(1)).createCertificate(
                eq("region-a"), eq("default"), bodyCaptor.capture());

        Map<String, Object> body = bodyCaptor.getValue();
        assertEquals("default-ns", body.get("namespace"), "body.namespace 应是 tenant.namespace");
        assertEquals("gw-cert", body.get("name"), "body.name 应是 alias");
        // 不断言 private_key / certificate 内容（敏感信息保护）
    }

    // ─────────────── addCertificate：alias 重名 → 409 ──────────────────

    @Test
    void addCertificate_duplicateAlias_throws409() {
        when(certRepo.existsByTenantIdAndAlias("tid-001", "dup-cert")).thenReturn(true);

        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> service.addCertificate("region-a", tenant, "dup-cert",
                        ck.certPem(), ck.privateKeyPem(), "服务端证书"));
        assertEquals(409, ex.getCode());
    }

    // ─────────────── deleteCertificate：被引用 → 409 ────────────────────

    @Test
    void deleteCertificate_stillInUse_throws409() {
        ServiceDomainCertificate cert = new ServiceDomainCertificate();
        cert.setId(5);
        cert.setAlias("used-cert");
        cert.setCertificateType("服务端证书");
        when(certRepo.findById(5)).thenReturn(Optional.of(cert));
        when(certRepo.countByCertificateId(5)).thenReturn(1L);

        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> service.deleteCertificate("region-a", tenant, 5));
        assertEquals(409, ex.getCode());
        verify(gatewayOps, never()).deleteCertificate(anyString(), anyString(), anyString(), anyString());
    }

    // ─────────────── deleteCertificate：gateway 类型先删 region ──────────

    @Test
    void deleteCertificate_gatewayType_callsRegionDelete() {
        ServiceDomainCertificate cert = new ServiceDomainCertificate();
        cert.setId(6);
        cert.setAlias("gw-del");
        cert.setCertificateType("gateway");
        when(certRepo.findById(6)).thenReturn(Optional.of(cert));
        when(certRepo.countByCertificateId(6)).thenReturn(0L);

        service.deleteCertificate("region-a", tenant, 6);

        verify(gatewayOps, times(1)).deleteCertificate(
                eq("region-a"), eq("default"), eq("default-ns"), eq("gw-del"));
    }

    // ─────────────── updateCertificate：普通→gateway 调 createCertificate ─

    @Test
    void updateCertificate_normalToGateway_callsCreate() {
        ServiceDomainCertificate cert = new ServiceDomainCertificate();
        cert.setId(7);
        cert.setAlias("switch-cert");
        cert.setCertificateType("服务端证书");
        cert.setCertificate(Base64.getEncoder().encodeToString(ck.certPem().getBytes()));
        cert.setPrivateKey(ck.privateKeyPem());
        when(certRepo.findById(7)).thenReturn(Optional.of(cert));
        when(certRepo.save(any())).thenReturn(cert);

        service.updateCertificate("region-a", tenant, 7, null, null, null, "gateway");

        verify(gatewayOps, times(1)).createCertificate(eq("region-a"), eq("default"), any());
        verify(gatewayOps, never()).deleteCertificate(anyString(), anyString(), anyString(), anyString());
        verify(gatewayOps, never()).updateCertificate(anyString(), anyString(), any());
    }

    // ─────────────── updateCertificate：gateway→普通 调 deleteCertificate ─

    @Test
    void updateCertificate_gatewayToNormal_callsDelete() {
        ServiceDomainCertificate cert = new ServiceDomainCertificate();
        cert.setId(8);
        cert.setAlias("gw-switch");
        cert.setCertificateType("gateway");
        cert.setCertificate(Base64.getEncoder().encodeToString(ck.certPem().getBytes()));
        cert.setPrivateKey(ck.privateKeyPem());
        when(certRepo.findById(8)).thenReturn(Optional.of(cert));
        when(certRepo.save(any())).thenReturn(cert);

        service.updateCertificate("region-a", tenant, 8, null, null, null, "服务端证书");

        verify(gatewayOps, times(1)).deleteCertificate(
                eq("region-a"), eq("default"), eq("default-ns"), eq("gw-switch"));
        verify(gatewayOps, never()).createCertificate(anyString(), anyString(), any());
    }

    // ─────────────── checkCertificate：通配符匹配 ────────────────────────

    @Test
    void checkCertificate_wildcardMatch_pass() {
        ServiceDomainCertificate cert = new ServiceDomainCertificate();
        cert.setId(9);
        cert.setCertificate(Base64.getEncoder().encodeToString(ck.certPem().getBytes()));
        when(certRepo.findById(9)).thenReturn(Optional.of(cert));

        CertificateAnalyzer.CertInfo mockInfo = new CertificateAnalyzer.CertInfo(
                "CN=Test CA", "CN=test", java.util.List.of("*.foo.com"), null, null, "SHA256withRSA", 2048);
        when(analyzer.analyze(anyString())).thenReturn(mockInfo);

        assertEquals("pass", service.checkCertificate(9, "bar.foo.com"));
    }

    @Test
    void checkCertificate_wildcardNotMatchRoot_unpass() {
        ServiceDomainCertificate cert = new ServiceDomainCertificate();
        cert.setId(10);
        cert.setCertificate(Base64.getEncoder().encodeToString(ck.certPem().getBytes()));
        when(certRepo.findById(10)).thenReturn(Optional.of(cert));

        CertificateAnalyzer.CertInfo mockInfo = new CertificateAnalyzer.CertInfo(
                "CN=Test CA", "CN=test", java.util.List.of("*.foo.com"), null, null, "SHA256withRSA", 2048);
        when(analyzer.analyze(anyString())).thenReturn(mockInfo);

        assertEquals("un_pass", service.checkCertificate(10, "foo.com"),
                "通配符 *.foo.com 不应匹配根域 foo.com");
    }
}
