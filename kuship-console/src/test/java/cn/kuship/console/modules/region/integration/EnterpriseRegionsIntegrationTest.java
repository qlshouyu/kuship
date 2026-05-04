package cn.kuship.console.modules.region.integration;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EnterpriseRegions 集成测试：列表（公开数据）+ 添加（admin 权限）+ 删除（无 team 在用）。
 *
 * <p>fixture：高位 user_id + 不存在的 enterprise_id；测试结束清理。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnterpriseRegionsIntegrationTest {

    private static final int ADMIN_USER_ID = 909060;
    private static final String ENTERPRISE = "kuship-test-ent-region";
    private static final String REGION_NAME = "kuship-test-region-1";
    private static final String GOOD_TOKEN = """
            ca.pem: "fake-ca"
            client.pem: "fake-cert"
            client.key.pem: "fake-key"
            apiAddress: https://172.20.0.99:6443
            websocketAddress: wss://172.20.0.99:6060
            defaultDomainSuffix: kuship-test.local
            defaultTCPHost: 172.20.0.99
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'kuship-region-ent', '区域测试企业', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENTERPRISE);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password)",
                ADMIN_USER_ID, "region-admin@kuship.local", "kuship-region-admin",
                encoder.encode("region-admin@kuship.localpwd12345"), ENTERPRISE);
        jdbc.update("INSERT INTO enterprise_user_perm (user_id, enterprise_id, identity, token) "
                + "VALUES (?, ?, 'admin', ?) ON DUPLICATE KEY UPDATE identity='admin'",
                ADMIN_USER_ID, ENTERPRISE, "region-admin-token-" + System.currentTimeMillis());
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM region_info WHERE region_name = ? OR enterprise_id = ?", REGION_NAME, ENTERPRISE);
        jdbc.update("DELETE FROM enterprise_user_perm WHERE user_id = ?", ADMIN_USER_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", ADMIN_USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENTERPRISE);
    }

    private String adminToken() {
        return tokenService.encode(
                new JwtClaims((long) ADMIN_USER_ID, "kuship-region-admin",
                        "region-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void crud_addListGetDelete() throws Exception {
        // 1. 添加
        var added = mvc.perform(post("/console/enterprise/" + ENTERPRISE + "/regions")
                        .header("Authorization", "GRJWT " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region_name\":\"" + REGION_NAME + "\","
                                + "\"region_alias\":\"测试集群\","
                                + "\"desc\":\"unit test\","
                                + "\"region_type\":[\"public\"],"
                                + "\"token\":" + tools.jackson.databind.json.JsonMapper.builder().build()
                                .writeValueAsString(GOOD_TOKEN) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.region_name").value(REGION_NAME))
                .andExpect(jsonPath("$.data.bean.status").value("1"))
                .andReturn();
        String regionId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(added.getResponse().getContentAsString())
                .path("data").path("bean").path("region_id").asText();

        // 2. 列表
        mvc.perform(get("/console/enterprise/" + ENTERPRISE + "/regions")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.region_name == '" + REGION_NAME + "')].region_alias").value("测试集群"));

        // 3. 详情
        mvc.perform(get("/console/enterprise/" + ENTERPRISE + "/regions/" + regionId)
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.region_id").value(regionId))
                // 默认脱敏，cert 字段为占位
                .andExpect(jsonPath("$.data.bean.cert_file").exists());

        // 4. 删除
        mvc.perform(delete("/console/enterprise/" + ENTERPRISE + "/regions/" + regionId)
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 5. 删除后再 GET 应 404
        mvc.perform(get("/console/enterprise/" + ENTERPRISE + "/regions/" + regionId)
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void addRegion_invalidToken_returns400() throws Exception {
        mvc.perform(post("/console/enterprise/" + ENTERPRISE + "/regions")
                        .header("Authorization", "GRJWT " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region_name\":\"bad-r\",\"region_alias\":\"x\","
                                + "\"region_type\":[],\"token\":\"ca.pem: only\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg_show").value("客户端密钥不存在"));
    }
}
