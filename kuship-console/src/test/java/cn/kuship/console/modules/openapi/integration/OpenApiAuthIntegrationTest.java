package cn.kuship.console.modules.openapi.integration;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** OpenAPI 认证 4 场景：X-Internal-Token 通过 / X-Internal-Token 错误 / PAT 不存在 / PAT 非 sys_admin。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy",
        "kuship.openapi.internal-token=test-internal-12345"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiAuthIntegrationTest {

    private static final int ADMIN_USER_ID = 909401;
    private static final int NORMAL_USER_ID = 909402;
    private static final String ENT = "kuship-test-ent-openapi";
    private static final String ADMIN_PAT = "test-pat-admin-1234567890abcdef";
    private static final String NORMAL_PAT = "test-pat-normal-1234567890abcde";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'oa-ent', 'OATest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                ADMIN_USER_ID, "oa-admin@kuship.local", "oa-admin",
                encoder.encode("oa-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=0",
                NORMAL_USER_ID, "oa-normal@kuship.local", "oa-normal",
                encoder.encode("oa-normal@kuship.localpwd12345"), ENT);
        jdbc.update("DELETE FROM user_access_key WHERE access_key IN (?, ?)", ADMIN_PAT, NORMAL_PAT);
        jdbc.update("INSERT INTO user_access_key (note, user_id, access_key, expire_time) VALUES (?, ?, ?, ?)",
                "openapi-admin", ADMIN_USER_ID, ADMIN_PAT, 99999999);
        jdbc.update("INSERT INTO user_access_key (note, user_id, access_key, expire_time) VALUES (?, ?, ?, ?)",
                "openapi-normal", NORMAL_USER_ID, NORMAL_PAT, 99999999);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM user_access_key WHERE access_key IN (?, ?)", ADMIN_PAT, NORMAL_PAT);
        jdbc.update("DELETE FROM user_info WHERE user_id IN (?, ?)", ADMIN_USER_ID, NORMAL_USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    @Test
    void internalToken_pass_returnsRegionListWithoutWrapper() throws Exception {
        mvc.perform(get("/openapi/v1/regions")
                        .header("X-Internal-Token", "test-internal-12345"))
                .andExpect(status().isOk())
                // 响应不包 console 风格的 {code, msg, data}
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.msg_show").doesNotExist())
                // 直接是数组（可能为空）
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void internalToken_wrongValue_returns401() throws Exception {
        mvc.perform(get("/openapi/v1/regions")
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid internal token"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void pat_invalid_returns401() throws Exception {
        mvc.perform(get("/openapi/v1/regions")
                        .header("Authorization", "this-token-does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid access token"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void pat_nonSysAdmin_returns403() throws Exception {
        mvc.perform(get("/openapi/v1/regions")
                        .header("Authorization", NORMAL_PAT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Permission denied: requires sys_admin"))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void pat_admin_passesAndReturnsAdminList() throws Exception {
        mvc.perform(get("/openapi/v1/administrators")
                        .header("Authorization", ADMIN_PAT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
