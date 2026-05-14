package cn.kuship.console.infrastructure.region.api;

import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.region.api.ClusterOperationsImpl;
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
 * ClusterOperationsImpl 节点管理 method 单测（migrate-console-cluster-nodes）。
 *
 * <p>使用 {@link MockRestServiceServer} 拦截真实 HTTP 调用，验证 7 个 method 各自的
 * URL / HTTP method / 请求体 / 响应解析路径。
 */
class ClusterOperationsNodeTest {

    private MockRestServiceServer mockServer;
    private ClusterOperationsImpl clusterOperations;

    @BeforeEach
    void setUp() {
        org.springframework.web.client.RestTemplate restTemplate =
                new org.springframework.web.client.RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.create(restTemplate);

        RegionClientFactory mockFactory = mock(RegionClientFactory.class);
        RegionInfoDto regionInfo = new RegionInfoDto("rid", "region-1", "Region 1", "[]",
                "https://mock-region", "wss://mock-region", "", "", "", "1", "private",
                null, null, null, "ent-1", null, null);
        RegionClient regionClient = new RegionClient(regionInfo, restClient, null);
        when(mockFactory.getClient(anyString(), anyString())).thenReturn(regionClient);

        tools.jackson.databind.ObjectMapper json =
                tools.jackson.databind.json.JsonMapper.builder().build();
        cn.kuship.console.infrastructure.region.RegionProperties props =
                new cn.kuship.console.infrastructure.region.RegionProperties(
                        5, false, 0, java.util.List.of("操作过于频繁，请稍后再试"));
        cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher enricher =
                new cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher();
        RegionApiResponseProcessor processor = new RegionApiResponseProcessor(json, props, enricher);

        cn.kuship.console.modules.account.repository.TenantsRepository mockTenantsRepo =
                org.mockito.Mockito.mock(cn.kuship.console.modules.account.repository.TenantsRepository.class);
        cn.kuship.console.modules.region.repository.RegionInfoEntityRepository mockRegionInfoRepo =
                org.mockito.Mockito.mock(cn.kuship.console.modules.region.repository.RegionInfoEntityRepository.class);
        clusterOperations = new ClusterOperationsImpl(mockFactory, processor,
                mockTenantsRepo, mockRegionInfoRepo, json);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    // T31: getClusterNodes

    @Test
    void getClusterNodes_sendsGetToNodesAndReturnsList() {
        mockServer.expect(requestTo("/v2/cluster/nodes"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"msg\":\"success\",\"data\":{\"list\":["
                                + "{\"name\":\"node-1\",\"architecture\":\"amd64\","
                                + "\"roles\":[\"master\"],\"unschedulable\":false,"
                                + "\"conditions\":[{\"type\":\"Ready\",\"status\":\"True\"}],"
                                + "\"resource\":{\"req_cpu\":2000,\"cap_cpu\":8000,"
                                + "\"req_memory\":4096000,\"cap_memory\":16384000}}]}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = clusterOperations.getClusterNodes("region-1", "ent-1");
        assertNotNull(result);
        assertTrue(result.containsKey("list"));
    }

    // T32: getNodeDetail

    @Test
    void getNodeDetail_sendsGetToNodeDetailUrl() {
        mockServer.expect(requestTo("/v2/cluster/nodes/node-1/detail"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{"
                                + "\"name\":\"node-1\",\"architecture\":\"amd64\","
                                + "\"external_ip\":\"192.168.1.1\",\"internal_ip\":\"10.0.0.1\","
                                + "\"container_run_time\":\"docker://20.10.0\","
                                + "\"roles\":[\"master\"],\"os_version\":\"Ubuntu 20.04\","
                                + "\"unschedulable\":false,\"create_time\":\"2024-01-01T00:00:00Z\","
                                + "\"kernel_version\":\"5.4.0\",\"operating_system\":\"linux\","
                                + "\"conditions\":[{\"type\":\"Ready\",\"status\":\"True\"}],"
                                + "\"resource\":{\"req_cpu\":2000,\"cap_cpu\":8000,"
                                + "\"req_memory\":4096000,\"cap_memory\":16384000,"
                                + "\"req_disk\":10737418240,\"cap_disk\":107374182400,"
                                + "\"cap_container_disk\":107374182400,\"req_container_disk\":10737418240}}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = clusterOperations.getNodeDetail("region-1", "ent-1", "node-1");
        assertNotNull(result);
        assertEquals("node-1", result.get("name"));
    }

    // T33: operateNodeAction

    @Test
    void operateNodeAction_sendsPostToActionUrl() {
        mockServer.expect(requestTo("/v2/cluster/nodes/node-1/action/unschedulable"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"msg\":\"success\",\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = clusterOperations.operateNodeAction(
                "region-1", "ent-1", "node-1", "unschedulable");
        assertNotNull(result);
    }

    @Test
    void operateNodeAction_evict_sendsPostToEvictUrl() {
        mockServer.expect(requestTo("/v2/cluster/nodes/node-1/action/evict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"drained\":true}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = clusterOperations.operateNodeAction(
                "region-1", "ent-1", "node-1", "evict");
        assertNotNull(result);
    }

    // T34: getNodeLabels / updateNodeLabels

    @Test
    void getNodeLabels_sendsGetToLabelsUrl() {
        mockServer.expect(requestTo("/v2/cluster/nodes/node-1/labels"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"labels\":{\"env\":\"prod\",\"role\":\"master\"}}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = clusterOperations.getNodeLabels("region-1", "ent-1", "node-1");
        assertNotNull(result);
        assertTrue(result.containsKey("labels"));
    }

    @Test
    void updateNodeLabels_sendsPutToLabelsUrl() {
        mockServer.expect(requestTo("/v2/cluster/nodes/node-1/labels"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"labels\":{\"env\":\"staging\"}}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> labels = Map.of("env", "staging");
        Map<String, Object> result = clusterOperations.updateNodeLabels(
                "region-1", "ent-1", "node-1", labels);
        assertNotNull(result);
    }

    // T35: getNodeTaints / updateNodeTaints

    @Test
    void getNodeTaints_sendsGetToTaintsUrl() {
        mockServer.expect(requestTo("/v2/cluster/nodes/node-1/taints"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"list\":["
                                + "{\"key\":\"node-role.kubernetes.io/master\","
                                + "\"effect\":\"NoSchedule\"}]}}",
                        MediaType.APPLICATION_JSON));

        List<Object> result = clusterOperations.getNodeTaints("region-1", "ent-1", "node-1");
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void updateNodeTaints_sendsPutToTaintsUrl() {
        mockServer.expect(requestTo("/v2/cluster/nodes/node-1/taints"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"list\":[]}}",
                        MediaType.APPLICATION_JSON));

        List<Object> taints = List.of();
        List<Object> result = clusterOperations.updateNodeTaints(
                "region-1", "ent-1", "node-1", taints);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
