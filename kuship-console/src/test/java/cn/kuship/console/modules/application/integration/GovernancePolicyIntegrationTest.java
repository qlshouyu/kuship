package cn.kuship.console.modules.application.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.application.governance.api.GovernanceModeOperations;
import cn.kuship.console.modules.application.k8sattr.api.K8sAttributeOperations;
import cn.kuship.console.modules.application.k8sattr.entity.ComponentK8sAttribute;
import cn.kuship.console.modules.application.k8sattr.repository.ComponentK8sAttributeRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

/** governance（6 endpoint）+ k8s_attribute（5 endpoint）集成测试。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GovernancePolicyIntegrationTest {

    private static final int USER_ID = 909201;
    private static final String NICK = "kuship-gov-admin";
    private static final String EMAIL = "gov-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-gov";
    private static final String TEAM = "kuship-gov-team";
    private static final String TEAM_ID = "9092010101010101gov78901234567ab";
    private static final String NAMESPACE = "ns-gov-team";
    private static final String REGION = "rainbond";
    private static final String ALIAS = "svc-gov";
    private static final String SERVICE_ID = "9092010101gov0000id000000000abc";
    private static final Integer GROUP_ID = 9099201;
    private static final String REGION_APP_ID = "rgnapp9092010101gov00000123";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;
    @Autowired ComponentK8sAttributeRepository attrRepo;

    @MockitoBean GovernanceModeOperations governanceOps;
    @MockitoBean K8sAttributeOperations attrOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'gov-ent', 'GovTest', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'GovTeam') "
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
        jdbc.update("INSERT INTO service_group (id, tenant_id, group_name, region_name, is_default, order_index, "
                + "create_time, update_time, app_type, k8s_app, governance_mode) "
                + "VALUES (?, ?, 'group-gov', ?, 1, 0, NOW(), NOW(), 'rainbond', 'k8s-gov', '') "
                + "ON DUPLICATE KEY UPDATE region_name=VALUES(region_name)",
                GROUP_ID, TEAM_ID, REGION);
        jdbc.update("INSERT INTO region_app (region_app_id, app_id, region_name) "
                + "VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE region_app_id=VALUES(region_app_id)",
                REGION_APP_ID, GROUP_ID, REGION);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM region_app WHERE app_id = ?", GROUP_ID);
        jdbc.update("DELETE FROM service_group WHERE id = ?", GROUP_ID);
        jdbc.update("DELETE FROM component_k8s_attributes WHERE component_id = ?", SERVICE_ID);
        jdbc.update("DELETE FROM tenant_service WHERE service_id = ?", SERVICE_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    @BeforeEach
    void clearAttrs() {
        jdbc.update("DELETE FROM component_k8s_attributes WHERE component_id = ?", SERVICE_ID);
    }

    @AfterEach
    void afterEach() {
        jdbc.update("DELETE FROM component_k8s_attributes WHERE component_id = ?", SERVICE_ID);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, EMAIL, null, null, Map.of()),
                Duration.ofHours(1));
    }

    // ===== Governance Mode =====

    @Test
    void listGovernanceMode_happy() throws Exception {
        when(governanceOps.listGovernanceMode(eq(REGION), eq(TEAM)))
                .thenReturn(List.of(Map.of("name", "istio"), Map.of("name", "kuma")));

        mvc.perform(get("/console/teams/" + TEAM + "/groups/" + GROUP_ID + "/governancemode/available")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(2));
    }

    @Test
    void updateGovernanceMode_writesLocalAndCallsRegion() throws Exception {
        when(governanceOps.createGovernanceCr(eq(REGION), eq(TEAM), eq(REGION_APP_ID), any()))
                .thenReturn(Map.of("kind", "DestinationRule"));

        mvc.perform(put("/console/teams/" + TEAM + "/groups/" + GROUP_ID + "/governancemode/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"governance_mode\":\"istio\",\"action\":\"create\",\"governance_cr\":{}}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.governance_mode").value("istio"));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_group WHERE id = ? AND governance_mode = ?",
                Integer.class, GROUP_ID, "istio");
        assertEquals(1, count);
    }

    @Test
    void updateGovernanceMode_missingMode_returns400() throws Exception {
        mvc.perform(put("/console/teams/" + TEAM + "/groups/" + GROUP_ID + "/governancemode/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void check_412_passesThrough() throws Exception {
        when(governanceOps.checkAppGovernanceMode(eq(REGION), eq(TEAM), eq(REGION_APP_ID), eq("istio")))
                .thenThrow(new RegionApiException("governance-mode",
                        "/v2/tenants/" + TEAM + "/apps/" + REGION_APP_ID + "/governance/check?governance_mode=istio",
                        "GET", 412, 412, "mesh not installed", "mesh 未安装", Map.of(), null));

        mvc.perform(get("/console/teams/" + TEAM + "/groups/" + GROUP_ID
                                + "/governancemode/check?governance_mode=istio")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value(412))
                .andExpect(jsonPath("$.msg_show").value("mesh 未安装"));
    }

    @Test
    void deleteGovernanceCr_callsRegion() throws Exception {
        when(governanceOps.deleteGovernanceCr(eq(REGION), eq(TEAM), eq(REGION_APP_ID)))
                .thenReturn(Map.of());

        mvc.perform(delete("/console/teams/" + TEAM + "/groups/" + GROUP_ID + "/governancemode-cr")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        verify(governanceOps).deleteGovernanceCr(REGION, TEAM, REGION_APP_ID);
    }

    // ===== K8s Attribute =====

    @Test
    void k8sAttribute_create_happyAndPersists() throws Exception {
        when(attrOps.createK8sAttribute(eq(REGION), eq(TEAM), eq(ALIAS), any()))
                .thenReturn(Map.of("name", "nodeSelector"));

        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + ALIAS + "/k8s-attributes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attribute\":{\"name\":\"nodeSelector\",\"save_type\":\"yaml\","
                                + "\"attribute_value\":\"key: gpu\"}}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.name").value("nodeSelector"));

        assertTrue(attrRepo.findByComponentIdAndName(SERVICE_ID, "nodeSelector").isPresent());
    }

    @Test
    void k8sAttribute_create_409_whenNameExists() throws Exception {
        ComponentK8sAttribute existing = new ComponentK8sAttribute();
        existing.setTenantId(TEAM_ID);
        existing.setComponentId(SERVICE_ID);
        existing.setName("nodeSelector");
        existing.setSaveType("yaml");
        existing.setAttributeValue("key: existing");
        attrRepo.save(existing);

        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + ALIAS + "/k8s-attributes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attribute\":{\"name\":\"nodeSelector\"}}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void k8sAttribute_list_returnsLocalRows() throws Exception {
        ComponentK8sAttribute a = new ComponentK8sAttribute();
        a.setTenantId(TEAM_ID);
        a.setComponentId(SERVICE_ID);
        a.setName("annot");
        a.setSaveType("json");
        a.setAttributeValue("{}");
        attrRepo.save(a);

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/k8s-attributes")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].name").value("annot"));
    }

    @Test
    void k8sAttribute_update_pathNameMustMatchBody() throws Exception {
        ComponentK8sAttribute a = new ComponentK8sAttribute();
        a.setTenantId(TEAM_ID);
        a.setComponentId(SERVICE_ID);
        a.setName("annot");
        a.setSaveType("yaml");
        a.setAttributeValue("v1");
        attrRepo.save(a);

        mvc.perform(put("/console/teams/" + TEAM + "/apps/" + ALIAS + "/k8s-attributes/annot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attribute\":{\"name\":\"different\"}}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void k8sAttribute_delete_callsRegionThenDeletesLocal() throws Exception {
        ComponentK8sAttribute a = new ComponentK8sAttribute();
        a.setTenantId(TEAM_ID);
        a.setComponentId(SERVICE_ID);
        a.setName("toDelete");
        a.setSaveType("yaml");
        a.setAttributeValue("v");
        attrRepo.save(a);
        when(attrOps.deleteK8sAttribute(eq(REGION), eq(TEAM), eq(ALIAS), any())).thenReturn(Map.of());

        mvc.perform(delete("/console/teams/" + TEAM + "/apps/" + ALIAS + "/k8s-attributes/toDelete")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        assertTrue(attrRepo.findByComponentIdAndName(SERVICE_ID, "toDelete").isEmpty());
    }

    @Test
    void k8sAttribute_delete_region404_stillRemovesLocal() throws Exception {
        ComponentK8sAttribute a = new ComponentK8sAttribute();
        a.setTenantId(TEAM_ID);
        a.setComponentId(SERVICE_ID);
        a.setName("stale");
        a.setSaveType("yaml");
        a.setAttributeValue("v");
        attrRepo.save(a);
        doThrow(new RegionApiException("k8s-attribute",
                "/v2/tenants/" + TEAM + "/services/" + ALIAS + "/k8s-attributes", "DELETE",
                404, 404, "not found", "属性不存在", Map.of(), null))
                .when(attrOps).deleteK8sAttribute(eq(REGION), eq(TEAM), eq(ALIAS), any());

        mvc.perform(delete("/console/teams/" + TEAM + "/apps/" + ALIAS + "/k8s-attributes/stale")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        assertTrue(attrRepo.findByComponentIdAndName(SERVICE_ID, "stale").isEmpty());
    }
}
