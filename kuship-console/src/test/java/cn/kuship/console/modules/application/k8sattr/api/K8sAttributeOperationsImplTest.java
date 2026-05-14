package cn.kuship.console.modules.application.k8sattr.api;

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

class K8sAttributeOperationsImplTest {

    private MockRestServiceServer mockServer;
    private K8sAttributeOperationsImpl ops;

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

        ops = new K8sAttributeOperationsImpl(mockFactory, processor);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void getK8sAttribute_getWithBody_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/k8s-attributes"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"list\":[{\"name\":\"nodeSelector\"}]}}}",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> list = ops.getK8sAttribute("region-1", "team-1", "svc1",
                Map.of("name", "nodeSelector"));
        assertEquals(1, list.size());
        assertEquals("nodeSelector", list.get(0).get("name"));
    }

    @Test
    void getK8sAttribute_5xx_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/k8s-attributes"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":500,\"msg\":\"err\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RegionApiException.class,
                () -> ops.getK8sAttribute("region-1", "team-1", "svc1", Map.of()));
    }

    @Test
    void createK8sAttribute_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/k8s-attributes"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"name\":\"nodeSelector\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.createK8sAttribute("region-1", "team-1", "svc1",
                Map.of("attribute", Map.of("name", "nodeSelector", "save_type", "yaml",
                        "attribute_value", "key: gpu")));
        assertEquals("nodeSelector", resp.get("name"));
    }

    @Test
    void createK8sAttribute_409_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/k8s-attributes"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT)
                        .body("{\"code\":409,\"msg\":\"name exists\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RegionApiException.class,
                () -> ops.createK8sAttribute("region-1", "team-1", "svc1", Map.of()));
    }

    @Test
    void updateK8sAttribute_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/k8s-attributes"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.updateK8sAttribute("region-1", "team-1", "svc1", Map.of("attribute", Map.of("name", "x")));
    }

    @Test
    void updateK8sAttribute_400_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/k8s-attributes"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .body("{\"code\":400,\"msg\":\"err\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RegionApiException.class,
                () -> ops.updateK8sAttribute("region-1", "team-1", "svc1", Map.of()));
    }

    @Test
    void deleteK8sAttribute_deleteWithBody_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/k8s-attributes"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.deleteK8sAttribute("region-1", "team-1", "svc1", Map.of("name", "nodeSelector"));
    }

    @Test
    void deleteK8sAttribute_404_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/services/svc1/k8s-attributes"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body("{\"code\":404,\"msg\":\"not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RegionApiException.class,
                () -> ops.deleteK8sAttribute("region-1", "team-1", "svc1", Map.of("name", "x")));
    }
}
