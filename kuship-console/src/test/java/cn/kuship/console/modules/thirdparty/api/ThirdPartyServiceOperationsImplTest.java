package cn.kuship.console.modules.thirdparty.api;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ThirdPartyServiceOperationsImpl 6 method 单测：
 * 用 MockRestServiceServer 拦截 region 调用，断言 URL / namespace fallback / Resource-Validation header 透传。
 */
class ThirdPartyServiceOperationsImplTest {

    private MockRestServiceServer mockServer;
    private ThirdPartyServiceOperationsImpl ops;
    private TenantsRepository tenantsRepo;

    @BeforeEach
    void setUp() {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
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
                java.util.List.of("操作过于频繁，请稍后再试"));
        RegionErrorMsgEnricher enricher = new RegionErrorMsgEnricher();
        RegionApiResponseProcessor processor = new RegionApiResponseProcessor(json, props, enricher);

        tenantsRepo = mock(TenantsRepository.class);
        ops = new ThirdPartyServiceOperationsImpl(mockFactory, processor, tenantsRepo);

        // 默认 tenant lookup
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
    void getEndpoints_noResourceValidationHeader() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/services/svc1/endpoints"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("Resource-Validation"))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"endpoints\":[{\"address\":\"10.0.0.1:80\"}]}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.getEndpoints("region-1", "default", "svc1");
        assertNotNull(resp.get("endpoints"));
    }

    @Test
    void postEndpoints_setsResourceValidationHeader() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/services/svc1/endpoints"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Resource-Validation", "true"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.postEndpoints("region-1", "default", "svc1",
                Map.of("address", "10.0.0.1:80", "is_online", true));
    }

    @Test
    void putEndpoints_setsResourceValidationHeader() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/services/svc1/endpoints"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Resource-Validation", "true"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.putEndpoints("region-1", "default", "svc1",
                Map.of("ep_id", "abc", "is_online", false));
    }

    @Test
    void deleteEndpoints_setsResourceValidationHeader() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/services/svc1/endpoints"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Resource-Validation", "true"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.deleteEndpoints("region-1", "default", "svc1", Map.of("ep_id", "abc"));
    }

    @Test
    void getHealth_noResourceValidationHeader() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/services/svc1/3rd-party/probe"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("Resource-Validation"))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"mode\":\"tcp\",\"port\":80}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.getHealth("region-1", "default", "svc1");
        assertEquals("tcp", resp.get("mode"));
        assertEquals(80, ((Number) resp.get("port")).intValue());
    }

    @Test
    void putHealth_noResourceValidationHeader() {
        // putHealth 与 endpoints 写不同：rainbond Python `_set_headers` 没传 resource_validation 给 health
        mockServer.expect(requestTo("/v2/tenants/my-ns/services/svc1/3rd-party/probe"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(headerDoesNotExist("Resource-Validation"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.putHealth("region-1", "default", "svc1",
                Map.of("mode", "tcp", "port", 80, "period", 30));
    }

    @Test
    void namespaceFallbackToTenantNameWhenBlank() {
        Tenants t = new Tenants();
        t.setTenantName("default2");
        t.setNamespace(null);
        when(tenantsRepo.findByTenantName("default2")).thenReturn(Optional.of(t));

        mockServer.expect(requestTo("/v2/tenants/default2/services/svc1/endpoints"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.getEndpoints("region-1", "default2", "svc1");
    }

    @Test
    void teamNotFound_throws404() {
        when(tenantsRepo.findByTenantName("missing")).thenReturn(Optional.empty());

        cn.kuship.console.common.exception.ServiceHandleException ex = assertThrows(
                cn.kuship.console.common.exception.ServiceHandleException.class,
                () -> ops.getEndpoints("region-1", "missing", "svc1"));
        assertEquals(404, ex.getCode());
        assertEquals("团队不存在", ex.getMsgShow());
    }

    @Test
    void postEndpoints_5xx_passesThroughRegionApiException() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/services/svc1/endpoints"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"code\":503,\"msg\":\"region down\",\"msg_show\":\"集群不可用\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.postEndpoints("region-1", "default", "svc1",
                        Map.of("address", "10.0.0.1:80")));
        assertEquals(503, ex.getCode());
        assertEquals("集群不可用", ex.getMsgShow());
    }
}
