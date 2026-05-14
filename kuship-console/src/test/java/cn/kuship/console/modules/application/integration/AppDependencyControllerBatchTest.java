package cn.kuship.console.modules.application.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.api.ServiceDependencyOperations;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.application.service.AppDependencyBatchService;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /console/teams/{team_name}/apps/{service_alias}/dependency-list} 集成测试。
 *
 * <p>测试场景：
 * <ul>
 *   <li>happy path — 200 + general_message 形状</li>
 *   <li>无权限（普通成员，无 {@code app_create_perms}）— 403</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppDependencyControllerBatchTest {

    private static final int ADMIN_UID = 908901;
    private static final int MEMBER_UID = 908902;
    private static final String ENT_ID  = "depctrlent089012345678901234567a";  // 32 chars
    private static final String TEAM_ID = "depctrlteam08901234567890a123456";  // 32 chars
    private static final String TEAM_NAME = "dep-ctrl-team-0890";
    private static final String NAMESPACE = "dep-ctrl-ns-0890";
    private static final String SVC_ID  = "depctrlsvc0890abcdef123456789012";  // 32 chars
    private static final String SVC_ALIAS = "dep-ctrl-svc-0890";
    private static final String DEP_A = "depctrlda0890abc0123456789012345";    // 32 chars

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    // AppDependencyBatchService 在此测试里 Mock，仅校验 controller 层行为
    @MockitoBean AppDependencyBatchService batchService;
    @MockitoBean ServiceDependencyOperations serviceDependencyOps;

    @BeforeAll
    void seed() {
        // 企业
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'DepCtrl Ent', 'DepCtrlEnt', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT_ID);

        // 管理员（sys_admin=1 → RequirePerm 直接放行）
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                ADMIN_UID, "dep-ctrl-admin@kuship.local", "DepCtrlAdmin",
                encoder.encode("dep-ctrl-admin@kuship.localpwd12345"), ENT_ID);

        // 普通成员（sys_admin=0，无任何 role）
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=0",
                MEMBER_UID, "dep-ctrl-member@kuship.local", "DepCtrlMember",
                encoder.encode("dep-ctrl-member@kuship.localpwd12345"), ENT_ID);

        // 团队
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'DepCtrl') ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM_NAME, ADMIN_UID, NAMESPACE, ENT_ID);

        // 组件
        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, container_gpu, "
                + "min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, creater, protocol, "
                + "total_memory, is_service, namespace, volume_type, port_type, service_origin, tenant_service_group_id, "
                + "open_webhooks, server_type, is_upgrate, build_upgrade, service_name, k8s_component_name, update_time) "
                + "VALUES (?, ?, 'app', ?, 'DepCtrlSvc', 'r1', 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, "
                + "256, 'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'assistant', 0, "
                + "0, 'tcp', 0, 0, ?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE service_region=VALUES(service_region)",
                SVC_ID, TEAM_ID, SVC_ALIAS, ADMIN_UID, NAMESPACE, SVC_ALIAS, SVC_ALIAS);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM tenant_service_relation WHERE service_id = ?", SVC_ID);
        jdbc.update("DELETE FROM tenant_service WHERE service_id = ?", SVC_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id IN (?, ?)", ADMIN_UID, MEMBER_UID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT_ID);
    }

    private String adminToken() {
        Instant now = Instant.now();
        JwtClaims claims = new JwtClaims((long) ADMIN_UID, "DepCtrlAdmin",
                "dep-ctrl-admin@kuship.local", now, now.plus(Duration.ofDays(1)), Map.of());
        return tokenService.encode(claims, Duration.ofDays(1));
    }

    private String memberToken() {
        Instant now = Instant.now();
        JwtClaims claims = new JwtClaims((long) MEMBER_UID, "DepCtrlMember",
                "dep-ctrl-member@kuship.local", now, now.plus(Duration.ofDays(1)), Map.of());
        return tokenService.encode(claims, Duration.ofDays(1));
    }

    /**
     * Happy path：管理员（sys_admin=1）POST dependency-list → 200 + general_message 形状。
     */
    @Test
    void postDependencyList_adminUser_returns200() throws Exception {
        when(batchService.addBatch(anyString(), anyString(), any()))
                .thenReturn(Map.of("result", "ok"));

        String body = "{\"dep_service_ids\":[\"" + DEP_A + "\"]}";

        mvc.perform(post("/console/teams/" + TEAM_NAME + "/apps/" + SVC_ALIAS + "/dependency-list")
                        .header("Authorization", "GRJWT " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.bean").exists());
    }

    /**
     * 权限校验：普通成员（无 app_create_perms 且非 sys_admin）→ 403。
     */
    @Test
    void postDependencyList_noPermMember_returns403() throws Exception {
        String body = "{\"dep_service_ids\":[\"" + DEP_A + "\"]}";

        mvc.perform(post("/console/teams/" + TEAM_NAME + "/apps/" + SVC_ALIAS + "/dependency-list")
                        .header("Authorization", "GRJWT " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
