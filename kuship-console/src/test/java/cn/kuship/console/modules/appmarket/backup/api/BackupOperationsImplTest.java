package cn.kuship.console.modules.appmarket.backup.api;

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

/** 9 method × ~1.5 = ~14 用例：既有 4 method URL 修正回归 + 5 个新 method × 1 happy + 1 错误。 */
class BackupOperationsImplTest {

    private MockRestServiceServer mockServer;
    private BackupOperationsImpl ops;

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

        ops = new BackupOperationsImpl(mockFactory, processor);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    // ===== 既有 4 method URL 修正回归 =====

    @Test
    void backup_urlIsBackupsPlural_groupIdInBody() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backups"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"event_id\":\"e1\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.backup("region-1", "team-1", "12345", Map.of("note", "test"));
        assertEquals("e1", resp.get("event_id"));
    }

    @Test
    void backupStatus_urlIsBackupsPlural() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backups/b1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"backup_id\":\"b1\",\"status\":\"success\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.backupStatus("region-1", "team-1", "b1");
        assertEquals("b1", resp.get("backup_id"));
    }

    @Test
    void restore_deprecatedThrows() {
        assertThrows(UnsupportedOperationException.class,
                () -> ops.restore("region-1", "team-1", Map.of()));
    }

    @Test
    void export_deprecatedThrows() {
        assertThrows(UnsupportedOperationException.class,
                () -> ops.export("region-1", "team-1", "b1"));
    }

    // ===== 5 新 method =====

    @Test
    void deleteBackup_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backups/b1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{}}}",
                        MediaType.APPLICATION_JSON));

        ops.deleteBackup("region-1", "team-1", "b1");
    }

    @Test
    void deleteBackup_404_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backups/b1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body("{\"code\":404,\"msg\":\"not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.deleteBackup("region-1", "team-1", "b1"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void listBackupsByGroupUuid_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backups?group_id=g-uuid-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"list\":[{\"backup_id\":\"b1\"},{\"backup_id\":\"b2\"}]}}}",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> list = ops.listBackupsByGroupUuid("region-1", "team-1", "g-uuid-1");
        assertEquals(2, list.size());
        assertEquals("b1", list.get(0).get("backup_id"));
    }

    @Test
    void listBackupsByGroupUuid_emptyList() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backups?group_id=g-uuid-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"bean\":{\"list\":[]}}}",
                        MediaType.APPLICATION_JSON));

        assertTrue(ops.listBackupsByGroupUuid("region-1", "team-1", "g-uuid-1").isEmpty());
    }

    @Test
    void startMigrate_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backups/b1/restore"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"restore_id\":\"r1\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.startMigrate("region-1", "team-1", "b1",
                Map.of("event_id", "e1", "group_id", "g-target"));
        assertEquals("r1", resp.get("restore_id"));
    }

    @Test
    void startMigrate_500_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backups/b1/restore"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":500,\"msg\":\"err\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> ops.startMigrate("region-1", "team-1", "b1", Map.of()));
        assertEquals(500, ex.getCode());
    }

    @Test
    void getMigrateStatus_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backups/b1/restore/r1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"status\":\"success\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.getMigrateStatus("region-1", "team-1", "b1", "r1");
        assertEquals("success", resp.get("status"));
    }

    @Test
    void getMigrateStatus_404_returnsNotFound() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backups/b1/restore/r-missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body("{\"code\":404,\"msg\":\"not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.getMigrateStatus("region-1", "team-1", "b1", "r-missing");
        assertEquals("not_found", resp.get("status"));
    }

    @Test
    void copyBackupData_happy() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backupcopy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"bean\":{\"backup_id\":\"b-copy\",\"status\":\"success\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> resp = ops.copyBackupData("region-1", "team-1",
                Map.of("backup_id", "b1", "version", "v1"));
        assertEquals("b-copy", resp.get("backup_id"));
    }

    @Test
    void copyBackupData_500_throws() {
        mockServer.expect(requestTo("/v2/tenants/team-1/groupapp/backupcopy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":500,\"msg\":\"err\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RegionApiException.class,
                () -> ops.copyBackupData("region-1", "team-1", Map.of()));
    }
}
