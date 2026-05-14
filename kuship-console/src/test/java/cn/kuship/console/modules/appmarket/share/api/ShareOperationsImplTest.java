package cn.kuship.console.modules.appmarket.share.api;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
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
 * {@link ShareOperationsImpl} 7 method 单测：用 MockRestServiceServer 拦截，断言 URL / namespace 替换。
 */
class ShareOperationsImplTest {

    private MockRestServiceServer mockServer;
    private ShareOperationsImpl ops;
    private TenantsRepository tenantsRepo;

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

        tenantsRepo = mock(TenantsRepository.class);
        ops = new ShareOperationsImpl(mockFactory, processor, tenantsRepo);

        Tenants t = new Tenants();
        t.setTenantName("default");
        t.setNamespace("my-ns");
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void shareCloudService_happy() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/cloud-share"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"share_id\":\"cs-1\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.shareCloudService("region-1", "default", Map.of("template", "tpl"));
        assertEquals("cs-1", resp.get("share_id"));
    }

    @Test
    void shareService_happy() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/services/svc1/share"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"share_id\":\"rs-1\",\"event_id\":\"e-1\",\"image_name\":\"img\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.shareService("region-1", "default", "svc1",
                Map.of("service_key", "k", "event_id", "e-1"));
        assertEquals("rs-1", resp.get("share_id"));
        assertEquals("e-1", resp.get("event_id"));
    }

    @Test
    void getShareServiceResult_happy() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/services/svc1/share/rs-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"status\":\"success\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.getShareServiceResult("region-1", "default", "svc1", "rs-1");
        assertEquals("success", resp.get("status"));
    }

    @Test
    void sharePlugin_happy() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/plugins/p-1/share"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"share_id\":\"ps-1\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.sharePlugin("region-1", "default", "p-1",
                Map.of("plugin_version", "1.0"));
        assertEquals("ps-1", resp.get("share_id"));
    }

    @Test
    void getSharePluginResult_happy() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/plugins/p-1/share/ps-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"status\":\"running\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.getSharePluginResult("region-1", "default", "p-1", "ps-1");
        assertEquals("running", resp.get("status"));
    }

    @Test
    void getServicePublishStatus_urlNoNamespace() {
        // 唯一例外：URL 不含 {namespace} 段
        mockServer.expect(requestTo("/v2/builder/publish/service/svckey/version/1.0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"status\":\"published\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.getServicePublishStatus("region-1", "default", "svckey", "1.0");
        assertEquals("published", resp.get("status"));
    }

    @Test
    void listAppReleases_extractsListField() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/apps/region-app-id/releases"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"list\":[\"v1.0\",\"v2.0\"]}}",
                        MediaType.APPLICATION_JSON));

        List<Object> list = ops.listAppReleases("region-1", "default", "region-app-id");
        assertEquals(2, list.size());
        assertEquals("v1.0", list.get(0));
    }

    @Test
    void namespaceFallbackToTenantName() {
        Tenants t = new Tenants();
        t.setTenantName("default2");
        t.setNamespace(null);
        when(tenantsRepo.findByTenantName("default2")).thenReturn(Optional.of(t));

        mockServer.expect(requestTo("/v2/tenants/default2/services/svc1/share"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.shareService("region-1", "default2", "svc1", Map.of());
    }

    @Test
    void shareService_5xx_throwsRegionApiException() {
        mockServer.expect(requestTo("/v2/tenants/my-ns/services/svc1/share"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"code\":503,\"msg\":\"region down\",\"msg_show\":\"集群不可用\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.shareService("region-1", "default", "svc1", Map.of()));
        assertEquals(503, ex.getCode());
        assertNotNull(ex.getMsgShow());
    }
}
