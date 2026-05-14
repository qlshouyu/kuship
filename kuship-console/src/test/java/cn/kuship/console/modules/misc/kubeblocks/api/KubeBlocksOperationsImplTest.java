package cn.kuship.console.modules.misc.kubeblocks.api;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * {@link KubeBlocksOperationsImpl} 13 method 单测：用 MockRestServiceServer 拦截 region 调用，
 * 断言 URL / HTTP method / body shape / query 拼接 / URL encode。
 *
 * <p>所有 method 共用同一个 processor + clientFactory，5xx 透传逻辑由 processor.checkStatus 统一处理；
 * 因此 5xx 透传测试只挑 3 个有代表性的 method（GET / POST / DELETE-with-body）覆盖底层异常族。
 */
class KubeBlocksOperationsImplTest {

    private MockRestServiceServer mockServer;
    private KubeBlocksOperationsImpl ops;

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

        ops = new KubeBlocksOperationsImpl(mockFactory, processor);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    // ─── 3 个 region-level GET ────────────────────────────────────────────────

    @Test
    void listSupportedDatabases_happy() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/supported-databases"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"list\":[{\"name\":\"mysql\"}]}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.listSupportedDatabases("region-1");
        assertNotNull(resp.get("list"));
    }

    @Test
    void listStorageClasses_happy() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/storage-classes"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"list\":[\"sc1\"]}}}",
                        MediaType.APPLICATION_JSON));

        ops.listStorageClasses("region-1");
    }

    @Test
    void listBackupRepos_happy() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/backup-repos"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"list\":[]}}}",
                        MediaType.APPLICATION_JSON));

        ops.listBackupRepos("region-1");
    }

    // ─── 3 个 cluster-level GET ───────────────────────────────────────────────

    @Test
    void getClusterDetail_happy() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters/svc-abc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"kubeblocks_status\":\"Running\",\"backup_config\":{}}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.getClusterDetail("region-1", "svc-abc");
        assertEquals("Running", resp.get("kubeblocks_status"));
    }

    @Test
    void listClusterBackups_happy_pagination() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters/svc-abc/backups?page=2&page_size=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"list\":[],\"total\":0}}}",
                        MediaType.APPLICATION_JSON));

        ops.listClusterBackups("region-1", "svc-abc", 2, 20);
    }

    @Test
    void getClusterPodDetail_urlEncodesPodName() {
        // pod 名含点 / 中划线 / 数字，URL encode 不丢
        mockServer.expect(requestTo(
                        "/v2/cluster/kubeblocks/clusters/svc-abc/pods/mysql-cluster-0.example.com/details"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"name\":\"mysql-cluster-0.example.com\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.getClusterPodDetail(
                "region-1", "svc-abc", "mysql-cluster-0.example.com");
        assertEquals("mysql-cluster-0.example.com", resp.get("name"));
    }

    // ─── listClusterParameters：keyword 在/不在两种情况 ─────────────────────────

    @Test
    void listClusterParameters_withKeyword_appendsQuery() {
        mockServer.expect(requestTo(
                        "/v2/cluster/kubeblocks/clusters/svc-abc/parameters?page=1&page_size=6&keyword=innodb"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"list\":[]}}}",
                        MediaType.APPLICATION_JSON));

        ops.listClusterParameters("region-1", "svc-abc", 1, 6, "innodb");
    }

    @Test
    void listClusterParameters_blankKeyword_skipped() {
        mockServer.expect(requestTo(
                        "/v2/cluster/kubeblocks/clusters/svc-abc/parameters?page=1&page_size=6"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"list\":[]}}}",
                        MediaType.APPLICATION_JSON));

        ops.listClusterParameters("region-1", "svc-abc", 1, 6, "  ");
    }

    // ─── 3 个 POST 写 method ──────────────────────────────────────────────────

    @Test
    void createCluster_happy_postsBody() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"replicas\":3")))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"service_id\":\"new-id\"}}}",
                        MediaType.APPLICATION_JSON));

        ops.createCluster("region-1",
                Map.of("type", "mysql", "replicas", 3, "version", "8.0"));
    }

    @Test
    void createManualBackup_happy_emptyBody() {
        // 无 body POST：rainbond Python `_post(url, headers, region=)` 不传 body
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters/svc-abc/backups"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"backup_id\":\"b1\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.createManualBackup("region-1", "svc-abc");
        assertEquals("b1", resp.get("backup_id"));
    }

    @Test
    void updateClusterParameters_happy_postsBody() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters/svc-abc/parameters"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.updateClusterParameters("region-1", "svc-abc",
                Map.of("parameters", List.of(Map.of("name", "max_connections", "value", "100"))));
    }

    // ─── 2 个 PUT ────────────────────────────────────────────────────────────

    @Test
    void expansionCluster_happy_putsBody() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters/svc-abc"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"cpu\":\"1000m\"")))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.expansionCluster("region-1", "svc-abc",
                Map.of("replicas", 3, "cpu", "1000m", "memory", "2Gi"));
    }

    @Test
    void updateBackupConfig_happy_putsBody() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters/svc-abc/backup-schedules"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.updateBackupConfig("region-1", "svc-abc",
                Map.of("schedule", "0 2 * * *", "retention", 7));
    }

    // ─── 2 个 DELETE with body ───────────────────────────────────────────────

    @Test
    void deleteCluster_happy_deletesWithBody() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"service_ids\":[\"sid1\"]")))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.deleteCluster("region-1",
                Map.of("service_ids", List.of("sid1"), "delete_pvc", true, "delete_backup", false));
    }

    @Test
    void deleteClusterBackups_happy_bodyShape() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters/svc-abc/backups"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"backups\":[\"b1\",\"b2\"]")))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.deleteClusterBackups("region-1", "svc-abc", List.of("b1", "b2"));
    }

    // ─── 5xx 透传：GET / POST / DELETE 三类各 1 个 ──────────────────────────────

    @Test
    void listSupportedDatabases_5xx_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/supported-databases"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"code\":503,\"msg\":\"region down\",\"msg_show\":\"集群不可用\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.listSupportedDatabases("region-1"));
        assertEquals(503, ex.getCode());
        assertEquals("集群不可用", ex.getMsgShow());
    }

    @Test
    void createCluster_5xx_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"code\":400,\"msg\":\"db type unsupported\",\"msg_show\":\"数据库类型不支持\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.createCluster("region-1", Map.of("type", "unknown")));
        assertEquals(400, ex.getCode());
        assertEquals("数据库类型不支持", ex.getMsgShow());
    }

    @Test
    void deleteClusterBackups_5xx_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/cluster/kubeblocks/clusters/svc-abc/backups"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"code\":503,\"msg\":\"region down\",\"msg_show\":\"集群不可用\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RegionApiException.class,
                () -> ops.deleteClusterBackups("region-1", "svc-abc", List.of("b1")));
    }
}
