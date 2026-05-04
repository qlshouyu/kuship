package cn.kuship.console.modules.appmarket.market.integration;

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

/** rainbond_center_app POST + GET 列表 + Tag 绑定。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarketTemplateIntegrationTest {

    private static final int USER_ID = 909091;
    private static final String NICK = "kuship-mkt-admin";
    private static final String ENT = "kuship-test-ent-mkt";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'mkt-ent', 'MKTTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "mkt-admin@kuship.local", NICK,
                encoder.encode("mkt-admin@kuship.localpwd12345"), ENT);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM rainbond_center_app_tag_relation WHERE enterprise_id = ?", ENT);
        jdbc.update("DELETE FROM rainbond_center_app_tag WHERE enterprise_id = ?", ENT);
        jdbc.update("DELETE FROM rainbond_center_app WHERE enterprise_id = ?", ENT);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "mkt-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void appModelCrud_andTagBinding() throws Exception {
        // POST 创建模板
        MvcResult res = mvc.perform(post("/console/enterprise/" + ENT + "/app-models")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app_name\":\"mkt-test-app\",\"scope\":\"enterprise\",\"describe\":\"e2e\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.app_name").value("mkt-test-app"))
                .andReturn();
        String body = res.getResponse().getContentAsString();
        String appId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).path("data").path("bean").path("app_id").asText();

        // GET 列表
        mvc.perform(get("/console/enterprise/" + ENT + "/app-models?page=1&page_size=10")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 创建 Tag
        MvcResult tagRes = mvc.perform(post("/console/enterprise/" + ENT + "/app-models/tag")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"mkt-tag-e2e\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Integer tagId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(tagRes.getResponse().getContentAsString()).path("data").path("bean").path("tag_id").asInt();

        // 绑定
        mvc.perform(post("/console/enterprise/" + ENT + "/app-model/" + appId + "/tag")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag_id\":" + tagId + "}"))
                .andExpect(status().isOk());
        Integer relCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rainbond_center_app_tag_relation WHERE app_id = ? AND tag_id = ?",
                Integer.class, appId, tagId);
        assert relCount != null && relCount == 1 : "relation should be 1, got " + relCount;
    }
}
