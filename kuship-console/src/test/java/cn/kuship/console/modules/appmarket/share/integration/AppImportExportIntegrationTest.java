package cn.kuship.console.modules.appmarket.share.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.appmarket.helm.api.HelmChartImportOperations;
import cn.kuship.console.modules.appmarket.share.export.api.AppExportOperations;
import cn.kuship.console.modules.appmarket.share.export.repository.AppExportRecordRepository;
import cn.kuship.console.modules.appmarket.share.import_.api.AppImportOperations;
import cn.kuship.console.modules.appmarket.share.import_.repository.AppImportRecordRepository;
import cn.kuship.console.modules.appmarket.share.upload.api.AppUploadOperations;
import cn.kuship.console.modules.appmarket.share.upload.api.LoadTarImageOperations;
import cn.kuship.console.modules.region.yaml.api.YamlResourceOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppImportExportIntegrationTest {

    private static final int USER_ID = 909301;
    private static final String NICK = "kuship-impexp-admin";
    private static final String EMAIL = "impexp-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-impexp";
    private static final String TEAM = "kuship-impexp-team";
    private static final String TEAM_ID = "9093010101010101impexp7890123ab";
    private static final String NAMESPACE = "ns-impexp-team";
    private static final String REGION = "rainbond";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;
    @Autowired AppImportRecordRepository importRepo;
    @Autowired AppExportRecordRepository exportRepo;

    @MockitoBean AppExportOperations exportOps;
    @MockitoBean AppImportOperations importOps;
    @MockitoBean AppUploadOperations uploadOps;
    @MockitoBean LoadTarImageOperations loadTarOps;
    @MockitoBean HelmChartImportOperations chartImportOps;
    @MockitoBean YamlResourceOperations yamlOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'impexp-ent', 'ImpExp', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'ImpExpTeam') "
                + "ON DUPLICATE KEY UPDATE namespace=VALUES(namespace)",
                TEAM_ID, TEAM, USER_ID, NAMESPACE, ENT);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM app_import_record WHERE enterprise_id = ?", ENT);
        jdbc.update("DELETE FROM app_export_record WHERE enterprise_id = ?", ENT);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, EMAIL, null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void exportTrigger_writesRecordAndCallsRegion() throws Exception {
        when(exportOps.exportApp(anyString(), eq(ENT), any()))
                .thenReturn(Map.of("event_id", "exp-evt-1", "status", "init"));

        mvc.perform(post("/console/enterprise/" + ENT + "/app-models/export?region_name=" + REGION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app_key\":\"k1\",\"app_versions\":\"1.0\",\"format\":\"rainbond-app\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.event_id").value("exp-evt-1"));

        assertTrue(exportRepo.findByEventId("exp-evt-1").isPresent());
    }

    @Test
    void exportStatus_pollUpdatesLocalRecord() throws Exception {
        // 先 trigger
        when(exportOps.exportApp(anyString(), eq(ENT), any()))
                .thenReturn(Map.of("event_id", "exp-evt-2", "status", "init"));
        mvc.perform(post("/console/enterprise/" + ENT + "/app-models/export?region_name=" + REGION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app_key\":\"k2\",\"app_versions\":\"1.0\",\"format\":\"rainbond-app\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        when(exportOps.getExportStatus(any(), eq(ENT), eq("exp-evt-2")))
                .thenReturn(Map.of("status", "success", "file_path", "/tmp/k2-1.0.tar"));

        mvc.perform(get("/console/enterprise/" + ENT + "/app-models/export/exp-evt-2/status")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.status").value("success"));
    }

    @Test
    void initImport_writesRecordAndReturnsEventId() throws Exception {
        when(importOps.importApp2Enterprise(anyString(), eq(ENT), any()))
                .thenReturn(Map.of("event_id", "imp-evt-1", "status", "init"));

        mvc.perform(post("/console/enterprise/" + ENT + "/app-models/import?region_name=" + REGION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"rainbond-app\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.event_id").value("imp-evt-1"));

        assertTrue(importRepo.findByEventId("imp-evt-1").isPresent());
    }

    @Test
    void getImportStatus_404_whenLocalMissing() throws Exception {
        mvc.perform(get("/console/enterprise/" + ENT + "/app-models/import/missing-event")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelImport_deletesLocalEvenWhenRegion404() throws Exception {
        when(importOps.importApp2Enterprise(anyString(), eq(ENT), any()))
                .thenReturn(Map.of("event_id", "imp-cancel-1", "status", "init"));
        mvc.perform(post("/console/enterprise/" + ENT + "/app-models/import?region_name=" + REGION)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        doThrow(new RegionApiException("app-import",
                "/v2/app/import/ids/imp-cancel-1", "DELETE",
                404, 404, "not found", "导入事件不存在", Map.of(), null))
                .when(importOps).deleteEnterpriseImport(anyString(), eq(ENT), eq("imp-cancel-1"));

        mvc.perform(delete("/console/enterprise/" + ENT + "/app-models/import/imp-cancel-1?region_name=" + REGION)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        assertTrue(importRepo.findByEventId("imp-cancel-1").isEmpty());
    }

    @Test
    void uploadEvent_create_callsRegion() throws Exception {
        when(uploadOps.createUploadDir(anyString(), eq(TEAM), eq("up-evt-1")))
                .thenReturn(Map.of("path", "/tmp/up-evt-1"));

        mvc.perform(post("/console/teams/" + TEAM + "/app-upload/events/up-evt-1?region_name=" + REGION)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.path").value("/tmp/up-evt-1"));
    }

    @Test
    void uploadEvent_updateComponent_callsRegionWithComponentPath() throws Exception {
        when(uploadOps.updateUploadDir(anyString(), eq(TEAM), eq("up-evt-2"), eq("c1")))
                .thenReturn(Map.of("status", "ok"));

        mvc.perform(put("/console/teams/" + TEAM + "/app-upload/events/up-evt-2/component_id/c1?region_name=" + REGION)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());
    }

    @Test
    void loadTarImage_callsRegion() throws Exception {
        when(loadTarOps.loadTarImage(anyString(), eq(TEAM), any()))
                .thenReturn(Map.of("load_status", "ok"));

        mvc.perform(post("/console/teams/" + TEAM + "/app/load_tar_image?region_name=" + REGION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file\":\"a.tar\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.load_status").value("ok"));
    }

    @Test
    void yamlResourceName_callsRegionWithEnterpriseEid() throws Exception {
        when(yamlOps.yamlResourceName(eq(ENT), anyString(), any()))
                .thenReturn(Map.of("resources", List.of(Map.of("name", "deploy-1"))));

        mvc.perform(post("/console/teams/" + TEAM + "/resource-name?region_name=" + REGION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yaml_content\":\"...\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());
    }

    @Test
    void yamlResourceImport_postUrlIsEnterpriseScope() throws Exception {
        when(yamlOps.yamlResourceImport(eq(ENT), eq(REGION), any()))
                .thenReturn(Map.of("imported", 5));

        mvc.perform(post("/console/enterprise/" + ENT + "/regions/" + REGION + "/yaml-resource-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yaml_content\":\"...\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.imported").value(5));
    }

    @Test
    void importChartResource_callsRegion() throws Exception {
        when(chartImportOps.importUploadChartResource(anyString(), any()))
                .thenReturn(Map.of("ok", true));

        mvc.perform(post("/console/teams/" + TEAM + "/import_upload_chart_resource?region_name=" + REGION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());
    }
}
