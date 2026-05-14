package cn.kuship.console.modules.application.governance.api;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GovernanceModeOperationsImplTest {

    private MockRestServiceServer mockServer;
    private GovernanceModeOperationsImpl ops;

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
        RegionApiResponseProcessor processor = new RegionApiResponseProcessor(json, props,
                new RegionErrorMsgEnricher());

        ops = new GovernanceModeOperationsImpl(mockFactory, processor);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void listGovernanceMode_happy() {
        mockServer.expect(requestTo("/v2/cluster/governance-mode"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"list\":[{\"name\":\"istio\"}]}}}",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> list = ops.listGovernanceMode("region-1", "team-1");
        assertEquals(1, list.size());
        assertEquals("istio", list.get(0).get("name"));
    }

    @Test
    void check_happy_passesQueryParam() {
        mockServer.expect(requestTo("/v2/tenants/team-1/apps/app-1/governance/check?governance_mode=istio"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"governance_mode\":\"istio\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.checkAppGovernanceMode("region-1", "team-1", "app-1", "istio");
        assertEquals("istio", resp.get("governance_mode"));
    }

    @Test
    void check_412_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/tenants/team-1/apps/app-1/governance/check?governance_mode=istio"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.PRECONDITION_FAILED)
                        .body("{\"code\":412,\"msg\":\"mesh not installed\",\"msg_show\":\"mesh 未安装\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.checkAppGovernanceMode("region-1", "team-1", "app-1", "istio"));
        assertEquals(412, ex.getCode());
    }

    @Test
    void createGovernanceCr_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/apps/app-1/governance-cr"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"name\":\"cr-1\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.createGovernanceCr("region-1", "team-1", "app-1",
                Map.of("kind", "DestinationRule"));
        assertEquals("cr-1", resp.get("name"));
    }

    @Test
    void updateGovernanceCr_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/apps/app-1/governance-cr"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.updateGovernanceCr("region-1", "team-1", "app-1", Map.of("kind", "VirtualService"));
    }

    @Test
    void deleteGovernanceCr_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/apps/app-1/governance-cr"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.deleteGovernanceCr("region-1", "team-1", "app-1");
    }

    @Test
    void deleteGovernanceCr_500_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/apps/app-1/governance-cr"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":500,\"msg\":\"err\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RegionApiException.class,
                () -> ops.deleteGovernanceCr("region-1", "team-1", "app-1"));
    }
}
