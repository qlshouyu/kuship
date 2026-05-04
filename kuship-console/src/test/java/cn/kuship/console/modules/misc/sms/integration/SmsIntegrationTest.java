package cn.kuship.console.modules.misc.sms.integration;

import cn.kuship.console.modules.misc.sms.repository.SmsVerificationCodeRepository;
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
 * Verifies the SMS verification flow under the LoggingSmsProvider (default for contract-test).
 * The end-to-end aliyun path requires real credentials and is not exercised here; unit tests
 * over {@code AliyunSmsProvider} (mocking the SDK Client) cover that surface.
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
class SmsIntegrationTest {

    private static final String PHONE = "13911223344";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SmsVerificationCodeRepository repo;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM sms_verification_code WHERE phone = ?", PHONE);
    }

    @Test
    void send_code_writes_db_row() throws Exception {
        mvc.perform(post("/console/sms/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"purpose\":\"login\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.sent").value(true))
                .andExpect(jsonPath("$.data.bean.provider").value("logging"));
        long count = repo.findByPhoneAndPurposeAndExpiresAtAfterOrderByCreatedAtDesc(
                PHONE, "login", java.time.LocalDateTime.now()).size();
        if (count < 1) {
            throw new AssertionError("expected at least one sms_verification_code row");
        }
    }

    @Test
    void send_code_invalid_phone_400() throws Exception {
        mvc.perform(post("/console/sms/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"not-a-phone\",\"purpose\":\"login\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void verify_code_locked_after_5_failures() throws Exception {
        // Seed a real code so subsequent verify(wrong) attempts hit the failure-counter path.
        mvc.perform(post("/console/sms/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"purpose\":\"login\"}"))
                .andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/console/users/login-by-phone")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + PHONE + "\",\"code\":\"000000\"}"))
                    .andExpect(jsonPath("$.code").value(401));
        }
        // 6th attempt should be locked (429) before hitting DB
        mvc.perform(post("/console/users/login-by-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"code\":\"000000\"}"))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.msg_show").value("验证码已锁定，请 5 分钟后重试"));
    }
}
