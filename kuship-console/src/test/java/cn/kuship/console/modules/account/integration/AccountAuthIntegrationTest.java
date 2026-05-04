package cn.kuship.console.modules.account.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

/**
 * 账户模块集成测试：login → details / 错误密码 → 400 / register → token / enterprise/info 公开端点。
 *
 * <p>沿用 contract 测试模式：用 fixture user（高位 user_id 避免与真实数据冲突）；@AfterAll 清理。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountAuthIntegrationTest {

    /** 高位 user_id 避免与真实用户冲突。 */
    private static final int TEST_USER_ID = 909043;
    private static final String TEST_NICK = "kuship-test-alice";
    private static final String TEST_EMAIL = "kuship-test-alice@example.com";
    /** rainbond 算法：encode("kuship-test-alice@example.compassword12345") */
    private static String hashedPassword;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    cn.kuship.console.modules.account.password.LegacyPasswordEncoder encoder;
    @Autowired
    cn.kuship.console.common.security.JwtTokenService tokenService;

    @BeforeAll
    void seed() {
        hashedPassword = encoder.encode(TEST_EMAIL + "password12345");
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, '', NOW()) "
                + "ON DUPLICATE KEY UPDATE password=VALUES(password), is_active=1",
                TEST_USER_ID, TEST_EMAIL, TEST_NICK, hashedPassword);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", TEST_USER_ID);
    }

    @Test
    void login_thenDetails_endToEnd() throws Exception {
        // 登录
        MvcResult login = mvc.perform(post("/console/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nick_name\":\"" + TEST_NICK + "\",\"password\":\"password12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.token").exists())
                .andExpect(jsonPath("$.data.bean.user.nick_name").value(TEST_NICK))
                .andReturn();

        JsonNode body = json.readTree(login.getResponse().getContentAsString());
        String token = body.path("data").path("bean").path("token").asText();

        // 用 token 调 details
        mvc.perform(get("/console/users/details").header("Authorization", "GRJWT " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.user_id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.data.bean.nick_name").value(TEST_NICK))
                .andExpect(jsonPath("$.data.bean.email").value(TEST_EMAIL));
    }

    @Test
    void login_wrongPassword_returns400_inBody() throws Exception {
        mvc.perform(post("/console/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nick_name\":\"" + TEST_NICK + "\",\"password\":\"wrongpasswd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg_show").value("用户名或密码错误"));
    }

    @Test
    void enterpriseInfo_isPublic() throws Exception {
        // 不带 Authorization
        mvc.perform(get("/console/enterprise/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void perms_isPublic() throws Exception {
        mvc.perform(get("/console/perms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void detailsWithoutToken_returns401() throws Exception {
        mvc.perform(get("/console/users/details"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg_show").value("未认证或 token 失效"));
    }

    @Test
    void detailsWithStaleUserId_returns401() throws Exception {
        String token = tokenService.encode(new cn.kuship.console.common.security.JwtClaims(
                        99999999L, "ghost", "ghost@nowhere.com", null, null, java.util.Map.of()),
                java.time.Duration.ofMinutes(5));
        mvc.perform(get("/console/users/details").header("Authorization", "GRJWT " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("user not found"));
    }
}
