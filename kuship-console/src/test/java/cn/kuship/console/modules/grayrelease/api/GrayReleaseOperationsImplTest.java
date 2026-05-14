package cn.kuship.console.modules.grayrelease.api;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link GrayReleaseOperationsImpl} 3 method 单测。
 */
class GrayReleaseOperationsImplTest {

    private MockRestServiceServer mockServer;
    private GrayReleaseOperationsImpl ops;
    private TenantsRepository tenantsRepo;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.create(restTemplate);

        RegionClientFactory mockFactory = mock(RegionClientFactory.class);
        RegionInfoDto regionInfo = new RegionInfoDto("rid", "region-1", "Region 1", "[]",
                "https://mock-region", "wss://mock-region", "", "", "", "1", "private",
                null, null, null, "ent-1", null, null);
        RegionClient regionClient = new RegionClient(regionInfo, restClient, null);
        when(mockFactory.getClient(anyString(), anyString())).thenReturn(regionClient);

        tools.jackson.databind.ObjectMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
        RegionProperties props = new RegionProperties(5, false, 0,
                List.of("操作过于频繁，请稍后再试"));
        RegionErrorMsgEnricher enricher = new RegionErrorMsgEnricher();
        RegionApiResponseProcessor processor = new RegionApiResponseProcessor(json, props, enricher);

        tenantsRepo = mock(TenantsRepository.class);
        ops = new GrayReleaseOperationsImpl(mockFactory, processor, tenantsRepo);

        Tenants t = new Tenants();
        t.setTenantName("default");
        t.setNamespace("my-ns");
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void createAppGrayRelease_happy_postsBodyWithTemplateId() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/apps/123/gray_release"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"template_id\":\"t1\"")))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"original_service_id\":\"o1\",\"gray_service_id\":\"g1\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.createAppGrayRelease("region-1", "default", 123,
                Map.of("template_id", "t1", "version", "1.0"));
        assertEquals("o1", resp.get("original_service_id"));
        assertEquals("g1", resp.get("gray_service_id"));
    }

    @Test
    void createAppGrayRelease_namespace_fallback_to_tenantName() {
        Tenants t = new Tenants();
        t.setTenantName("default2");
        t.setNamespace(null);
        when(tenantsRepo.findByTenantName("default2")).thenReturn(Optional.of(t));

        mockServer.expect(requestTo("/v2/tenants/default2/apps/123/gray_release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.createAppGrayRelease("region-1", "default2", 123, Map.of());
    }

    @Test
    void createAppGrayRelease_5xx_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/apps/123/gray_release"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"code\":503,\"msg\":\"region down\",\"msg_show\":\"集群不可用\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.createAppGrayRelease("region-1", "default", 123, Map.of("template_id", "t1")));
        assertEquals(503, ex.getCode());
    }

    @Test
    void updateAppGrayRelease_happy_putsBody() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/apps/123/gray_release"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"gray_ratio\":70")))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.updateAppGrayRelease("region-1", "default", 123, Map.of("gray_ratio", 70));
    }

    @Test
    void operateAppGrayRelease_happy_rollback() {
        mockServer.expect(requestTo(
                        "/v2/tenants/my-ns/apps/123/operate_gray_release"
                        + "?namespace=my-ns&app_id=123&operation_method=rollback"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.operateAppGrayRelease("region-1", "default", 123, "my-ns", "rollback");
    }

    @Test
    void operateAppGrayRelease_namespace_resolved_from_tenantName_when_blank() {
        mockServer.expect(requestTo(
                        "/v2/tenants/my-ns/apps/123/operate_gray_release"
                        + "?namespace=my-ns&app_id=123&operation_method=rollback"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.operateAppGrayRelease("region-1", "default", 123, "", "rollback");
    }
}
