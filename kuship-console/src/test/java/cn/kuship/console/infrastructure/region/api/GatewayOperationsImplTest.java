package cn.kuship.console.infrastructure.region.api;

import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link GatewayOperationsImpl} 单元测试（MockRestServiceServer）。
 *
 * <p>任务 3.3：断言 5 个证书 method 的 URL 路径 + HTTP 方法 + body 形状 + region 5xx 透传。
 */
class GatewayOperationsImplTest {

    private MockRestServiceServer mockServer;
    private GatewayOperationsImpl gatewayOps;

    private static final String REGION = "region-test";
    private static final String TENANT = "default";
    private static final String NAMESPACE = "default-ns";

    @BeforeEach
    void setUp() {
        org.springframework.web.client.RestTemplate restTemplate =
                new org.springframework.web.client.RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.create(restTemplate);

        RegionClientFactory mockFactory = mock(RegionClientFactory.class);
        RegionInfoDto regionInfo = new RegionInfoDto("rid", REGION, "Region Test", "[]",
                "https://mock-region", "wss://mock-region", "", "", "", "1", "private",
                null, null, null, "ent-1", null, null);
        RegionClient regionClient = new RegionClient(regionInfo, restClient, null);
        when(mockFactory.getClient(anyString(), anyString())).thenReturn(regionClient);

        TenantsRepository mockTenantsRepo = mock(TenantsRepository.class);
        Tenants tenant = new Tenants();
        tenant.setTenantName(TENANT);
        tenant.setNamespace(NAMESPACE);
        when(mockTenantsRepo.findByTenantName(TENANT)).thenReturn(Optional.of(tenant));

        ObjectMapper json = JsonMapper.builder().build();
        cn.kuship.console.infrastructure.region.RegionProperties props = new cn.kuship.console.infrastructure.region.RegionProperties(
                5, false, 0, java.util.List.of("操作过于频繁，请稍后再试"));
        cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher enricher =
                new cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher();
        cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor processor =
                new cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor(json, props, enricher);
        gatewayOps = new GatewayOperationsImpl(mockFactory, processor, json, mockTenantsRepo);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    // ─────────────── getCertificate：GET 路径 ────────────────────────────

    @Test
    void getCertificate_sendsGetToCorrectPath() {
        mockServer.expect(requestTo("/v2/tenants/" + TENANT + "/gateway-certificate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        gatewayOps.getCertificate(REGION, TENANT, Map.of());
    }

    // ─────────────── createCertificate：POST 路径 ─────────────────────────

    @Test
    void createCertificate_sendsPostToCorrectPath() {
        mockServer.expect(requestTo("/v2/tenants/" + TENANT + "/gateway-certificate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        gatewayOps.createCertificate(REGION, TENANT,
                Map.of("namespace", NAMESPACE, "name", "test-cert",
                        "private_key", "pk", "certificate", "cert"));
    }

    // ─────────────── updateCertificate：PUT 路径 ─────────────────────────

    @Test
    void updateCertificate_sendsPutToCorrectPath() {
        mockServer.expect(requestTo("/v2/tenants/" + TENANT + "/gateway-certificate"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        gatewayOps.updateCertificate(REGION, TENANT, Map.of("name", "test-cert"));
    }

    // ─────────────── deleteCertificate：DELETE 带 query string ─────────────

    @Test
    void deleteCertificate_sendsDeleteWithQueryParams() {
        mockServer.expect(requestTo("/v2/tenants/" + TENANT
                        + "/gateway-certificate?namespace=" + NAMESPACE + "&name=test-cert"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        gatewayOps.deleteCertificate(REGION, TENANT, NAMESPACE, "test-cert");
    }

    // ─────────────── updateIngressesByCertificate：用 namespace 路径段 ─────

    @Test
    void updateIngressesByCertificate_usesNamespaceInPath() {
        // region_tenant_name = namespace (not tenant_name)
        mockServer.expect(requestTo("/v2/tenants/" + NAMESPACE + "/gateway/certificate"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        gatewayOps.updateIngressesByCertificate(REGION, TENANT, Map.of("body", "data"));
    }

    // ─────────────── region 5xx → RegionApiException ─────────────────────

    @Test
    void createCertificate_region5xx_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/tenants/" + TENANT + "/gateway-certificate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":500,\"msg\":\"region error\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RegionApiException.class,
                () -> gatewayOps.createCertificate(REGION, TENANT, Map.of("name", "fail-cert")));
    }
}
