package cn.kuship.console.modules.misc.kubeblocks.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.misc.kubeblocks.api.KubeBlocksOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KubeBlocks 数据库托管端到端集成测试。
 *
 * <p>策略：mock {@link KubeBlocksOperations} 跳过 region 调用，但保留真实 DB +
 * 真实 KubeBlocksController + 真实 resolveService 校验链。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KubeBlocksIntegrationTest {

    private static final int USER_ID = 909902;
    private static final String NICK = "kuship-kb-admin";
    private static final String EMAIL = "kb-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-kb";
    private static final String TEAM = "kuship-kb-team";
    private static final String TEAM_ID = "9099020202020202kb78901234567xx";
    private static final String NAMESPACE = "ns-kb-team";
    private static final String REGION = "rainbond";
    private static final String ALIAS = "mysql1";
    private static final String SERVICE_ID = "9099020202kb0001id00000000000abc";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean KubeBlocksOperations kubeblocksOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'kb-ent', 'KbTest', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'KbTeam') "
                + "ON DUPLICATE KEY UPDATE namespace=VALUES(namespace)",
                TEAM_ID, TEAM, USER_ID, NAMESPACE, ENT);

        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, container_gpu, "
                + "min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, creater, protocol, "
                + "total_memory, is_service, namespace, volume_type, port_type, service_origin, service_source, create_status, "
                + "tenant_service_group_id, open_webhooks, server_type, is_upgrate, build_upgrade, service_name, k8s_component_name, update_time, secret) "
                + "VALUES (?, ?, 'app', ?, 'MySQL', ?, 'app', 0, 0, '8.0', 1, 'kubeblocks/mysql:8.0', 1, 100, 0, 256, "
                + "'state', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'kubeblocks', 'kubeblocks', 'complete', "
                + "0, 1, 'tcp', 0, 0, ?, ?, NOW(), 'sec') "
                + "ON DUPLICATE KEY UPDATE service_source=VALUES(service_source)",
                SERVICE_ID, TEAM_ID, ALIAS, REGION, USER_ID, TEAM,
                "kuship-" + ALIAS, "kuship-" + ALIAS);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM tenant_service WHERE service_id = ?", SERVICE_ID);
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
    void supportedDatabases_happy() throws Exception {
        when(kubeblocksOps.listSupportedDatabases(eq(REGION))).thenReturn(Map.of(
                "list", List.of(Map.of("name", "mysql", "versions", List.of("8.0")))));

        mvc.perform(get("/console/teams/" + TEAM + "/regions/" + REGION + "/kubeblocks/supported_databases")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg_show").value("OK"))
                .andExpect(jsonPath("$.data.bean.list[0].name").value("mysql"));

        verify(kubeblocksOps).listSupportedDatabases(REGION);
    }

    @Test
    void getClusterDetail_happy_passesServiceIdAndStatus() throws Exception {
        when(kubeblocksOps.getClusterDetail(eq(REGION), eq(SERVICE_ID))).thenReturn(Map.of(
                "kubeblocks_status", "Running",
                "backup_config", Map.of("schedule", "0 2 * * *", "retention", 7)));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/kubeblocks/detail")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.kubeblocks_status").value("Running"))
                .andExpect(jsonPath("$.data.bean.backup_config.schedule").value("0 2 * * *"));
    }

    @Test
    void expansionCluster_putBodyPassThrough() throws Exception {
        when(kubeblocksOps.expansionCluster(eq(REGION), eq(SERVICE_ID), any())).thenReturn(Map.of());

        mvc.perform(put("/console/teams/" + TEAM + "/apps/" + ALIAS + "/kubeblocks/detail")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replicas\":3,\"cpu\":\"1000m\",\"memory\":\"2Gi\"}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(kubeblocksOps).expansionCluster(eq(REGION), eq(SERVICE_ID), bodyCap.capture());
        Map<String, Object> body = bodyCap.getValue();
        assertEquals(3, ((Number) body.get("replicas")).intValue());
        assertEquals("1000m", body.get("cpu"));
    }

    @Test
    void listClusterBackups_paginationParsed() throws Exception {
        when(kubeblocksOps.listClusterBackups(eq(REGION), eq(SERVICE_ID), eq(2), eq(20))).thenReturn(
                Map.of("list", List.of(), "total", 0));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/kubeblocks/backups?page=2&page_size=20")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        verify(kubeblocksOps).listClusterBackups(REGION, SERVICE_ID, 2, 20);
    }

    @Test
    void createManualBackup_postNoBody() throws Exception {
        when(kubeblocksOps.createManualBackup(eq(REGION), eq(SERVICE_ID))).thenReturn(Map.of("backup_id", "b1"));

        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + ALIAS + "/kubeblocks/backups")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.backup_id").value("b1"));

        verify(kubeblocksOps).createManualBackup(REGION, SERVICE_ID);
    }

    @Test
    void deleteClusterBackups_bodyShapeListSize2() throws Exception {
        when(kubeblocksOps.deleteClusterBackups(eq(REGION), eq(SERVICE_ID), any())).thenReturn(Map.of());

        mvc.perform(delete("/console/teams/" + TEAM + "/apps/" + ALIAS + "/kubeblocks/backups")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backups\":[\"b1\",\"b2\"]}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> backupsCap = ArgumentCaptor.forClass(List.class);
        verify(kubeblocksOps).deleteClusterBackups(eq(REGION), eq(SERVICE_ID), backupsCap.capture());
        List<String> backups = backupsCap.getValue();
        assertEquals(2, backups.size());
        assertEquals("b1", backups.get(0));
    }

    @Test
    void updateBackupConfig_putBodyPassThrough() throws Exception {
        when(kubeblocksOps.updateBackupConfig(eq(REGION), eq(SERVICE_ID), any())).thenReturn(Map.of());

        mvc.perform(put("/console/teams/" + TEAM + "/apps/" + ALIAS + "/kubeblocks/backup-config")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schedule\":\"0 2 * * *\",\"retention\":7}"))
                .andExpect(status().isOk());

        verify(kubeblocksOps).updateBackupConfig(eq(REGION), eq(SERVICE_ID), any());
        verify(kubeblocksOps, never()).updateClusterParameters(anyString(), anyString(), any());
    }

    @Test
    void listClusterParameters_keywordPassThrough() throws Exception {
        when(kubeblocksOps.listClusterParameters(eq(REGION), eq(SERVICE_ID), eq(1), eq(6), eq("innodb")))
                .thenReturn(Map.of("list", List.of()));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/kubeblocks/parameters?page=1&page_size=6&keyword=innodb")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        verify(kubeblocksOps).listClusterParameters(REGION, SERVICE_ID, 1, 6, "innodb");
    }

    @Test
    void updateClusterParameters_postBodyPassThrough() throws Exception {
        when(kubeblocksOps.updateClusterParameters(eq(REGION), eq(SERVICE_ID), any())).thenReturn(Map.of());

        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + ALIAS + "/kubeblocks/parameters")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parameters\":[{\"name\":\"max_connections\",\"value\":\"100\"}]}"))
                .andExpect(status().isOk());

        verify(kubeblocksOps).updateClusterParameters(eq(REGION), eq(SERVICE_ID), any());
    }

    @Test
    void getClusterDetail_region5xx_passesThrough() throws Exception {
        when(kubeblocksOps.getClusterDetail(eq(REGION), eq(SERVICE_ID))).thenThrow(
                new RegionApiException("kubeblocks",
                        "/v2/cluster/kubeblocks/clusters/" + SERVICE_ID, "GET",
                        503, 503, "region down", "集群不可用", Map.of(), null));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/kubeblocks/detail")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.msg_show").value("集群不可用"));
    }

    @Test
    void teamNotFound_returns404_noOpsCall() throws Exception {
        mvc.perform(get("/console/teams/no-such-team/apps/" + ALIAS + "/kubeblocks/detail")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg_show").value("团队不存在"));

        verify(kubeblocksOps, never()).getClusterDetail(anyString(), anyString());
    }

    @Test
    void serviceNotFound_returns404_noOpsCall() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/apps/no-such-svc/kubeblocks/detail")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg_show").value("组件不存在"));

        verify(kubeblocksOps, never()).getClusterDetail(anyString(), anyString());
    }
}
