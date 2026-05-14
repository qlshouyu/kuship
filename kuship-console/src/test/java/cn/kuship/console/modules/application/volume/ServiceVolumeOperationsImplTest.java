package cn.kuship.console.modules.application.volume;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.api.ServiceVolumeOperations;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.application.api.ServiceVolumeOperationsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ServiceVolumeOperationsImpl 单元测试：通过 MockRestServiceServer 拦截 region HTTP 调用，
 * 验证 6 个新增 method 各自的 URL 路径、HTTP 方法与响应解析是否正确。
 *
 * <p>已有 3 个 method（addVolumes / deleteVolumes / upgradeVolumes）由 AppVolumeController 集成测试覆盖；
 * 本测试专注 migrate-console-volume-extras 新增的 6 个 method。
 */
class ServiceVolumeOperationsImplTest {

    private static final String REGION = "region-1";
    private static final String TENANT = "my-team";
    private static final String ALIAS = "my-svc";

    private MockRestServiceServer mockServer;
    private ServiceVolumeOperations volumeOperations;

    @BeforeEach
    void setUp() {
        org.springframework.web.client.RestTemplate restTemplate =
                new org.springframework.web.client.RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.create(restTemplate);

        RegionClientFactory mockFactory = mock(RegionClientFactory.class);
        RegionInfoDto regionInfo = new RegionInfoDto("rid", REGION, "Region 1", "[]",
                "https://mock-region", "wss://mock-region", "", "", "", "1", "private",
                null, null, null, "ent-1", null, null);
        RegionClient regionClient = new RegionClient(regionInfo, restClient, null);
        when(mockFactory.getClient(anyString(), anyString())).thenReturn(regionClient);

        tools.jackson.databind.ObjectMapper json =
                tools.jackson.databind.json.JsonMapper.builder().build();
        RegionProperties props = new RegionProperties(5, false, 0,
                List.of("操作过于频繁，请稍后再试"));
        RegionErrorMsgEnricher enricher = new RegionErrorMsgEnricher();
        RegionApiResponseProcessor processor = new RegionApiResponseProcessor(json, props, enricher);

        volumeOperations = new ServiceVolumeOperationsImpl(mockFactory, processor);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    // ─── getVolumeOptions ───────────────────────────────────────────────────

    @Test
    void getVolumeOptions_callsCorrectUrl() {
        mockServer.expect(requestTo("/v2/volume-options"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"options\":[\"rbd\",\"nfs\"]}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = volumeOperations.getVolumeOptions(REGION, TENANT);
        assertNotNull(result);
    }

    // ─── getVolumes ─────────────────────────────────────────────────────────

    @Test
    void getVolumes_callsCorrectUrl() {
        mockServer.expect(requestTo("/v2/tenants/my-team/services/my-svc/volumes"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"volume_list\":[]},\"list\":[]}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = volumeOperations.getVolumes(REGION, TENANT, ALIAS);
        assertNotNull(result);
    }

    // ─── getVolumeStatus ────────────────────────────────────────────────────

    @Test
    void getVolumeStatus_callsVolumesStatusUrl() {
        mockServer.expect(requestTo("/v2/tenants/my-team/services/my-svc/volumes-status"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"status\":\"ready\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = volumeOperations.getVolumeStatus(REGION, TENANT, ALIAS);
        assertNotNull(result);
    }

    // ─── getDepVolumes ──────────────────────────────────────────────────────

    @Test
    void getDepVolumes_callsDepvolumesUrl() {
        mockServer.expect(requestTo("/v2/tenants/my-team/services/my-svc/depvolumes"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{},\"list\":[]}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = volumeOperations.getDepVolumes(REGION, TENANT, ALIAS);
        assertNotNull(result);
    }

    // ─── addDepVolumes ──────────────────────────────────────────────────────

    @Test
    void addDepVolumes_postsToDepvolumesUrl() {
        mockServer.expect(requestTo("/v2/tenants/my-team/services/my-svc/depvolumes"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> body = Map.of(
                "depend_service_id", "dep-svc-id",
                "volume_name", "vol-1",
                "volume_path", "/mnt/vol1",
                "enterprise_id", "ent-1"
        );
        Map<String, Object> result = volumeOperations.addDepVolumes(REGION, TENANT, ALIAS, body);
        assertNotNull(result);
    }

    // ─── deleteDepVolumes ───────────────────────────────────────────────────

    @Test
    void deleteDepVolumes_sendsDeleteWithBody() {
        mockServer.expect(requestTo("/v2/tenants/my-team/services/my-svc/depvolumes"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> body = Map.of(
                "depend_service_id", "dep-svc-id",
                "volume_name", "vol-1"
        );
        assertDoesNotThrow(() -> volumeOperations.deleteDepVolumes(REGION, TENANT, ALIAS, body));
    }
}
