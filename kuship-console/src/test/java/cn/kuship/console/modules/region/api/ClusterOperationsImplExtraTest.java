package cn.kuship.console.modules.region.api;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ClusterOperationsImpl 5 method 单测：MockRestServiceServer 拦截 region API 调用，
 * 断言 URL/HTTP 方法/查询串编码 / 节点名 URL 编码 / 404 降级 / 错误透传。
 */
class ClusterOperationsImplExtraTest {

    private MockRestServiceServer mockServer;
    private ClusterOperationsImpl clusterOps;
    private TenantsRepository tenantsRepo;
    private RegionInfoEntityRepository regionInfoRepo;

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
        regionInfoRepo = mock(RegionInfoEntityRepository.class);
        clusterOps = new ClusterOperationsImpl(mockFactory, processor, tenantsRepo, regionInfoRepo, json);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void getResources_usesNamespaceFromTenants() {
        Tenants t = new Tenants();
        t.setTenantName("default");
        t.setNamespace("my-ns");
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));

        mockServer.expect(requestTo("/v2/tenants/my-ns/resources?enterprise_id=ent-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"cpu\":1000,\"memory\":2048}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = clusterOps.getResources("region-1", "default", "ent-1");
        assertEquals(1000, ((Number) resp.get("cpu")).intValue());
        assertEquals(2048, ((Number) resp.get("memory")).intValue());
    }

    @Test
    void getResources_fallbackToTenantNameWhenNamespaceBlank() {
        Tenants t = new Tenants();
        t.setTenantName("default");
        t.setNamespace(null);
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));

        mockServer.expect(requestTo("/v2/tenants/default/resources?enterprise_id=ent-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"cpu\":500}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = clusterOps.getResources("region-1", "default", "ent-1");
        assertEquals(500, ((Number) resp.get("cpu")).intValue());
    }

    @Test
    void getResources_teamNotFound_throws404() {
        when(tenantsRepo.findByTenantName("no-such")).thenReturn(Optional.empty());

        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> clusterOps.getResources("region-1", "no-such", "ent-1"));
        assertEquals(404, ex.getCode());
        assertEquals("团队不存在", ex.getMsgShow());
    }

    @Test
    void getClusterInfo_happyPath() {
        mockServer.expect(requestTo("/v2/cluster/info"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"version\":\"v1.28\",\"capacity\":{\"cpu\":\"16\"}}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = clusterOps.getClusterInfo("region-1");
        assertEquals("v1.28", resp.get("version"));
    }

    @Test
    void getClusterInfo_404_fallbacksToLocalRegionInfo() {
        mockServer.expect(requestTo("/v2/cluster/info"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body("{\"code\":404,\"msg\":\"not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionInfo ri = new RegionInfo();
        ri.setRegionName("region-1");
        ri.setRegionAlias("Region 1");
        ri.setUrl("https://mock-region");
        ri.setTcpdomain("172.20.0.1");
        ri.setHttpdomain("apps.local");
        ri.setStatus("1");
        when(regionInfoRepo.findByRegionName("region-1")).thenReturn(Optional.of(ri));

        Map<String, Object> resp = clusterOps.getClusterInfo("region-1");
        assertEquals("region-1", resp.get("region_name"));
        assertEquals("Region 1", resp.get("region_alias"));
        assertEquals("172.20.0.1", resp.get("tcpdomain"));
        assertEquals("apps.local", resp.get("httpdomain"));
        assertEquals("1", resp.get("status"));
    }

    @Test
    void getClusterEvents_serializesBodyToSortedQueryString() {
        // body 输入顺序 type → since；TreeMap 字典序输出 since → type
        mockServer.expect(requestTo("/v2/cluster/events?since=1h&type=warning"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"list\":[{\"reason\":\"BackOff\"}]}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "warning");
        body.put("since", "1h");
        Map<String, Object> resp = clusterOps.getClusterEvents("region-1", body);
        assertNotNull(resp);
        assertNotNull(resp.get("list"));
    }

    @Test
    void getClusterEvents_emptyBody_noQueryString() {
        mockServer.expect(requestTo("/v2/cluster/events"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"list\":[]}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = clusterOps.getClusterEvents("region-1", Map.of());
        assertNotNull(resp);
    }

    @Test
    void getNodes_returnsList() {
        mockServer.expect(requestTo("/v2/cluster/nodes"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"list\":[{\"name\":\"node-1\"},{\"name\":\"node-2\"}]}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = clusterOps.getClusterNodes("region-1", "");
        assertNotNull(resp.get("list"));
    }

    @Test
    void getNodeDetail_urlEncodesNodeNameWithDot() {
        // 节点名 worker-01.example.com 含点号；URL encode 后 . 不会被编码（标记为 RFC 3986 unreserved char）
        mockServer.expect(requestTo("/v2/cluster/nodes/worker-01.example.com/detail"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"name\":\"worker-01.example.com\",\"role\":\"worker\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = clusterOps.getNodeDetail("region-1", "", "worker-01.example.com");
        assertEquals("worker-01.example.com", resp.get("name"));
        assertEquals("worker", resp.get("role"));
    }

    @Test
    void getNodes_503_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/cluster/nodes"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"code\":503,\"msg\":\"region down\",\"msg_show\":\"集群不可用\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> clusterOps.getClusterNodes("region-1", ""));
        assertEquals(503, ex.getCode());
        assertEquals("集群不可用", ex.getMsgShow());
    }
}
