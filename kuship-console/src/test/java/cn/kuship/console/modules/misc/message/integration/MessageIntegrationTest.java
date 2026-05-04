package cn.kuship.console.modules.misc.message.integration;

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

/** 消息中心 round-trip：seed 一条 user_message → GET 列表 → PUT 标记已读 → 验证 is_read=1。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageIntegrationTest {

    private static final int USER_ID = 909301;
    private static final String NICK = "kuship-msg-admin";
    private static final String ENT = "kuship-test-ent-msg";
    private static final String TEAM = "kuship-test-team-msg";
    private static final String TEAM_ID = "9093010101010101ms1234567890123";
    private static final String MSG_ID = "msg9093010101test12345678901abcd";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'msg-ent', 'MSGTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "msg-admin@kuship.local", NICK,
                encoder.encode("msg-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'MSGTeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM, USER_ID, TEAM, ENT);
        jdbc.update("INSERT INTO user_message (message_id, receiver_id, content, is_read, create_time, update_time, msg_type, title, level) "
                + "VALUES (?, ?, ?, 0, NOW(), NOW(), ?, ?, ?)",
                MSG_ID, USER_ID, "测试系统消息内容", "warning", "测试标题", "important");
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM user_message WHERE message_id = ?", MSG_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "msg-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void list_then_markRead_roundtrip() throws Exception {
        // GET 未读列表
        mvc.perform(get("/console/teams/" + TEAM + "/message?is_read=false")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[?(@.message_id=='" + MSG_ID + "')].title")
                        .value(org.hamcrest.Matchers.hasItem("测试标题")))
                .andExpect(jsonPath("$.data.list[?(@.message_id=='" + MSG_ID + "')].is_read")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo(false))));

        // PUT 标记已读
        mvc.perform(put("/console/teams/" + TEAM + "/message")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message_ids\":[\"" + MSG_ID + "\"],\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.updated").value(1));

        // 验证数据库 is_read=1
        Integer isRead = jdbc.queryForObject(
                "SELECT is_read FROM user_message WHERE message_id = ?", Integer.class, MSG_ID);
        assert isRead != null && isRead == 1 : "is_read should be 1, got " + isRead;
    }
}
