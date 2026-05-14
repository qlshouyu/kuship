package cn.kuship.console.modules.region.maven.api;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 5 method × 2 = 10 用例。 */
class MavenSettingOperationsImplTest {

    private MockRestServiceServer mockServer;
    private MavenSettingOperationsImpl ops;

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

        ops = new MavenSettingOperationsImpl(mockFactory, processor);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void list_onlyName_projectsToNameAndIsDefault() {
        mockServer.expect(requestTo("/v2/cluster/builder/mavensetting"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"list\":[{\"name\":\"m1\",\"is_default\":true,"
                                + "\"content\":\"<settings>...</settings>\"},"
                                + "{\"name\":\"m2\",\"is_default\":false,\"content\":\"<settings>x</settings>\"}]}}}",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> list = ops.listMavenSettings("ent-1", "region-1", true);
        assertEquals(2, list.size());
        assertEquals("m1", list.get(0).get("name"));
        assertEquals(true, list.get(0).get("is_default"));
        assertFalse(list.get(0).containsKey("content"));
    }

    @Test
    void list_full_returnsCompleteContent() {
        mockServer.expect(requestTo("/v2/cluster/builder/mavensetting"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"list\":[{\"name\":\"m1\",\"is_default\":true,\"content\":\"<settings>full</settings>\"}]}}}",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> list = ops.listMavenSettings("ent-1", "region-1", false);
        assertEquals(1, list.size());
        assertEquals("<settings>full</settings>", list.get(0).get("content"));
    }

    @Test
    void add_happy() {
        mockServer.expect(requestTo("/v2/cluster/builder/mavensetting"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"name\":\"m1\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.addMavenSetting("ent-1", "region-1",
                Map.of("name", "m1", "content", "<settings/>"));
        assertEquals("m1", resp.get("name"));
    }

    @Test
    void add_400_nameExists_throws() {
        mockServer.expect(requestTo("/v2/cluster/builder/mavensetting"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .body("{\"code\":400,\"msg\":\"name exists\",\"msg_show\":\"配置名称已存在\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.addMavenSetting("ent-1", "region-1", Map.of("name", "m1")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void get_happy() {
        mockServer.expect(requestTo("/v2/cluster/builder/mavensetting/m1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"name\":\"m1\",\"content\":\"<x/>\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.getMavenSetting("ent-1", "region-1", "m1");
        assertEquals("m1", resp.get("name"));
    }

    @Test
    void get_404_throws() {
        mockServer.expect(requestTo("/v2/cluster/builder/mavensetting/m1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body("{\"code\":404,\"msg\":\"not found\",\"msg_show\":\"配置不存在\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.getMavenSetting("ent-1", "region-1", "m1"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void update_happy() {
        mockServer.expect(requestTo("/v2/cluster/builder/mavensetting/m1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"name\":\"m1\"}}}",
                        MediaType.APPLICATION_JSON));

        ops.updateMavenSetting("ent-1", "region-1", "m1", Map.of("content", "<new/>"));
    }

    @Test
    void update_404_throws() {
        mockServer.expect(requestTo("/v2/cluster/builder/mavensetting/m1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body("{\"code\":404,\"msg\":\"not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RegionApiException.class,
                () -> ops.updateMavenSetting("ent-1", "region-1", "m1", Map.of()));
    }

    @Test
    void delete_happy() {
        mockServer.expect(requestTo("/v2/cluster/builder/mavensetting/m1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.deleteMavenSetting("ent-1", "region-1", "m1");
    }

    @Test
    void delete_404_throws() {
        mockServer.expect(requestTo("/v2/cluster/builder/mavensetting/m1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body("{\"code\":404,\"msg\":\"not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RegionApiException.class,
                () -> ops.deleteMavenSetting("ent-1", "region-1", "m1"));
    }
}
