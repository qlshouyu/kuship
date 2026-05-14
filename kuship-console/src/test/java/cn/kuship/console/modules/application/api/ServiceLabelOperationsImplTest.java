package cn.kuship.console.modules.application.api;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 4 region method × 1 happy + 1 错误 = 8 用例。 */
class ServiceLabelOperationsImplTest {

    private MockRestServiceServer mockServer;
    private ServiceLabelOperationsImpl ops;

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

        ops = new ServiceLabelOperationsImpl(mockFactory, processor);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void listRegionLabels_happy() {
        mockServer.expect(requestTo("/v2/resources/labels"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"list\":[{\"label_id\":\"l1\",\"label_alias\":\"GPU\"}]}}}",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> list = ops.listRegionLabels("region-1", "team-1");
        assertEquals(1, list.size());
        assertEquals("l1", list.get(0).get("label_id"));
    }

    @Test
    void listRegionLabels_emptyList() {
        mockServer.expect(requestTo("/v2/resources/labels"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"list\":[]}}}",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> list = ops.listRegionLabels("region-1", "team-1");
        assertTrue(list.isEmpty());
    }

    @Test
    void addServiceNodeLabel_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/label"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"event_id\":\"e1\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.addServiceNodeLabel("region-1", "team-1", "svc1",
                Map.of("label_ids", List.of("l1", "l2")));
        assertEquals("e1", resp.get("event_id"));
    }

    @Test
    void addServiceNodeLabel_500_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/label"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":500,\"msg\":\"oops\",\"msg_show\":\"region 故障\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.addServiceNodeLabel("region-1", "team-1", "svc1",
                        Map.of("label_ids", List.of("l1"))));
        assertEquals(500, ex.getCode());
    }

    @Test
    void deleteServiceNodeLabel_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/label"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.deleteServiceNodeLabel("region-1", "team-1", "svc1", Map.of("label_id", "l1"));
    }

    @Test
    void deleteServiceNodeLabel_404_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/label"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body("{\"code\":404,\"msg\":\"label not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.deleteServiceNodeLabel("region-1", "team-1", "svc1", Map.of("label_id", "l1")));
        assertEquals(404, ex.getCode());
    }

    @Test
    void updateServiceStateLabel_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/label"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.updateServiceStateLabel("region-1", "team-1", "svc1",
                Map.of("label_ids", List.of("stateful")));
    }

    @Test
    void updateServiceStateLabel_500_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/label"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":500,\"msg\":\"err\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.updateServiceStateLabel("region-1", "team-1", "svc1", Map.of()));
        assertEquals(500, ex.getCode());
    }
}
