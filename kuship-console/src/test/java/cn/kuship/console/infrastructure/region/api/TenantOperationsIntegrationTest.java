package cn.kuship.console.infrastructure.region.api;

import cn.kuship.console.infrastructure.region.api.dto.CreateTenantReq;
import cn.kuship.console.infrastructure.region.api.dto.RegionLabelsResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionPublickeyResp;
import cn.kuship.console.infrastructure.region.api.dto.TenantResourcesResp;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * TenantOperations 集成测试：用 {@link MockRestServiceServer} 拦截真实 HTTP 调用，
 * 验证 5 个 method 各自的 URL/HTTP method/body 序列化与响应反序列化路径。
 */
class TenantOperationsIntegrationTest {

    private MockRestServiceServer mockServer;
    private TenantOperationsImpl tenantOperations;

    @BeforeEach
    void setUp() {
        // RestClient 经 RestClient.builder() 构造时无法直接接 MockRestServiceServer；
        // 用底层 RestTemplate 适配的方式构造。
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.create(restTemplate);

        // 用 mock factory 让 getClient 返回我们用的 RestClient
        RegionClientFactory mockFactory = mock(RegionClientFactory.class);
        RegionInfoDto regionInfo = new RegionInfoDto("rid", "region-1", "Region 1", "[]",
                "https://mock-region", "wss://mock-region", "", "", "", "1", "private",
                null, null, null, "ent-1", null, null);
        RegionClient regionClient = new RegionClient(regionInfo, restClient, null);
        when(mockFactory.getClient(anyString(), anyString())).thenReturn(regionClient);

        // 真实的 ResponseProcessor
        tools.jackson.databind.ObjectMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
        cn.kuship.console.infrastructure.region.RegionProperties props = new cn.kuship.console.infrastructure.region.RegionProperties(
                5, false, 0, java.util.List.of("操作过于频繁，请稍后再试"));
        cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher enricher =
                new cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher();
        RegionApiResponseProcessor processor = new RegionApiResponseProcessor(json, props, enricher);

        tenantOperations = new TenantOperationsImpl(mockFactory, processor);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void createTenant_postsBodyAndReturnsBean() {
        mockServer.expect(requestTo("/v2/tenants"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"msg\":\"success\","
                                + "\"data\":{\"bean\":{\"region\":\"region-1\",\"namespace\":\"my-ns\","
                                + "\"limit_memory\":2048}}}",
                        MediaType.APPLICATION_JSON));

        TenantResourcesResp resp = tenantOperations.createTenant("region-1", "ent-1",
                new CreateTenantReq("name", "id", "ent-1", "my-ns", false));
        assertEquals("region-1", resp.region());
        assertEquals("my-ns", resp.namespace());
        assertEquals(2048L, resp.limitMemory());
    }

    @Test
    void deleteTenant_sendsDelete() {
        mockServer.expect(requestTo("/v2/tenants/team-x"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"code\":200,\"msg\":\"success\",\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        tenantOperations.deleteTenant("region-1", "ent-1", "team-x");
    }

    @Test
    void getTenantResources_appendsEnterpriseIdQuery() {
        mockServer.expect(requestTo("/v2/tenants/team-x/res?enterprise_id=ent-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"msg\":\"success\","
                                + "\"data\":{\"bean\":{\"region\":\"region-1\",\"namespace\":\"team-x\"}}}",
                        MediaType.APPLICATION_JSON));

        TenantResourcesResp resp = tenantOperations.getTenantResources("region-1", "ent-1", "team-x");
        assertEquals("team-x", resp.namespace());
    }

    @Test
    void getRegionPublickey_appendsBothQueryParams() {
        mockServer.expect(requestTo("/v2/tenants/team-x/region-key?enterprise_id=ent-1&tenant_id=tid"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"public_key\":\"PUB\"}}}",
                        MediaType.APPLICATION_JSON));

        RegionPublickeyResp resp = tenantOperations.getRegionPublickey("region-1", "ent-1", "team-x", "tid");
        assertEquals("PUB", resp.publicKey());
    }

    @Test
    void getRegionLabels_returnsList() {
        mockServer.expect(requestTo("/v2/tenants/team-x/labels"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"labels\":[\"windows\",\"ssd\"]}}}",
                        MediaType.APPLICATION_JSON));

        RegionLabelsResp resp = tenantOperations.getRegionLabels("region-1", "ent-1", "team-x");
        assertEquals(2, resp.labels().size());
        assertEquals("windows", resp.labels().get(0));
    }

    @Test
    void serverError409_throwsRegionApiException_passesUpstreamMsgShow() {
        mockServer.expect(requestTo("/v2/tenants"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT)
                        .body("{\"code\":409,\"msg\":\"tenant already exists\",\"msg_show\":\"团队已存在\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> tenantOperations.createTenant("region-1", "ent-1",
                        new CreateTenantReq("dup", "id", "ent-1", "ns", false)));
        assertEquals(409, ex.getCode());
        assertEquals("团队已存在", ex.getMsgShow());
    }
}
