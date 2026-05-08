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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 验证 @RequirePerm AOP：sysAdmin 直通；普通用户无权限 → 403；普通用户挂上 role 后通过。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermAspectIntegrationTest {

    private static final int SYS_ADMIN_ID = 909046;
    private static final int NO_PERM_ID = 909047;
    private static final int WITH_PERM_ID = 909048;
    private static final String TEAM = "kuship-test-perm-team";
    private static final String ENTERPRISE = "kuship-test-ent-perm";
    private static int teamPk;
    private static int roleId;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        // 企业 + 3 个用户
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'kuship-perm-ent', '权限测试企业', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENTERPRISE);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=VALUES(sys_admin)",
                SYS_ADMIN_ID, "sys-admin-perm@kuship.local", "kuship-perm-sysadmin",
                encoder.encode("sys-admin-perm@kuship.localpwd12345"), ENTERPRISE);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password)",
                NO_PERM_ID, "noperm@kuship.local", "kuship-perm-noperm",
                encoder.encode("noperm@kuship.localpwd12345"), ENTERPRISE);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password)",
                WITH_PERM_ID, "withperm@kuship.local", "kuship-perm-withperm",
                encoder.encode("withperm@kuship.localpwd12345"), ENTERPRISE);
        // team
        String tenantId = "permtest" + System.currentTimeMillis();
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, '权限测试团队')",
                tenantId, TEAM, SYS_ADMIN_ID, TEAM, ENTERPRISE);
        teamPk = jdbc.queryForObject("SELECT ID FROM tenant_info WHERE tenant_name = ?", Integer.class, TEAM);
        // role + role_perms 让 WITH_PERM_ID 拥有 team_role_perms
        jdbc.update("INSERT INTO role_info (name, kind_id, kind) VALUES ('test-perm-role', ?, 'team')", String.valueOf(teamPk));
        roleId = jdbc.queryForObject("SELECT ID FROM role_info WHERE name = 'test-perm-role' AND kind_id = ?",
                Integer.class, String.valueOf(teamPk));
        // 确保 perms_info 含 team_role_perms（PermsInitService 启动会写入；此处 upsert 兜底）
        jdbc.update("INSERT INTO perms_info (name, `desc`, code, `group`, kind) VALUES ('team_role_perms', '团队角色管理', 200011, 'admin', 'team') "
                + "ON DUPLICATE KEY UPDATE code=VALUES(code)");
        Integer permCode = jdbc.queryForObject("SELECT code FROM perms_info WHERE name = 'team_role_perms'", Integer.class);
        jdbc.update("INSERT INTO role_perms (role_id, perm_code, app_id) VALUES (?, ?, -1)", roleId, permCode);
        // user_role 关联 WITH_PERM_ID
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", String.valueOf(WITH_PERM_ID), String.valueOf(roleId));
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM user_role WHERE user_id IN (?, ?, ?)",
                String.valueOf(SYS_ADMIN_ID), String.valueOf(NO_PERM_ID), String.valueOf(WITH_PERM_ID));
        jdbc.update("DELETE FROM role_perms WHERE role_id = ?", roleId);
        jdbc.update("DELETE FROM role_info WHERE ID = ?", roleId);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_name = ?", TEAM);
        jdbc.update("DELETE FROM user_info WHERE user_id IN (?, ?, ?)", SYS_ADMIN_ID, NO_PERM_ID, WITH_PERM_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENTERPRISE);
    }

    private String tokenFor(int userId, String username) {
        return tokenService.encode(
                new JwtClaims((long) userId, username, username + "@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void sysAdmin_bypassesPermCheck() throws Exception {
        // sys_admin 调用要求 team_role_perms 的端点 → 直接放行
        mvc.perform(get("/console/teams/" + TEAM + "/roles")
                        .header("Authorization", "GRJWT " + tokenFor(SYS_ADMIN_ID, "kuship-perm-sysadmin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void normalUser_withoutPerm_returns403() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/roles")
                        .header("Authorization", "GRJWT " + tokenFor(NO_PERM_ID, "kuship-perm-noperm")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg_show").value("您无操作此功能的权限"));
    }

    @Test
    void normalUser_withPerm_passes() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/roles")
                        .header("Authorization", "GRJWT " + tokenFor(WITH_PERM_ID, "kuship-perm-withperm")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
