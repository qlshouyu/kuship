package cn.kuship.console.modules.plugin.team.integration;

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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 插件 CRUD：POST 创建 + GET 列表 + 详情 + DELETE。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PluginCrudIntegrationTest {

    private static final int USER_ID = 909201;
    private static final String NICK = "kuship-plugin-admin";
    private static final String ENT = "kuship-test-ent-plg";
    private static final String TEAM = "kuship-test-team-plg";
    private static final String TEAM_ID = "9092010101010101pl1234567890123";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'plg-ent', 'PLGTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "plg-admin@kuship.local", NICK,
                encoder.encode("plg-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'PLGTeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM, USER_ID, TEAM, ENT);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM tenant_plugin WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "plg-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void create_get_delete_pluginRoundtrip() throws Exception {
        // POST 创建
        MvcResult res = mvc.perform(post("/console/teams/" + TEAM + "/plugins")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plugin_name\":\"my-sidecar\",\"plugin_alias\":\"侧车\","
                                + "\"category\":\"net-plugin:up\",\"build_source\":\"image\","
                                + "\"image\":\"nginx:1.20\",\"region\":\"r1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.plugin_name").value("my-sidecar"))
                .andReturn();
        String body = res.getResponse().getContentAsString();
        String pluginId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).path("data").path("bean").path("plugin_id").asText();

        // 数据库写入
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_plugin WHERE plugin_id = ? AND tenant_id = ?",
                Integer.class, pluginId, TEAM_ID);
        assert count != null && count == 1 : "tenant_plugin should have 1 row";

        // GET 列表
        mvc.perform(get("/console/teams/" + TEAM + "/plugins")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.plugin_id=='" + pluginId + "')].plugin_name")
                        .value(org.hamcrest.Matchers.hasItem("my-sidecar")));

        // GET 详情
        mvc.perform(get("/console/teams/" + TEAM + "/plugins/" + pluginId)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.plugin_alias").value("侧车"));

        // DELETE
        mvc.perform(delete("/console/teams/" + TEAM + "/plugins/" + pluginId)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());
        Integer countAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_plugin WHERE plugin_id = ?", Integer.class, pluginId);
        assert countAfter != null && countAfter == 0 : "tenant_plugin should be 0 after delete";
    }
}
