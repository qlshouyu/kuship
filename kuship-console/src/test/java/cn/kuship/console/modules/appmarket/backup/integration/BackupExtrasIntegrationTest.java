package cn.kuship.console.modules.appmarket.backup.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.appmarket.backup.api.BackupOperations;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GroupCopyMigrateController 改造后 3 endpoint 集成测试。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackupExtrasIntegrationTest {

    private static final int USER_ID = 909906;
    private static final String NICK = "kuship-backup-admin";
    private static final String EMAIL = "backup-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-backup";
    private static final String TEAM = "kuship-backup-team";
    private static final String TEAM_ID = "9099060606060606backup7890123ab";
    private static final String TARGET_TEAM = "kuship-backup-target";
    private static final String TARGET_TEAM_ID = "9099060606target0606backup7890ab";
    private static final String NAMESPACE = "ns-backup-team";
    private static final String REGION = "rainbond";
    private static final Integer GROUP_ID = 9099906;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean BackupOperations backupOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'backup-ent', 'BackupTest', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'BackupTeam') "
                + "ON DUPLICATE KEY UPDATE namespace=VALUES(namespace)",
                TEAM_ID, TEAM, USER_ID, NAMESPACE, ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'BackupTarget') "
                + "ON DUPLICATE KEY UPDATE namespace=VALUES(namespace)",
                TARGET_TEAM_ID, TARGET_TEAM, USER_ID, "ns-backup-target", ENT);
        jdbc.update("INSERT INTO service_group (id, tenant_id, group_name, region_name, is_default, order_index, "
                + "create_time, update_time, app_type, k8s_app) "
                + "VALUES (?, ?, 'group-backup', ?, 1, 0, NOW(), NOW(), 'rainbond', 'k8s-bk') "
                + "ON DUPLICATE KEY UPDATE region_name=VALUES(region_name)",
                GROUP_ID, TEAM_ID, REGION);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM service_group WHERE id = ?", GROUP_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id IN (?, ?)", TEAM_ID, TARGET_TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, EMAIL, null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void copy_callsRegionCopyBackupData() throws Exception {
        when(backupOps.copyBackupData(eq(REGION), eq(TEAM), any()))
                .thenReturn(Map.of("backup_id", "b-copy", "status", "success"));

        mvc.perform(post("/console/teams/" + TEAM + "/groupapp/" + GROUP_ID + "/copy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_region\":\"region-2\",\"backup_id\":\"b1\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.backup_id").value("b-copy"));

        verify(backupOps).copyBackupData(eq(REGION), eq(TEAM), any());
    }

    @Test
    void migrate_validatesAndCallsStartMigrate() throws Exception {
        when(backupOps.startMigrate(eq(REGION), eq(TEAM), eq("b1"), any()))
                .thenReturn(Map.of("restore_id", "r1"));

        mvc.perform(post("/console/teams/" + TEAM + "/groupapp/" + GROUP_ID + "/migrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backup_id\":\"b1\",\"team\":\"" + TARGET_TEAM
                                + "\",\"region\":\"" + REGION + "\",\"migrate_type\":\"migrate\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.restore_id").value("r1"))
                .andExpect(jsonPath("$.data.bean.target_team_id").value(TARGET_TEAM_ID));
    }

    @Test
    void migrate_missingBackupId_returns400() throws Exception {
        mvc.perform(post("/console/teams/" + TEAM + "/groupapp/" + GROUP_ID + "/migrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team\":\"" + TARGET_TEAM + "\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void migrate_missingTeam_returns400() throws Exception {
        mvc.perform(post("/console/teams/" + TEAM + "/groupapp/" + GROUP_ID + "/migrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backup_id\":\"b1\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void migrate_targetTeamNotFound_returns404() throws Exception {
        mvc.perform(post("/console/teams/" + TEAM + "/groupapp/" + GROUP_ID + "/migrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backup_id\":\"b1\",\"team\":\"missing-team\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void migrateRecord_callsGetMigrateStatus() throws Exception {
        when(backupOps.getMigrateStatus(eq(REGION), eq(TEAM), eq("b1"), eq("r1")))
                .thenReturn(Map.of("status", "success"));

        mvc.perform(get("/console/teams/" + TEAM + "/groupapp/" + GROUP_ID
                                + "/migrate/record?backup_id=b1&restore_id=r1")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.status").value("success"));
    }

    @Test
    void migrateRecord_missingRestoreId_returns400() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/groupapp/" + GROUP_ID
                                + "/migrate/record?backup_id=b1")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void migrateRecord_404FromRegion_returnsNotFoundBean() throws Exception {
        when(backupOps.getMigrateStatus(eq(REGION), eq(TEAM), eq("b1"), eq("r-missing")))
                .thenReturn(Map.of("status", "not_found"));

        mvc.perform(get("/console/teams/" + TEAM + "/groupapp/" + GROUP_ID
                                + "/migrate/record?backup_id=b1&restore_id=r-missing")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.status").value("not_found"));
    }
}
