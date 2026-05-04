package cn.kuship.console.modules.appruntime.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.api.ServiceLifecycleOperations;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证 lifecycle 端点：start/stop/restart 调用 mock + update_version+1。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppLifecycleIntegrationTest {

    private static final int USER_ID = 909082;
    private static final String NICK = "kuship-lifecycle-admin";
    private static final String ENT = "kuship-test-ent-lifecycle";
    private static final String TEAM = "kuship-test-team-lifecycle";
    private static final String TEAM_ID = "9090828282828282lc1234567890123";
    private static final String SERVICE_ID = "lctest909082svc1234567890abcdef0";
    private static final String SERVICE_ALIAS = "kuship-lc-svc";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean ServiceLifecycleOperations lifecycle;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'lc-ent', 'LCTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "lc-admin@kuship.local", NICK,
                encoder.encode("lc-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'LCTeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM, USER_ID, TEAM, ENT);
        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, "
                + "container_gpu, min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, "
                + "creater, protocol, total_memory, is_service, namespace, volume_type, port_type, service_origin, "
                + "tenant_service_group_id, open_webhooks, server_type, is_upgrate, build_upgrade, service_name, "
                + "k8s_component_name, update_time) "
                + "VALUES (?, ?, 'app', ?, 'LCSvc', 'r1', 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, 256, "
                + "'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'assistant', 0, 0, 'tcp', 0, 0, "
                + "'kuship-lc-svc', 'kuship-lc-svc', NOW()) "
                + "ON DUPLICATE KEY UPDATE service_alias=VALUES(service_alias)",
                SERVICE_ID, TEAM_ID, SERVICE_ALIAS, USER_ID, TEAM);
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
                new JwtClaims((long) USER_ID, NICK, "lc-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void start_stop_restart_invokeRegionAndBumpUpdateVersion() throws Exception {
        when(lifecycle.startService(eq("r1"), eq(TEAM), eq(SERVICE_ALIAS), ArgumentMatchers.any()))
                .thenReturn(Map.of("event_id", "ev-start-1"));
        when(lifecycle.stopService(eq("r1"), eq(TEAM), eq(SERVICE_ALIAS), ArgumentMatchers.any()))
                .thenReturn(Map.of("event_id", "ev-stop-1"));
        when(lifecycle.restartService(eq("r1"), eq(TEAM), eq(SERVICE_ALIAS), ArgumentMatchers.any()))
                .thenReturn(Map.of("event_id", "ev-restart-1"));

        // POST /start
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/start")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.event_id").value("ev-start-1"))
                .andExpect(jsonPath("$.data.bean.update_version").value(2));
        verify(lifecycle, times(1)).startService(eq("r1"), eq(TEAM), eq(SERVICE_ALIAS), ArgumentMatchers.any());

        // POST /stop
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/stop")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.event_id").value("ev-stop-1"))
                .andExpect(jsonPath("$.data.bean.update_version").value(3));

        // POST /restart
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/restart")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.event_id").value("ev-restart-1"))
                .andExpect(jsonPath("$.data.bean.update_version").value(4));
    }
}
