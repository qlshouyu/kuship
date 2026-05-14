package cn.kuship.console.modules.application.api;

import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link ServiceDependencyOperationsImpl} 新增 3 个 method 的单测：
 * addDependencies / addVolumeDependency / deleteVolumeDependency
 *
 * <p>使用 {@link MockRestServiceServer} 拦截 HTTP，重点断言：
 * <ul>
 *   <li>{@code addDependencies} 路径含 {@code dependencys}（rainbond 历史拼写）</li>
 *   <li>{@code addVolumeDependency} / {@code deleteVolumeDependency} 路径含 {@code volume-dependency}</li>
 *   <li>region 5xx 时三个 method 均抛 {@link RegionApiException}</li>
 * </ul>
 */
class ServiceDependencyOperationsImplExtraTest {

    private MockRestServiceServer mockServer;
    private ServiceDependencyOperationsImpl ops;

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
        cn.kuship.console.infrastructure.region.RegionProperties props =
                new cn.kuship.console.infrastructure.region.RegionProperties(
                        5, false, 0, List.of("操作过于频繁，请稍后再试"));
        cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher enricher =
                new cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher();
        RegionApiResponseProcessor processor = new RegionApiResponseProcessor(json, props, enricher);

        ops = new ServiceDependencyOperationsImpl(mockFactory, processor);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    // ===== addDependencies =====

    @Test
    void addDependencies_happy_usesCorrectDependencysSpelling() {
        // 断言路径拼写是 dependencys（不是 dependencies）
        mockServer.expect(requestTo("/v2/tenants/team-ns/services/svc-alias/dependencys"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"msg\":\"success\",\"data\":{\"bean\":{\"result\":\"ok\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> body = Map.of("dep_service_ids", List.of("dep1", "dep2"), "tenant_id", "team-ns");
        Map<String, Object> result = ops.addDependencies("region-1", "team-ns", "svc-alias", body);

        assertEquals("ok", result.get("result"));
    }

    @Test
    void addDependencies_region5xx_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/tenants/team-ns/services/svc-alias/dependencys"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":500,\"msg\":\"internal error\",\"msg_show\":\"集群内部错误\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.addDependencies("region-1", "team-ns", "svc-alias", Map.of()));
        assertEquals(500, ex.getCode());
        assertEquals("集群内部错误", ex.getMsgShow());
    }

    // ===== addVolumeDependency =====

    @Test
    void addVolumeDependency_happy_postsToVolumeDependencyPath() {
        mockServer.expect(requestTo("/v2/tenants/team-ns/services/svc-alias/volume-dependency"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"msg\":\"success\",\"data\":{\"bean\":{\"mounted\":true}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> body = Map.of("volume_name", "vol1", "volume_path", "/data");
        Map<String, Object> result = ops.addVolumeDependency("region-1", "team-ns", "svc-alias", body);

        assertEquals(true, result.get("mounted"));
    }

    @Test
    void addVolumeDependency_region5xx_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/tenants/team-ns/services/svc-alias/volume-dependency"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":500,\"msg\":\"volume error\",\"msg_show\":\"挂载依赖失败\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.addVolumeDependency("region-1", "team-ns", "svc-alias", Map.of()));
        assertEquals(500, ex.getCode());
    }

    // ===== deleteVolumeDependency =====

    @Test
    void deleteVolumeDependency_happy_sendsDeleteWithBody() {
        mockServer.expect(requestTo("/v2/tenants/team-ns/services/svc-alias/volume-dependency"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess(
                        "{\"code\":200,\"msg\":\"success\",\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        // 不抛异常即为成功
        assertDoesNotThrow(() ->
                ops.deleteVolumeDependency("region-1", "team-ns", "svc-alias", Map.of("volume_name", "vol1")));
    }

    @Test
    void deleteVolumeDependency_region5xx_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/tenants/team-ns/services/svc-alias/volume-dependency"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":500,\"msg\":\"delete error\",\"msg_show\":\"删除挂载依赖失败\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.deleteVolumeDependency("region-1", "team-ns", "svc-alias", Map.of()));
        assertEquals(500, ex.getCode());
    }
}
