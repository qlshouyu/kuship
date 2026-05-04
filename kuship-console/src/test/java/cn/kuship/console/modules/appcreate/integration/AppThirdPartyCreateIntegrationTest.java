package cn.kuship.console.modules.appcreate.integration;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 第三方组件创建：不调 region API，本机可直跑；验证 service_source / tenant_service 写入。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppThirdPartyCreateIntegrationTest {

    private static final int USER_ID = 909080;
    private static final String NICK = "kuship-thirdparty-admin";
    private static final String ENT = "kuship-test-ent-thirdparty";
    private static final String TEAM = "kuship-test-team-thirdparty";
    private static final String TEAM_ID = "9090808080808080tp1234567890123";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'tp-ent', 'TPTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "tp-admin@kuship.local", NICK,
                encoder.encode("tp-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'TPTeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM, USER_ID, TEAM, ENT);
    }

    @AfterAll
    void cleanup() {
        // 清理可能创建的 service / source 数据
        jdbc.update("DELETE FROM service_source WHERE team_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM tenant_service WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM service_group_relation WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "tp-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void createThirdPartyComponent_writesAllTables() throws Exception {
        MvcResult res = mvc.perform(post("/console/teams/" + TEAM + "/apps/third_party")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service_cname\":\"external-mysql\","
                                + "\"region_name\":\"r1\","
                                + "\"endpoints\":[{\"address\":\"172.20.0.99\",\"port\":3306}],"
                                + "\"kind\":\"static\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.service_cname").value("external-mysql"))
                .andExpect(jsonPath("$.data.bean.service_source").value("third_party"))
                .andExpect(jsonPath("$.data.bean.create_status").value("complete"))
                .andReturn();
        String body = res.getResponse().getContentAsString();
        String serviceId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).path("data").path("bean").path("service_id").asText();

        // 验证 tenant_service 写入
        Integer countTs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_service WHERE service_id = ?", Integer.class, serviceId);
        assert countTs != null && countTs == 1 : "tenant_service should have 1 row";

        // 验证 service_source 写入（含 endpoints JSON）
        String extendInfo = jdbc.queryForObject(
                "SELECT extend_info FROM service_source WHERE service_id = ?", String.class, serviceId);
        assert extendInfo != null && extendInfo.contains("172.20.0.99") :
                "service_source.extend_info should contain endpoints JSON, got: " + extendInfo;

        // 验证 service_origin = third_party（不调 region）
        String origin = jdbc.queryForObject(
                "SELECT service_origin FROM tenant_service WHERE service_id = ?", String.class, serviceId);
        assert "third_party".equals(origin) : "service_origin should be third_party, got: " + origin;
    }
}
