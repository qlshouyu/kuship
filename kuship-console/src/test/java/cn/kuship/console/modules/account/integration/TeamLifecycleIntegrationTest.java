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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Team 生命周期集成测试：创建 team → 改 alias → 退出 → 删除。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TeamLifecycleIntegrationTest {

    private static final int OWNER_USER_ID = 909044;
    private static final int OTHER_USER_ID = 909045;
    private static final String TEST_TEAM = "kuship-test-team-lifecycle";
    private static final String TEST_ENTERPRISE = "kuship-test-ent-life";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'kuship-test-ent', '测试企业', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", TEST_ENTERPRISE);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password)",
                OWNER_USER_ID, "owner-life@kuship.local", "kuship-life-owner",
                encoder.encode("owner-life@kuship.localpassword12345"), TEST_ENTERPRISE);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password)",
                OTHER_USER_ID, "other-life@kuship.local", "kuship-life-other",
                encoder.encode("other-life@kuship.localpassword12345"), TEST_ENTERPRISE);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM tenant_perms WHERE user_id IN (?, ?)", OWNER_USER_ID, OTHER_USER_ID);
        jdbc.update("DELETE FROM tenant_region WHERE tenant_id IN (SELECT tenant_id FROM tenant_info WHERE tenant_name = ?)", TEST_TEAM);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_name = ?", TEST_TEAM);
        jdbc.update("DELETE FROM user_info WHERE user_id IN (?, ?)", OWNER_USER_ID, OTHER_USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", TEST_ENTERPRISE);
    }

    private String tokenFor(int userId, String username) {
        return tokenService.encode(
                new JwtClaims((long) userId, username, username + "@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void teamLifecycle_create_update_exit_delete() throws Exception {
        String ownerToken = tokenFor(OWNER_USER_ID, "kuship-life-owner");

        // 1. 创建 team
        mvc.perform(post("/console/teams/init")
                        .header("Authorization", "GRJWT " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team_name\":\"" + TEST_TEAM + "\","
                                + "\"team_alias\":\"测试团队\","
                                + "\"namespace\":\"" + TEST_TEAM + "\","
                                + "\"enterprise_id\":\"" + TEST_ENTERPRISE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.team_name").value(TEST_TEAM))
                .andExpect(jsonPath("$.data.bean.creater").value(OWNER_USER_ID));

        // 2. 改 alias
        mvc.perform(put("/console/teams/" + TEST_TEAM)
                        .header("Authorization", "GRJWT " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team_alias\":\"测试团队-V2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.team_alias").value("测试团队-V2"));

        // 3. 唯一 owner 不能退出
        mvc.perform(post("/console/teams/" + TEST_TEAM + "/exit")
                        .header("Authorization", "GRJWT " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg_show").value("团队仅剩一位 owner，无法退出"));

        // 4. 非 owner 改不了 team
        String otherToken = tokenFor(OTHER_USER_ID, "kuship-life-other");
        mvc.perform(put("/console/teams/" + TEST_TEAM)
                        .header("Authorization", "GRJWT " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team_alias\":\"hijack\"}"))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg_show").value("无该团队管理权限"));

        // 5. owner 删除 team
        mvc.perform(delete("/console/teams/" + TEST_TEAM)
                        .header("Authorization", "GRJWT " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
