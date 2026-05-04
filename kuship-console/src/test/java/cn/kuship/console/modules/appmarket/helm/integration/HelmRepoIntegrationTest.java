package cn.kuship.console.modules.appmarket.helm.integration;

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

/** Helm Repo POST 验证：写本地 + GET 列表返回掩码 + 数据库列存的不是明文。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy",
        "kuship.helm.repo-password-key=test-helm-encryption-key-32bytes!"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelmRepoIntegrationTest {

    private static final int USER_ID = 909094;
    private static final String NICK = "kuship-helm-admin";
    private static final String ENT = "kuship-test-ent-helm";
    private static final String REPO_NAME = "kuship-helm-test-repo";
    private static final String REPO_PASSWORD = "secret-helm-password-12345";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'helm-ent', 'HelmTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "helm-admin@kuship.local", NICK,
                encoder.encode("helm-admin@kuship.localpwd12345"), ENT);
        jdbc.update("DELETE FROM helm_repo WHERE repo_name = ?", REPO_NAME);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM helm_repo WHERE repo_name = ?", REPO_NAME);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "helm-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void createRepo_passwordEncryptedAtRest_andMaskedInResponse() throws Exception {
        mvc.perform(post("/console/helm/repos")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + REPO_NAME + "\",\"url\":\"https://example.com/charts\","
                                + "\"username\":\"u\",\"password\":\"" + REPO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.password").value("***"));

        // 数据库中存的不能是明文
        String stored = jdbc.queryForObject(
                "SELECT password FROM helm_repo WHERE repo_name = ?", String.class, REPO_NAME);
        assert stored != null && !stored.equals(REPO_PASSWORD)
                : "password should be encrypted at rest, got: " + stored;
        assert stored.startsWith("AES:") : "expected AES: prefix, got: " + stored;

        // GET 列表 password 仍然是 *** 掩码
        mvc.perform(get("/console/helm/repos")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.name=='" + REPO_NAME + "')].password")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("***"))));
    }
}
