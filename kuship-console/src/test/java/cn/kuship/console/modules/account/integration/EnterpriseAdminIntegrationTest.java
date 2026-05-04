package cn.kuship.console.modules.account.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Enterprise admin 集成测试：admin 创建用户成功 / 普通用户无权 / 公开 enterprise/info 端点已在 AccountAuthIntegrationTest 覆盖。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnterpriseAdminIntegrationTest {

    private static final int ADMIN_USER_ID = 909049;
    private static final int NORMAL_USER_ID = 909050;
    private static final String ENTERPRISE = "kuship-test-ent-admin";
    private static final String CREATED_USER_NICK = "kuship-admin-created";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'kuship-admin-ent', '管理测试企业', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENTERPRISE);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password)",
                ADMIN_USER_ID, "admin-test@kuship.local", "kuship-admin",
                encoder.encode("admin-test@kuship.localpwd12345"), ENTERPRISE);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password)",
                NORMAL_USER_ID, "normal-test@kuship.local", "kuship-normal",
                encoder.encode("normal-test@kuship.localpwd12345"), ENTERPRISE);
        // ADMIN_USER_ID 加入 enterprise_user_perm 作为 admin
        jdbc.update("INSERT INTO enterprise_user_perm (user_id, enterprise_id, identity, token) "
                + "VALUES (?, ?, 'admin', ?) ON DUPLICATE KEY UPDATE identity='admin'",
                ADMIN_USER_ID, ENTERPRISE, "kuship-admin-token-" + System.currentTimeMillis());
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM enterprise_user_perm WHERE user_id = ?", ADMIN_USER_ID);
        jdbc.update("DELETE FROM user_info WHERE nick_name = ?", CREATED_USER_NICK);
        jdbc.update("DELETE FROM user_info WHERE user_id IN (?, ?)", ADMIN_USER_ID, NORMAL_USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENTERPRISE);
    }

    private String tokenFor(int userId, String username) {
        return tokenService.encode(
                new JwtClaims((long) userId, username, username + "@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void admin_canCreateEnterpriseUser() throws Exception {
        mvc.perform(post("/console/enterprise/" + ENTERPRISE + "/users")
                        .header("Authorization", "GRJWT " + tokenFor(ADMIN_USER_ID, "kuship-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_name\":\"" + CREATED_USER_NICK + "\","
                                + "\"email\":\"created@kuship.local\","
                                + "\"password\":\"createdpwd12345\","
                                + "\"phone\":\"\",\"real_name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.nick_name").value(CREATED_USER_NICK));
    }

    @Test
    void normalUser_cannotCreateEnterpriseUser() throws Exception {
        mvc.perform(post("/console/enterprise/" + ENTERPRISE + "/users")
                        .header("Authorization", "GRJWT " + tokenFor(NORMAL_USER_ID, "kuship-normal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_name\":\"would-fail\","
                                + "\"email\":\"would-fail@kuship.local\","
                                + "\"password\":\"failpwd12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg_show").value("您无操作此功能的权限"));
    }

    @Test
    void enterpriseUsers_listIsReadable_byNormalUser() throws Exception {
        // GET /enterprise/{id}/users 不要求 admin（只要登录）
        mvc.perform(get("/console/enterprise/" + ENTERPRISE + "/users")
                        .header("Authorization", "GRJWT " + tokenFor(NORMAL_USER_ID, "kuship-normal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
