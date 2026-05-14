package cn.kuship.console.modules.appmarket.share;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.appmarket.helm.api.HelmChartImportOperationsImpl;
import cn.kuship.console.modules.appmarket.share.export.api.AppExportOperationsImpl;
import cn.kuship.console.modules.appmarket.share.import_.api.AppImportOperationsImpl;
import cn.kuship.console.modules.appmarket.share.upload.api.AppUploadOperationsImpl;
import cn.kuship.console.modules.appmarket.share.upload.api.LoadTarImageOperationsImpl;
import cn.kuship.console.modules.region.yaml.api.YamlResourceOperationsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 综合单测覆盖 6 个 Impl 主要 method（22 method 中各取核心代表）。 */
class AppImportExportOperationsTest {

    private MockRestServiceServer mockServer;
    private RegionClientFactory factory;
    private RegionApiResponseProcessor processor;

    @BeforeEach
    void setUp() {
        org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
        mockServer = MockRestServiceServer.bindTo(rt).build();
        RestClient rc = RestClient.create(rt);
        factory = mock(RegionClientFactory.class);
        RegionInfoDto info = new RegionInfoDto("rid", "region-1", "Region 1", "[]",
                "https://mock", "wss://mock", "", "", "", "1", "private",
                null, null, null, "ent-1", null, null);
        when(factory.getClient(anyString(), anyString())).thenReturn(new RegionClient(info, rc, null));
        tools.jackson.databind.ObjectMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
        processor = new RegionApiResponseProcessor(json,
                new RegionProperties(5, false, 0, java.util.List.of("操作过于频繁，请稍后再试")),
                new RegionErrorMsgEnricher());
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void exportApp_postUrlIsAppExport() {
        mockServer.expect(requestTo("/v2/app/export"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"event_id\":\"e1\"}}}",
                        MediaType.APPLICATION_JSON));
        Map<String, Object> resp = new AppExportOperationsImpl(factory, processor)
                .exportApp("region-1", "ent-1", Map.of("app_key", "k1"));
        assertEquals("e1", resp.get("event_id"));
    }

    @Test
    void getExportStatus_getUrlIncludesEventId() {
        mockServer.expect(requestTo("/v2/app/export/e1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"status\":\"success\"}}}",
                        MediaType.APPLICATION_JSON));
        Map<String, Object> resp = new AppExportOperationsImpl(factory, processor)
                .getExportStatus("region-1", "ent-1", "e1");
        assertEquals("success", resp.get("status"));
    }

    @Test
    void importApp2Enterprise_postUrlIsAppImport() {
        mockServer.expect(requestTo("/v2/app/import"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"event_id\":\"e2\"}}}",
                        MediaType.APPLICATION_JSON));
        Map<String, Object> resp = new AppImportOperationsImpl(factory, processor)
                .importApp2Enterprise("region-1", "ent-1", Map.of());
        assertEquals("e2", resp.get("event_id"));
    }

    @Test
    void getEnterpriseImportStatus_getUrlIncludesIdsSegment() {
        mockServer.expect(requestTo("/v2/app/import/ids/e2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"status\":\"importing\"}}}",
                        MediaType.APPLICATION_JSON));
        Map<String, Object> resp = new AppImportOperationsImpl(factory, processor)
                .getEnterpriseImportStatus("region-1", "ent-1", "e2");
        assertEquals("importing", resp.get("status"));
    }

    @Test
    void deleteEnterpriseImport_deleteUrlIncludesIdsSegment() {
        mockServer.expect(requestTo("/v2/app/import/ids/e2"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));
        new AppImportOperationsImpl(factory, processor).deleteEnterpriseImport("region-1", "ent-1", "e2");
    }

    @Test
    void createUploadDir_postUrlIsAppUploadEvents() {
        mockServer.expect(requestTo("/v2/app/upload/events/e3"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));
        new AppUploadOperationsImpl(factory, processor).createUploadDir("region-1", "team-1", "e3");
    }

    @Test
    void updateUploadDir_putUrlIncludesComponentSegment() {
        mockServer.expect(requestTo("/v2/app/upload/events/e3/component_id/c1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));
        new AppUploadOperationsImpl(factory, processor).updateUploadDir("region-1", "team-1", "e3", "c1");
    }

    @Test
    void loadTarImage_postUrlIsAppLoadTar() {
        mockServer.expect(requestTo("/v2/app/load_tar_image"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"load_status\":\"ok\"}}}",
                        MediaType.APPLICATION_JSON));
        Map<String, Object> resp = new LoadTarImageOperationsImpl(factory, processor)
                .loadTarImage("region-1", "team-1", Map.of("file", "a.tar"));
        assertEquals("ok", resp.get("load_status"));
    }

    @Test
    void importUploadChartResource_postUrlIsHelmImport() {
        mockServer.expect(requestTo("/v2/helm/import_upload_chart_resource"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));
        new HelmChartImportOperationsImpl(factory, processor).importUploadChartResource("region-1", Map.of());
    }

    @Test
    void yamlResourceName_getWithBodyAndQueryParam() {
        mockServer.expect(requestTo("/v2/cluster/yaml_resource_name?eid=ent-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"resources\":[{\"name\":\"deploy-1\"}]}}}",
                        MediaType.APPLICATION_JSON));
        Map<String, Object> resp = new YamlResourceOperationsImpl(factory, processor)
                .yamlResourceName("ent-1", "region-1", Map.of("yaml_content", "..."));
        Object res = resp.get("resources");
        assertEquals(true, res instanceof java.util.List);
    }

    @Test
    void yamlResourceImport_postUrlIncludesEid() {
        mockServer.expect(requestTo("/v2/cluster/yaml_resource_import?eid=ent-1"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"imported\":3}}}",
                        MediaType.APPLICATION_JSON));
        Map<String, Object> resp = new YamlResourceOperationsImpl(factory, processor)
                .yamlResourceImport("ent-1", "region-1", Map.of("yaml_content", "..."));
        assertEquals(3, ((Number) resp.get("imported")).intValue());
    }
}
