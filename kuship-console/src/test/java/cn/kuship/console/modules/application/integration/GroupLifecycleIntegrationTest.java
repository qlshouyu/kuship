package cn.kuship.console.modules.application.integration;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** ServiceGroup（应用）生命周期：sysAdmin 创建 → 改 governance → 列表 → 删除空应用。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupLifecycleIntegrationTest {

    private static final int USER_ID = 909070;
    private static final String NICK = "kuship-app-admin";
    private static final String ENT = "kuship-test-ent-app";
    private static final String TEAM = "kuship-test-team-app";
    private static final String TEAM_ID = "9090707070707070app1234567890123";
    private static final String GROUP_NAME = "kuship-test-app";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'app-ent', 'AppTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        // sys_admin user 直接通过权限校验
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "app-admin@kuship.local", NICK,
                encoder.encode("app-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'AppTeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM, USER_ID, TEAM, ENT);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM service_group_relation WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM service_group WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "app-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void groupLifecycle_create_governance_delete() throws Exception {
        // 1. 创建 application
        var created = mvc.perform(post("/console/teams/" + TEAM + "/groups")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group_name\":\"" + GROUP_NAME + "\","
                                + "\"region_name\":\"r1\",\"k8s_app\":\"k8s-test\","
                                + "\"governance_mode\":\"KUBERNETES_NATIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.group_name").value(GROUP_NAME))
                .andExpect(jsonPath("$.data.bean.governance_mode").value("KUBERNETES_NATIVE"))
                .andReturn();
        Integer appId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString())
                .path("data").path("bean").path("app_id").asInt();

        // 2. 列表（包含新建项）
        mvc.perform(get("/console/teams/" + TEAM + "/groups")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.group_name == '" + GROUP_NAME + "')].app_id").exists());

        // 3. 改 governance_mode
        mvc.perform(put("/console/teams/" + TEAM + "/groups/" + appId + "/governancemode")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"governance_mode\":\"NO_GOVERNANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.governance_mode").value("NO_GOVERNANCE"));

        // 4. 非法 governance_mode 拒绝
        mvc.perform(put("/console/teams/" + TEAM + "/groups/" + appId + "/governancemode")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"governance_mode\":\"BAD_VALUE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // 5. 详情
        mvc.perform(get("/console/teams/" + TEAM + "/groups/" + appId)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.governance_mode").value("NO_GOVERNANCE"));

        // 6. 空应用删除成功
        mvc.perform(delete("/console/teams/" + TEAM + "/groups/" + appId)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
