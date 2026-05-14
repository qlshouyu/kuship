package cn.kuship.console.modules.region.maven.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.region.maven.api.MavenSettingOperations;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MavenSettingIntegrationTest {

    private static final int ADMIN_USER_ID = 909107;
    private static final int NORMAL_USER_ID = 909108;
    private static final String NICK = "kuship-maven-admin";
    private static final String NORMAL_NICK = "kuship-maven-user";
    private static final String EMAIL = "maven-admin@kuship.local";
    private static final String NORMAL_EMAIL = "maven-user@kuship.local";
    private static final String ENT = "kuship-test-ent-maven";
    private static final String REGION = "rainbond";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean MavenSettingOperations mavenOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'maven-ent', 'MavenTest', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                ADMIN_USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=0, enterprise_id=VALUES(enterprise_id)",
                NORMAL_USER_ID, NORMAL_EMAIL, NORMAL_NICK, encoder.encode(NORMAL_EMAIL + "pwd12345"), ENT);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM user_info WHERE user_id IN (?, ?)", ADMIN_USER_ID, NORMAL_USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String adminToken() {
        return tokenService.encode(
                new JwtClaims((long) ADMIN_USER_ID, NICK, EMAIL, null, null, Map.of()),
                Duration.ofHours(1));
    }

    private String userToken() {
        return tokenService.encode(
                new JwtClaims((long) NORMAL_USER_ID, NORMAL_NICK, NORMAL_EMAIL, null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void list_onlyName_happy() throws Exception {
        when(mavenOps.listMavenSettings(eq(ENT), eq(REGION), eq(true)))
                .thenReturn(List.of(Map.of("name", "m1", "is_default", true)));

        mvc.perform(get("/console/enterprise/" + ENT + "/regions/" + REGION + "/mavensettings?onlyname=true")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].name").value("m1"));
    }

    @Test
    void list_full_when_onlyname_false() throws Exception {
        when(mavenOps.listMavenSettings(eq(ENT), eq(REGION), eq(false)))
                .thenReturn(List.of(Map.of("name", "m1", "is_default", true,
                        "content", "<settings>x</settings>")));

        mvc.perform(get("/console/enterprise/" + ENT + "/regions/" + REGION + "/mavensettings?onlyname=false")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].content").value("<settings>x</settings>"));
    }

    @Test
    void add_happy() throws Exception {
        when(mavenOps.addMavenSetting(eq(ENT), eq(REGION), any()))
                .thenReturn(Map.of("name", "m1", "is_default", false));

        mvc.perform(post("/console/enterprise/" + ENT + "/regions/" + REGION + "/mavensettings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"m1\",\"content\":\"<settings/>\"}")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.name").value("m1"));

        verify(mavenOps).addMavenSetting(eq(ENT), eq(REGION), any());
    }

    @Test
    void add_400_nameExists_passesThrough() throws Exception {
        doThrow(new RegionApiException("maven-setting", "/v2/cluster/builder/mavensetting", "POST",
                400, 400, "name exists", "配置名称已存在", Map.of(), null))
                .when(mavenOps).addMavenSetting(eq(ENT), eq(REGION), any());

        mvc.perform(post("/console/enterprise/" + ENT + "/regions/" + REGION + "/mavensettings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"m1\"}")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg_show").value("配置名称已存在"));
    }

    @Test
    void get_happy() throws Exception {
        when(mavenOps.getMavenSetting(eq(ENT), eq(REGION), eq("m1")))
                .thenReturn(Map.of("name", "m1", "content", "<settings/>"));

        mvc.perform(get("/console/enterprise/" + ENT + "/regions/" + REGION + "/mavensettings/m1")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.name").value("m1"));
    }

    @Test
    void update_happy() throws Exception {
        when(mavenOps.updateMavenSetting(eq(ENT), eq(REGION), eq("m1"), any()))
                .thenReturn(Map.of("name", "m1"));

        mvc.perform(put("/console/enterprise/" + ENT + "/regions/" + REGION + "/mavensettings/m1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"<new/>\"}")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void delete_happy() throws Exception {
        when(mavenOps.deleteMavenSetting(eq(ENT), eq(REGION), eq("m1")))
                .thenReturn(Map.of());

        mvc.perform(delete("/console/enterprise/" + ENT + "/regions/" + REGION + "/mavensettings/m1")
                        .header("Authorization", "GRJWT " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void normalUser_isForbidden() throws Exception {
        mvc.perform(get("/console/enterprise/" + ENT + "/regions/" + REGION + "/mavensettings")
                        .header("Authorization", "GRJWT " + userToken()))
                .andExpect(status().isForbidden());
    }
}
