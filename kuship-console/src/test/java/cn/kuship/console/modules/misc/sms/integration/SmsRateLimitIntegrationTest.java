package cn.kuship.console.modules.misc.sms.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms the per-phone rate limiter blocks the second send within the window when
 * {@code kuship.sms.rate-limit.enabled} is flipped on. Default contract-test config has
 * limiting off so we override here only.
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy",
        "kuship.sms.rate-limit.enabled=true",
        "kuship.sms.rate-limit.window-seconds=60"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
class SmsRateLimitIntegrationTest {

    private static final String PHONE = "13911554477";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM sms_verification_code WHERE phone = ?", PHONE);
    }

    @Test
    void second_send_rate_limited() throws Exception {
        mvc.perform(post("/console/sms/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"purpose\":\"login\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/console/sms/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"purpose\":\"login\"}"))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.msg_show").value("发送过于频繁，请稍后再试"));
    }
}
