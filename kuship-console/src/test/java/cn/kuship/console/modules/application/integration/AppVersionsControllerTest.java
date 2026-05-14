package cn.kuship.console.modules.application.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.api.ServiceOperations;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.application.api.BatchServiceOperations;
import cn.kuship.console.modules.application.api.LangVersionOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * build-versions 3 个新 controller 端到端集成测试 + 1 个 lang-version controller。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppVersionsControllerTest {

    private static final int USER_ID = 909905;
    private static final String NICK = "kuship-versions-admin";
    private static final String EMAIL = "versions-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-versions";
    private static final String TEAM = "kuship-versions-team";
    private static final String TEAM_ID = "9099050505050505versions89012xx";
    private static final String NAMESPACE = "ns-versions-team";
    private static final String REGION = "rainbond";
    private static final String ALIAS = "svc-v1";
    private static final String SERVICE_ID = "9099050505versions01id00000abcd";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean ServiceOperations serviceOps;
    @MockitoBean LangVersionOperations langOps;
    @MockitoBean BatchServiceOperations batchOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'versions-ent', 'VersionsTest', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'VersionsTeam') "
                + "ON DUPLICATE KEY UPDATE namespace=VALUES(namespace)",
                TEAM_ID, TEAM, USER_ID, NAMESPACE, ENT);

        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, container_gpu, "
                + "min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, creater, protocol, "
                + "total_memory, is_service, namespace, volume_type, port_type, service_origin, service_source, create_status, "
                + "tenant_service_group_id, open_webhooks, server_type, is_upgrate, build_upgrade, service_name, k8s_component_name, update_time, secret) "
                + "VALUES (?, ?, 'app', ?, '组件', ?, 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, 256, "
                + "'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'assistant', 'docker_run', 'complete', "
                + "0, 1, 'tcp', 0, 0, ?, ?, NOW(), 'sec') "
                + "ON DUPLICATE KEY UPDATE service_source=VALUES(service_source)",
                SERVICE_ID, TEAM_ID, ALIAS, REGION, USER_ID, TEAM,
                "kuship-" + ALIAS, "kuship-" + ALIAS);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM tenant_service WHERE service_id = ?", SERVICE_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, EMAIL, null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void listVersions_happy() throws Exception {
        when(serviceOps.getBuildVersions(eq(REGION), eq(TEAM), eq(ALIAS))).thenReturn(Map.of(
                "list", List.of(Map.of("build_version", "v1", "deploy_version", "v1"))));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/build-versions")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.list[0].build_version").value("v1"));
    }

    @Test
    void getVersion_happy() throws Exception {
        when(serviceOps.getBuildVersionById(eq(REGION), eq(TEAM), eq(ALIAS), eq("v1")))
                .thenReturn(Map.of("build_version", "v1", "kind", "build_from_image"));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/build-versions/v1")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.kind").value("build_from_image"));
    }

    @Test
    void updateVersion_passesPlanVersion() throws Exception {
        when(serviceOps.updateBuildVersion(eq(REGION), eq(TEAM), eq(ALIAS), eq("v1"), any()))
                .thenReturn(Map.of("ok", true));

        mvc.perform(put("/console/teams/" + TEAM + "/apps/" + ALIAS + "/build-versions/v1")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan_version\":\"v2\"}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(serviceOps).updateBuildVersion(eq(REGION), eq(TEAM), eq(ALIAS), eq("v1"), bodyCap.capture());
        assertTrue(bodyCap.getValue().containsKey("plan_version"));
    }

    @Test
    void deleteVersion_injectsOperator() throws Exception {
        mvc.perform(delete("/console/teams/" + TEAM + "/apps/" + ALIAS + "/build-versions/v1")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(serviceOps).deleteBuildVersion(eq(REGION), eq(TEAM), eq(ALIAS), eq("v1"), bodyCap.capture());
        assertTrue(bodyCap.getValue().containsKey("operator"));
    }

    @Test
    void deployVersion_singleService() throws Exception {
        when(serviceOps.getServiceDeployVersion(eq(REGION), eq(TEAM), eq(ALIAS)))
                .thenReturn(Map.of("deploy_version", "v3"));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/deploy-version")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.deploy_version").value("v3"));
    }

    @Test
    void sourceCheck_returnsCheckUuid() throws Exception {
        when(serviceOps.serviceSourceCheck(eq(REGION), eq(TEAM), any())).thenReturn(
                Map.of("check_uuid", "abc-123"));

        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + ALIAS + "/source-check")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source_type\":\"sourcecode\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.check_uuid").value("abc-123"));
    }

    @Test
    void buildStatus_query_passesPluginIdAndBuildVersion() throws Exception {
        when(serviceOps.getBuildStatus(eq(REGION), eq(TEAM), eq("p1"), eq("bv1")))
                .thenReturn(Map.of("status", "complete"));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/build-status?plugin_id=p1&build_version=bv1")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.status").value("complete"));
    }

    @Test
    void serviceNotFound_returns404() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/apps/no-such-svc/build-versions")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.msg_show").value("组件不存在"));
    }

    @Test
    void region5xx_passesThrough() throws Exception {
        when(serviceOps.getBuildVersions(eq(REGION), eq(TEAM), eq(ALIAS))).thenThrow(
                new RegionApiException("service",
                        "/v2/tenants/" + NAMESPACE + "/services/" + ALIAS + "/build-list", "GET",
                        503, 503, "region down", "集群不可用", Map.of(), null));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/build-versions")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.msg_show").value("集群不可用"));
    }

    @Test
    void batchDeployVersion_happy() throws Exception {
        when(serviceOps.getTeamServicesDeployVersion(eq(REGION), eq(TEAM), any())).thenReturn(
                Map.of(SERVICE_ID, "v5"));

        mvc.perform(post("/console/teams/" + TEAM + "/deploy-version")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service_ids\":[\"" + SERVICE_ID + "\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void batchDeployVersion_emptyServiceIds_returns400() throws Exception {
        mvc.perform(post("/console/teams/" + TEAM + "/deploy-version")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service_ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg_show").value("缺少 service_ids"));
    }

    @Test
    void langVersion_get_passesBuildStrategy() throws Exception {
        when(langOps.getLangVersion(eq(ENT), eq(REGION), eq("java"), eq("true"), eq("slug")))
                .thenReturn(Map.of("list", List.of(Map.of("version", "11"))));

        mvc.perform(get("/console/enterprise/" + ENT + "/regions/" + REGION
                        + "/lang-version?lang=java&show=true&build_strategy=slug")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.list[0].version").value("11"));
    }

    @Test
    void cnbFrameworks_defaultsToNodejs() throws Exception {
        when(langOps.getCnbFrameworks(eq(ENT), eq(REGION), eq("nodejs"))).thenReturn(
                Map.of("list", List.of("express")));

        mvc.perform(get("/console/enterprise/" + ENT + "/regions/" + REGION + "/cnb/frameworks")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        verify(langOps).getCnbFrameworks(ENT, REGION, "nodejs");
    }
}
