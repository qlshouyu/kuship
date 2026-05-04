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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** vertical / horizontal 写本地配置 + 调用 region。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppScalingIntegrationTest {

    private static final int USER_ID = 909083;
    private static final String NICK = "kuship-scaling-admin";
    private static final String ENT = "kuship-test-ent-scaling";
    private static final String TEAM = "kuship-test-team-scaling";
    private static final String TEAM_ID = "9090838383838383sc1234567890123";
    private static final String SERVICE_ID = "sctest909083svc1234567890abcdef0";
    private static final String SERVICE_ALIAS = "kuship-sc-svc";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean ServiceLifecycleOperations lifecycle;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'sc-ent', 'SCTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "sc-admin@kuship.local", NICK,
                encoder.encode("sc-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'SCTeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM, USER_ID, TEAM, ENT);
        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, "
                + "container_gpu, min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, "
                + "creater, protocol, total_memory, is_service, namespace, volume_type, port_type, service_origin, "
                + "tenant_service_group_id, open_webhooks, server_type, is_upgrate, build_upgrade, service_name, "
                + "k8s_component_name, update_time) "
                + "VALUES (?, ?, 'app', ?, 'SCSvc', 'r1', 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, 256, "
                + "'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'assistant', 0, 0, 'tcp', 0, 0, "
                + "'kuship-sc-svc', 'kuship-sc-svc', NOW()) "
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
                new JwtClaims((long) USER_ID, NICK, "sc-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void vertical_writesMinCpuAndMemoryAndCallsRegion() throws Exception {
        when(lifecycle.verticalUpgrade(eq("r1"), eq(TEAM), eq(SERVICE_ALIAS), ArgumentMatchers.any()))
                .thenReturn(Map.of("event_id", "ev-vertical"));
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/vertical")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"new_cpu\":2000,\"new_memory\":1024,\"new_gpu\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Integer cpu = jdbc.queryForObject("SELECT min_cpu FROM tenant_service WHERE service_id = ?", Integer.class, SERVICE_ID);
        Integer mem = jdbc.queryForObject("SELECT min_memory FROM tenant_service WHERE service_id = ?", Integer.class, SERVICE_ID);
        assert cpu != null && cpu == 2000 : "min_cpu should be 2000, got " + cpu;
        assert mem != null && mem == 1024 : "min_memory should be 1024, got " + mem;
        verify(lifecycle, times(1)).verticalUpgrade(eq("r1"), eq(TEAM), eq(SERVICE_ALIAS), ArgumentMatchers.any());
    }

    @Test
    void horizontal_writesMinNodeAndCallsRegion() throws Exception {
        when(lifecycle.horizontalUpgrade(eq("r1"), eq(TEAM), eq(SERVICE_ALIAS), ArgumentMatchers.any()))
                .thenReturn(Map.of("event_id", "ev-horizontal"));
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/horizontal")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"new_node\":3}"))
                .andExpect(status().isOk());
        Integer node = jdbc.queryForObject("SELECT min_node FROM tenant_service WHERE service_id = ?", Integer.class, SERVICE_ID);
        assert node != null && node == 3 : "min_node should be 3, got " + node;
    }
}
