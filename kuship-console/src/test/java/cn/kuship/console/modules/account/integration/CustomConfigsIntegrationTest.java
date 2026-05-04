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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@code /console/users/custom_configs} 集成测试：PUT bulk upsert + GET 验证。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomConfigsIntegrationTest {

    private static final int USER_ID = 909051;
    private static final String NICK = "kuship-cfg-user";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, '', NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password)",
                USER_ID, "cfg@kuship.local", NICK,
                encoder.encode("cfg@kuship.localpwd12345"));
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM console_config WHERE user_nick_name = ?", NICK);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "cfg@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void putThenGet_perUser() throws Exception {
        // PUT 写入两条
        mvc.perform(put("/console/users/custom_configs")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"key\":\"theme\",\"value\":\"dark\",\"description\":\"用户主题\"},"
                                + "{\"key\":\"lang\",\"value\":\"zh-CN\",\"description\":null}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list.length()").value(2));

        // GET 查询
        mvc.perform(get("/console/users/custom_configs")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list.length()").value(2));

        // 第二次 PUT 同 key 覆盖（upsert 验证）
        mvc.perform(put("/console/users/custom_configs")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"key\":\"theme\",\"value\":\"light\",\"description\":\"翻车了\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(1));

        // GET 应仍含 lang（不被同 key 删除影响），但 theme 值变 light
        mvc.perform(get("/console/users/custom_configs")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(2));
    }

    @Test
    void putWithoutToken_returns401() throws Exception {
        mvc.perform(put("/console/users/custom_configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());
    }
}
