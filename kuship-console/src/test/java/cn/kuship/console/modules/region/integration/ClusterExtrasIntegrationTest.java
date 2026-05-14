package cn.kuship.console.modules.region.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * cluster-extras 4 controller 端到端集成测试。HelmOperations 风格：@MockitoBean ClusterOperations
 * 替换 region 调用，断言 controller→ops 入参与响应 general_message 形状。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClusterExtrasIntegrationTest {

    private static final int USER_ID = 909801;
    private static final String NICK = "kuship-cluster-admin";
    private static final String EMAIL = "cluster-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-cluster";
    private static final String TEAM = "kuship-cluster-team";
    private static final String TEAM_ID = "9098010101010101cluster78901234x";
    private static final String NAMESPACE = "ns-cluster-team";
    private static final String REGION = "rainbond";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean ClusterOperations clusterOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'cluster-ent', 'ClusterTest', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO enterprise_user_perm (user_id, enterprise_id, identity, token) "
                + "VALUES (?, ?, 'admin', ?) "
                + "ON DUPLICATE KEY UPDATE identity='admin'",
                USER_ID, ENT, "cluster-admin-token-" + System.currentTimeMillis());
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'ClusterTeam') "
                + "ON DUPLICATE KEY UPDATE namespace=VALUES(namespace)",
                TEAM_ID, TEAM, USER_ID, NAMESPACE, ENT);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM enterprise_user_perm WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, EMAIL, null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void clusterInfo_happyPath_returnsGeneralMessage() throws Exception {
        when(clusterOps.getClusterInfo(REGION)).thenReturn(Map.of(
                "version", "v1.28",
                "capacity", Map.of("cpu", "16")));

        mvc.perform(get("/console/enterprise/" + ENT + "/regions/" + REGION + "/info")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.bean.version").value("v1.28"));
    }

    @Test
    void clusterEvents_queryStringPassthrough() throws Exception {
        when(clusterOps.getClusterEvents(eq(REGION), any())).thenReturn(Map.of(
                "list", java.util.List.of(Map.of("reason", "BackOff"))));

        mvc.perform(get("/console/enterprise/" + ENT + "/regions/" + REGION
                                + "/cluster-events?type=warning&since=1h")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass((Class) Map.class);
        verify(clusterOps).getClusterEvents(eq(REGION), captor.capture());
        Map<String, Object> body = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("warning", body.get("type"));
        org.junit.jupiter.api.Assertions.assertEquals("1h", body.get("since"));
    }

    // /nodes 端点的 listNodes_happyPath / nodeDetail_nameWithDot_passesThrough / nodes_region5xx_passesThrough
    // 三个原 cluster-extras 测试在 leader 整合阶段被 cluster-nodes worktree 的 ClusterNodesController 接管：
    // 新 controller 经 ClusterNodeService 包装（ClusterNodesResult record + role count 增强），
    // response shape 与 cluster-extras 旧版不同，这里删除冗余测试，行为契约改由
    // ClusterNodesIntegrationTest（cluster-nodes change 自带 13 个集成测试）承担。

    @Test
    void tenantResources_happyPath_passesEnterpriseId() throws Exception {
        when(clusterOps.getResources(eq(REGION), eq(TEAM), anyString())).thenReturn(Map.of(
                "cpu", 1000, "memory", 2048));

        mvc.perform(get("/console/teams/" + TEAM + "/resources?region_name=" + REGION
                                + "&enterprise_id=" + ENT)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.cpu").value(1000));

        verify(clusterOps).getResources(REGION, TEAM, ENT);
    }
}
