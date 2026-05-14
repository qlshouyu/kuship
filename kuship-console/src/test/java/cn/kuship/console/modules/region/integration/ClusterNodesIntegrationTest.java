package cn.kuship.console.modules.region.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ClusterNodes 集成测试（migrate-console-cluster-nodes）。
 *
 * <p>使用 {@code @MockitoBean ClusterOperations} 屏蔽真实 region 调用，
 * 验证 7 个端点的路径 / 鉴权 / 响应格式契约。
 *
 * <p>测试场景：
 * <ul>
 *   <li>T36: 7 个端点路径 / 鉴权 / 响应格式</li>
 *   <li>T37: 未认证请求返回 401</li>
 *   <li>T38: 非 enterprise admin 请求 nodes/action 返回 403</li>
 *   <li>T39: NodeAction 未知 action 返回 400</li>
 * </ul>
 *
 * <p>注意：{@code @MockitoBean} 在 Spring Boot Test 中每次测试方法后会 reset mock，
 * 因此 mock 配置放在 {@code @BeforeEach} 中（每次测试方法前重新配置），
 * DB seed / cleanup 保留在 {@code @BeforeAll} / {@code @AfterAll}。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClusterNodesIntegrationTest {

    private static final int ADMIN_USER_ID = 909070;
    private static final int NORMAL_USER_ID = 909071;
    private static final String ENTERPRISE = "kuship-test-ent-nodes";
    private static final String REGION_NAME = "kuship-test-region-nodes-1";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean
    ClusterOperations clusterOperations;

    @BeforeAll
    void seed() {
        // 创建测试企业
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'kuship-nodes-ent', '节点测试企业', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENTERPRISE);

        // 创建管理员用户
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password)",
                ADMIN_USER_ID, "nodes-admin@kuship.local", "kuship-nodes-admin",
                encoder.encode("nodes-admin@kuship.localpwd12345"), ENTERPRISE);
        jdbc.update("INSERT INTO enterprise_user_perm (user_id, enterprise_id, identity, token) "
                + "VALUES (?, ?, 'admin', ?) ON DUPLICATE KEY UPDATE identity='admin'",
                ADMIN_USER_ID, ENTERPRISE, "nodes-admin-token-" + System.currentTimeMillis());

        // 创建普通用户（非 admin）
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password)",
                NORMAL_USER_ID, "nodes-user@kuship.local", "kuship-nodes-user",
                encoder.encode("nodes-user@kuship.localpwd12345"), ENTERPRISE);
        // 普通用户不插入 enterprise_user_perm

        // 创建 region_info（desc 列不可为空，传入空字符串）
        jdbc.update("INSERT INTO region_info (region_id, region_name, region_alias, url, wsurl, httpdomain, tcpdomain, "
                + "ssl_ca_cert, cert_file, key_file, enterprise_id, status, region_type, `desc`, create_time, scope) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1', '[]', '', NOW(), 'private') "
                + "ON DUPLICATE KEY UPDATE region_name=VALUES(region_name)",
                "nodes-region-id-001", REGION_NAME, "节点测试集群",
                "https://mock-region:8443", "wss://mock-region:6060",
                "kuship-nodes.local", "tcp.kuship-nodes.local",
                "fake-ca", "fake-cert", "fake-key", ENTERPRISE);
    }

    /**
     * 每次测试方法前重新配置 mock。
     * 由于 {@code @MockitoBean} 在 Spring Boot Test 中每次测试方法后会 reset，
     * 需在 {@code @BeforeEach} 中重新设置 mock 行为。
     */
    @BeforeEach
    void setupMocks() {
        // 配置 mock：getClusterNodes 返回模拟节点列表
        Map<String, Object> nodeListResp = new java.util.LinkedHashMap<>();
        tools.jackson.databind.json.JsonMapper jm =
                tools.jackson.databind.json.JsonMapper.builder().build();
        tools.jackson.databind.node.ArrayNode arr = jm.createArrayNode();
        tools.jackson.databind.node.ObjectNode nodeObj = jm.createObjectNode();
        nodeObj.put("name", "test-node-1");
        nodeObj.put("architecture", "amd64");
        nodeObj.put("unschedulable", false);
        nodeObj.putArray("roles").add("master");
        tools.jackson.databind.node.ArrayNode conds = nodeObj.putArray("conditions");
        tools.jackson.databind.node.ObjectNode cond = jm.createObjectNode();
        cond.put("type", "Ready");
        cond.put("status", "True");
        conds.add(cond);
        tools.jackson.databind.node.ObjectNode resource = nodeObj.putObject("resource");
        resource.put("req_cpu", 2000);
        resource.put("cap_cpu", 8000);
        resource.put("req_memory", 4096000);
        resource.put("cap_memory", 16384000);
        arr.add(nodeObj);
        nodeListResp.put("list", arr);
        when(clusterOperations.getClusterNodes(anyString(), anyString())).thenReturn(nodeListResp);

        // 配置 mock：getNodeDetail
        Map<String, Object> nodeDetail = new java.util.LinkedHashMap<>();
        nodeDetail.put("name", "test-node-1");
        nodeDetail.put("architecture", "amd64");
        nodeDetail.put("external_ip", "192.168.1.1");
        nodeDetail.put("internal_ip", "10.0.0.1");
        nodeDetail.put("container_run_time", "docker://20.10.0");
        nodeDetail.put("roles", List.of("master"));
        nodeDetail.put("os_version", "Ubuntu 20.04");
        nodeDetail.put("unschedulable", false);
        nodeDetail.put("create_time", "2024-01-01T00:00:00Z");
        nodeDetail.put("kernel_version", "5.4.0");
        nodeDetail.put("operating_system", "linux");
        nodeDetail.put("conditions", List.of(Map.of("type", "Ready", "status", "True")));
        nodeDetail.put("resource", Map.of(
                "req_cpu", 2000, "cap_cpu", 8000,
                "req_memory", 4096000L, "cap_memory", 16384000L,
                "req_disk", 10737418240L, "cap_disk", 107374182400L,
                "cap_container_disk", 107374182400L, "req_container_disk", 10737418240L));
        when(clusterOperations.getNodeDetail(anyString(), anyString(), anyString()))
                .thenReturn(nodeDetail);

        // 配置 mock：operateNodeAction（白名单动作）
        when(clusterOperations.operateNodeAction(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of());

        // 配置 mock：getNodeLabels
        when(clusterOperations.getNodeLabels(anyString(), anyString(), anyString()))
                .thenReturn(Map.of("labels", Map.of("env", "prod")));

        // 配置 mock：updateNodeLabels
        when(clusterOperations.updateNodeLabels(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("labels", Map.of("env", "staging")));

        // 配置 mock：getNodeTaints
        when(clusterOperations.getNodeTaints(anyString(), anyString(), anyString()))
                .thenReturn(List.of(Map.of("key", "node-role.kubernetes.io/master",
                        "effect", "NoSchedule")));

        // 配置 mock：updateNodeTaints
        when(clusterOperations.updateNodeTaints(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM region_info WHERE region_name = ?", REGION_NAME);
        jdbc.update("DELETE FROM enterprise_user_perm WHERE user_id IN (?, ?)",
                ADMIN_USER_ID, NORMAL_USER_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id IN (?, ?)",
                ADMIN_USER_ID, NORMAL_USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENTERPRISE);
    }

    private String adminToken() {
        return tokenService.encode(
                new JwtClaims((long) ADMIN_USER_ID, "kuship-nodes-admin",
                        "nodes-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    private String normalToken() {
        return tokenService.encode(
                new JwtClaims((long) NORMAL_USER_ID, "kuship-nodes-user",
                        "nodes-user@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    // T36: 7 个端点路径 / 鉴权 / 响应格式

    @Test
    void getNodes_returnsNodeListAndRoleCount() throws Exception {
        mvc.perform(get("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME + "/nodes")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].name").value("test-node-1"))
                .andExpect(jsonPath("$.data.list[0].status").value("Ready"))
                .andExpect(jsonPath("$.data.list[0].arch").value("amd64"))
                .andExpect(jsonPath("$.data.bean").isMap());
    }

    @Test
    void getNode_returnsNodeDetail() throws Exception {
        mvc.perform(get("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME
                        + "/nodes/test-node-1")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.name").value("test-node-1"))
                .andExpect(jsonPath("$.data.bean.architecture").value("amd64"))
                .andExpect(jsonPath("$.data.bean.ip").value("192.168.1.1"))
                .andExpect(jsonPath("$.data.bean.status").value("Ready"));
    }

    @Test
    void nodeAction_knownAction_returns200() throws Exception {
        mvc.perform(post("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME
                        + "/nodes/test-node-1/action")
                        .header("Authorization", "GRJWT " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"unschedulable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getNodeLabels_returnsLabels() throws Exception {
        mvc.perform(get("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME
                        + "/nodes/test-node-1/labels")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.labels").isMap());
    }

    @Test
    void updateNodeLabels_returnsUpdatedLabels() throws Exception {
        mvc.perform(put("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME
                        + "/nodes/test-node-1/labels")
                        .header("Authorization", "GRJWT " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":{\"env\":\"staging\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getNodeTaints_returnsTaintsList() throws Exception {
        mvc.perform(get("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME
                        + "/nodes/test-node-1/taints")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].key").value("node-role.kubernetes.io/master"));
    }

    @Test
    void updateNodeTaints_returnsUpdatedTaints() throws Exception {
        mvc.perform(put("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME
                        + "/nodes/test-node-1/taints")
                        .header("Authorization", "GRJWT " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taints\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    // T37: 未认证请求返回 401

    @Test
    void unauthenticated_getNodes_returns401() throws Exception {
        mvc.perform(get("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME + "/nodes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void unauthenticated_nodeAction_returns401() throws Exception {
        mvc.perform(post("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME
                        + "/nodes/test-node-1/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"unschedulable\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // T38: 非 enterprise admin 请求 nodes/action 返回 403

    @Test
    void normalUser_nodeAction_returns403() throws Exception {
        mvc.perform(post("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME
                        + "/nodes/test-node-1/action")
                        .header("Authorization", "GRJWT " + normalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"unschedulable\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void normalUser_getNodes_returns403() throws Exception {
        mvc.perform(get("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME + "/nodes")
                        .header("Authorization", "GRJWT " + normalToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // T39: NodeAction 未知 action 返回 400

    @Test
    void nodeAction_unknownAction_returns400() throws Exception {
        mvc.perform(post("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME
                        + "/nodes/test-node-1/action")
                        .header("Authorization", "GRJWT " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"invalid_action\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void nodeAction_emptyAction_returns400() throws Exception {
        mvc.perform(post("/console/enterprise/" + ENTERPRISE + "/regions/" + REGION_NAME
                        + "/nodes/test-node-1/action")
                        .header("Authorization", "GRJWT " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
