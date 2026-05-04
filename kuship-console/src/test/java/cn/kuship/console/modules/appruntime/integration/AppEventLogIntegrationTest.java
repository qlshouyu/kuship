package cn.kuship.console.modules.appruntime.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.api.EventOperations;
import cn.kuship.console.infrastructure.region.api.ServiceLogOperations;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证 events / event_log / log_instance 透传响应字段保留。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppEventLogIntegrationTest {

    private static final int USER_ID = 909085;
    private static final String NICK = "kuship-evt-admin";
    private static final String ENT = "kuship-test-ent-evt";
    private static final String TEAM = "kuship-test-team-evt";
    private static final String TEAM_ID = "9090858585858585ev1234567890123";
    private static final String SERVICE_ID = "evttest909085svc1234567890abcde0";
    private static final String SERVICE_ALIAS = "kuship-evt-svc";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean EventOperations events;
    @MockitoBean ServiceLogOperations logOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'evt-ent', 'EVTTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "evt-admin@kuship.local", NICK,
                encoder.encode("evt-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'EVTTeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM, USER_ID, TEAM, ENT);
        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, "
                + "container_gpu, min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, "
                + "creater, protocol, total_memory, is_service, namespace, volume_type, port_type, service_origin, "
                + "tenant_service_group_id, open_webhooks, server_type, is_upgrate, build_upgrade, service_name, "
                + "k8s_component_name, update_time) "
                + "VALUES (?, ?, 'app', ?, 'EVTSvc', 'r1', 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, 256, "
                + "'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'assistant', 0, 0, 'tcp', 0, 0, "
                + "'kuship-evt-svc', 'kuship-evt-svc', NOW()) "
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
                new JwtClaims((long) USER_ID, NICK, "evt-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void events_passesThroughTotalAndList() throws Exception {
        when(events.getTargetEventsList(ArgumentMatchers.eq("r1"), ArgumentMatchers.eq(TEAM), ArgumentMatchers.any()))
                .thenReturn(Map.of(
                        "total", 2,
                        "list", List.of(Map.of("event_id", "ev1"), Map.of("event_id", "ev2"))));
        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/events?page=1&page_size=10")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.total").value(2))
                .andExpect(jsonPath("$.data.bean.list.length()").value(2))
                .andExpect(jsonPath("$.data.bean.list[0].event_id").value("ev1"));
    }

    @Test
    void eventLog_passesThroughLines() throws Exception {
        when(events.getEventLog(ArgumentMatchers.eq("r1"), ArgumentMatchers.eq(TEAM), ArgumentMatchers.any()))
                .thenReturn(Map.of("event_id", "ev99",
                        "log", List.of("line-1", "line-2", "line-3")));
        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/event_log?event_id=ev99")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.event_id").value("ev99"))
                .andExpect(jsonPath("$.data.bean.log.length()").value(3));
    }

    @Test
    void logInstance_passesThroughHostPathToken() throws Exception {
        when(logOps.getDockerLogInstance(ArgumentMatchers.eq("r1"), ArgumentMatchers.eq(TEAM), ArgumentMatchers.eq(SERVICE_ALIAS)))
                .thenReturn(Map.of(
                        "host", "192.0.2.10:6060",
                        "path", "/log/svc",
                        "token", "wsT0kenAbC"));
        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/log_instance")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.host").value("192.0.2.10:6060"))
                .andExpect(jsonPath("$.data.bean.path").value("/log/svc"))
                .andExpect(jsonPath("$.data.bean.token").value("wsT0kenAbC"));
    }
}
