package cn.kuship.console.modules.appruntime.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.appruntime.api.AutoscalerOperations;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** xparules POST 双写本地 autoscaler_rules + autoscaler_rule_metrics + region 调用。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppAutoscalerIntegrationTest {

    private static final int USER_ID = 909084;
    private static final String NICK = "kuship-xpa-admin";
    private static final String ENT = "kuship-test-ent-xpa";
    private static final String TEAM = "kuship-test-team-xpa";
    private static final String TEAM_ID = "9090848484848484xp1234567890123";
    private static final String SERVICE_ID = "xpatest909084svc1234567890abcd0";
    private static final String SERVICE_ALIAS = "kuship-xpa-svc";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean AutoscalerOperations regionAutoscaler;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'xpa-ent', 'XPATest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "xpa-admin@kuship.local", NICK,
                encoder.encode("xpa-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'XPATeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM, USER_ID, TEAM, ENT);
        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, "
                + "container_gpu, min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, "
                + "creater, protocol, total_memory, is_service, namespace, volume_type, port_type, service_origin, "
                + "tenant_service_group_id, open_webhooks, server_type, is_upgrate, build_upgrade, service_name, "
                + "k8s_component_name, update_time) "
                + "VALUES (?, ?, 'app', ?, 'XPASvc', 'r1', 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, 256, "
                + "'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'assistant', 0, 0, 'tcp', 0, 0, "
                + "'kuship-xpa-svc', 'kuship-xpa-svc', NOW()) "
                + "ON DUPLICATE KEY UPDATE service_alias=VALUES(service_alias)",
                SERVICE_ID, TEAM_ID, SERVICE_ALIAS, USER_ID, TEAM);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM autoscaler_rule_metrics WHERE rule_id IN (SELECT rule_id FROM autoscaler_rules WHERE service_id = ?)", SERVICE_ID);
        jdbc.update("DELETE FROM autoscaler_rules WHERE service_id = ?", SERVICE_ID);
        jdbc.update("DELETE FROM tenant_service WHERE service_id = ?", SERVICE_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "xpa-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void createXparule_writesBothTablesAndCallsRegion() throws Exception {
        when(regionAutoscaler.createRule(eq("r1"), eq(TEAM), eq(SERVICE_ALIAS), ArgumentMatchers.any()))
                .thenReturn(Map.of("rule_id", "stub-from-region"));

        MvcResult res = mvc.perform(post("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/xparules")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"min_replicas\":1,\"max_replicas\":5,\"xpa_type\":\"hpa\","
                                + "\"metrics\":[{\"metric_type\":\"resource_metrics\",\"metric_name\":\"cpu\","
                                + "\"metric_target_type\":\"utilization\",\"metric_target_value\":80}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.min_replicas").value(1))
                .andExpect(jsonPath("$.data.bean.max_replicas").value(5))
                .andReturn();
        String body = res.getResponse().getContentAsString();
        String ruleId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).path("data").path("bean").path("rule_id").asText();

        Integer ruleCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM autoscaler_rules WHERE rule_id = ?", Integer.class, ruleId);
        assert ruleCount != null && ruleCount == 1 : "autoscaler_rules should have 1 row";
        Integer metricCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM autoscaler_rule_metrics WHERE rule_id = ?", Integer.class, ruleId);
        assert metricCount != null && metricCount == 1 : "autoscaler_rule_metrics should have 1 row";
        verify(regionAutoscaler, times(1)).createRule(eq("r1"), eq(TEAM), eq(SERVICE_ALIAS), ArgumentMatchers.any());

        // GET 列表
        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/xparules")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(1));
    }

    @Test
    void createXparule_invalidMetricsRejected() throws Exception {
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/xparules")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"min_replicas\":1,\"max_replicas\":5,\"xpa_type\":\"hpa\",\"metrics\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg_show").value("metrics 不能为空"));
    }
}
