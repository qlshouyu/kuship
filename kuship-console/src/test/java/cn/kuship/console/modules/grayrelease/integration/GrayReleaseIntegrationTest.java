package cn.kuship.console.modules.grayrelease.integration;

import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.grayrelease.entity.GrayReleaseStatus;
import cn.kuship.console.modules.grayrelease.repository.GrayReleaseRecordRepository;
import org.junit.jupiter.api.AfterEach;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gray release 4 个 OpenAPI v1 端点 + 1 个 console 端点的集成测试。
 * <p>OpenAPI v1 用 X-Internal-Token 鉴权（绕过 PAT 表）；ApisixRoute 调用通过
 * {@code kuship.gray-release.skip-apisix-update=true} 跳过（无 live rainbond-go core）。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy",
        "kuship.openapi.internal-token=test-internal-gray-12345",
        "kuship.gray-release.skip-apisix-update=true"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrayReleaseIntegrationTest {

    private static final int USER_ID = 909501;
    private static final String NICK = "gr-admin";
    private static final String ENT = "kuship-test-ent-gr";
    private static final String TEAM_ID = "team-gr-test-1234567890abcdef";
    private static final String TEAM_NAME = "gr-team";
    private static final String REGION = "r1";
    private static final String INTERNAL_TOKEN = "test-internal-gray-12345";

    private Integer appId;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired LegacyPasswordEncoder encoder;
    @Autowired GrayReleaseRecordRepository repo;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'gr-ent', 'GRTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "gr-admin@kuship.local", NICK,
                encoder.encode("gr-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'GRTeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM_NAME, USER_ID, TEAM_NAME, ENT);
        jdbc.update("DELETE FROM gray_release_record WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM service_group WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("INSERT INTO service_group (tenant_id, group_name, region_name, governance_mode, is_default, order_index, app_type, app_store_name, app_store_url, app_template_name, version, logo, k8s_app, note, username, create_time, update_time) "
                + "VALUES (?, 'gr-app', ?, 'NO_GOVERNANCE', 0, 0, 'rainbond', '', '', '', '', '', 'gr-app', '', 'gr-admin', NOW(), NOW())",
                TEAM_ID, REGION);
        appId = jdbc.queryForObject(
                "SELECT ID FROM service_group WHERE tenant_id = ? AND group_name = 'gr-app'",
                Integer.class, TEAM_ID);
    }

    @AfterEach
    void cleanRecords() {
        jdbc.update("DELETE FROM gray_release_record WHERE tenant_id = ?", TEAM_ID);
    }

    private String openApiBase() {
        return "/openapi/v1/teams/" + TEAM_ID + "/regions/" + REGION + "/apps/" + appId;
    }

    @Test
    void create_gray_release_happy_path() throws Exception {
        mvc.perform(post(openApiBase() + "/gray-release")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_id\":\"tpl-1\",\"template_version\":\"1.0\","
                                + "\"domain_name\":\"app.example.com\",\"gray_ratio\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app_id").value(appId))
                .andExpect(jsonPath("$.gray_ratio").value(30))
                .andExpect(jsonPath("$.original_weight").value(70))
                .andExpect(jsonPath("$.new_weight").value(30))
                .andExpect(jsonPath("$.status").value("active"));

        long count = repo.findFirstByTenantIdAndAppIdAndStatus(TEAM_ID, appId, GrayReleaseStatus.ACTIVE)
                .map(r -> 1L).orElse(0L);
        if (count != 1L) throw new AssertionError("expected 1 active record, got " + count);
    }

    @Test
    void create_gray_release_invalid_ratio_400() throws Exception {
        mvc.perform(post(openApiBase() + "/gray-release")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_id\":\"tpl-1\",\"gray_ratio\":120}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_gray_release_active_exists_409() throws Exception {
        mvc.perform(post(openApiBase() + "/gray-release")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_id\":\"tpl-1\",\"gray_ratio\":20}"))
                .andExpect(status().isOk());
        mvc.perform(post(openApiBase() + "/gray-release")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_id\":\"tpl-2\",\"gray_ratio\":40}"))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void update_gray_ratio_happy_path() throws Exception {
        mvc.perform(post(openApiBase() + "/gray-release")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_id\":\"tpl-3\",\"gray_ratio\":30}"))
                .andExpect(status().isOk());

        mvc.perform(put(openApiBase() + "/gray-ratio")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_id\":\"tpl-3\",\"gray_ratio\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gray_ratio").value(60))
                .andExpect(jsonPath("$.original_weight").value(40))
                .andExpect(jsonPath("$.new_weight").value(60));
    }

    @Test
    void rollback_active_to_cancelled() throws Exception {
        mvc.perform(post(openApiBase() + "/gray-release")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_id\":\"tpl-4\",\"gray_ratio\":30}"))
                .andExpect(status().isOk());

        mvc.perform(post(openApiBase() + "/gray-rollback")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_id\":\"tpl-4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolled_back").value(true))
                .andExpect(jsonPath("$.record.status").value("cancelled"));
    }

    @Test
    void rollback_no_active_returns_false() throws Exception {
        mvc.perform(post(openApiBase() + "/gray-rollback")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolled_back").value(false));
    }

    @Test
    void list_gray_releases_filters_by_tenant() throws Exception {
        mvc.perform(post(openApiBase() + "/gray-release")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_id\":\"tpl-5\",\"gray_ratio\":50}"))
                .andExpect(status().isOk());

        mvc.perform(get("/openapi/v1/gray-releases?tenant_id=" + TEAM_ID + "&page=1&page_size=10")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list[0].tenant_id").value(TEAM_ID))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }
}
