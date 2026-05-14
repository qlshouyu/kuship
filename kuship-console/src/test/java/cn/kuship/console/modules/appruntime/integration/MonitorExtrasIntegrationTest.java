package cn.kuship.console.modules.appruntime.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.appruntime.api.MonitorOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * monitor-extras 4 个新 endpoint 的端到端集成测试。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonitorExtrasIntegrationTest {

    private static final int USER_ID = 909904;
    private static final String NICK = "kuship-monitor-admin";
    private static final String EMAIL = "monitor-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-monitor";
    private static final String TEAM = "kuship-monitor-team";
    private static final String TEAM_ID = "9099040404040404monitor7890123x";
    private static final String NAMESPACE = "ns-monitor-team";
    private static final String REGION = "rainbond";
    private static final String ALIAS = "svc-monitor";
    private static final String SERVICE_ID = "9099040404monitor0001id00000abc";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean MonitorOperations monitorOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'monitor-ent', 'MonitorTest', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'MonitorTeam') "
                + "ON DUPLICATE KEY UPDATE namespace=VALUES(namespace)",
                TEAM_ID, TEAM, USER_ID, NAMESPACE, ENT);

        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, container_gpu, "
                + "min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, creater, protocol, "
                + "total_memory, is_service, namespace, volume_type, port_type, service_origin, service_source, create_status, "
                + "tenant_service_group_id, open_webhooks, server_type, is_upgrate, build_upgrade, service_name, k8s_component_name, update_time, secret) "
                + "VALUES (?, ?, 'app', ?, '组件', ?, 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, 256, "
                + "'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'assistant', 'docker_run', 'complete', "
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
    void metricsHappy_passesTenantIdAndServiceId() throws Exception {
        when(monitorOps.getMonitorMetrics(eq(REGION), eq(TEAM_ID), eq("component"), eq(""), eq(SERVICE_ID)))
                .thenReturn(Map.of("list", List.of(Map.of("metric", "cpu_usage"))));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/metrics")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg_show").value("OK"))
                .andExpect(jsonPath("$.data.bean.list[0].metric").value("cpu_usage"));

        verify(monitorOps).getMonitorMetrics(REGION, TEAM_ID, "component", "", SERVICE_ID);
    }

    @Test
    void sortDomainQuery_paginatesPromResult() throws Exception {
        // 模拟 region 端 prom 返结果
        Map<String, Object> data = Map.of("result", List.of(
                Map.of("metric", Map.of("host", "h1"), "value", List.of(1700, "100")),
                Map.of("metric", Map.of("host", "h2"), "value", List.of(1700, "200")),
                Map.of("metric", Map.of("host", "h3"), "value", List.of(1700, "300"))
        ));
        when(monitorOps.queryDomainAccess(eq(REGION), eq(TEAM), any())).thenReturn(
                Map.of("data", data));

        mvc.perform(get("/console/teams/" + TEAM + "/region/" + REGION + "/sort_domain/query?page=1&page_size=2")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.total").value(3))
                .andExpect(jsonPath("$.data.bean.total_traffic").value(600))
                .andExpect(jsonPath("$.data.list.length()").value(2));
    }

    @Test
    void sortServiceQuery_mergesOuterAndInner() throws Exception {
        Map<String, Object> outer = Map.of("data", Map.of("result", List.of(
                Map.of("metric", Map.of("service", "svc-a"), "value", List.of(1700, "100")),
                Map.of("metric", Map.of("service", "svc-b"), "value", List.of(1700, "50"))
        )));
        Map<String, Object> inner = Map.of("data", Map.of("result", List.of(
                Map.of("metric", Map.of("service_id", "id-c"), "value", List.of(1700, "30"))
        )));
        when(monitorOps.queryServiceAccess(eq(REGION), eq(TEAM), any())).thenReturn(outer, inner);

        mvc.perform(get("/console/teams/" + TEAM + "/region/" + REGION + "/sort_service/query")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(3));

        verify(monitorOps, times(2)).queryServiceAccess(eq(REGION), eq(TEAM), any());
    }

    @Test
    void metricsRegion5xx_passesThrough() throws Exception {
        when(monitorOps.getMonitorMetrics(eq(REGION), eq(TEAM_ID), eq("component"), eq(""), eq(SERVICE_ID)))
                .thenThrow(new RegionApiException("monitor",
                        "/v2/monitor/metrics", "GET",
                        503, 503, "region down", "监控服务不可用", Map.of(), null));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/metrics")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.msg_show").value("监控服务不可用"));
    }

}
