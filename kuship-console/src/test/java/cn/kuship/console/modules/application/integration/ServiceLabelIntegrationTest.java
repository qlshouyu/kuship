package cn.kuship.console.modules.application.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.application.api.ServiceLabelOperations;
import cn.kuship.console.modules.application.entity.TenantServiceLabel;
import cn.kuship.console.modules.application.repository.TenantServiceLabelRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 4 endpoint × happy/error 集成测试。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceLabelIntegrationTest {

    private static final int USER_ID = 909905;
    private static final String NICK = "kuship-label-admin";
    private static final String EMAIL = "label-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-label";
    private static final String TEAM = "kuship-label-team";
    private static final String TEAM_ID = "9099050505050505label78901234ab";
    private static final String NAMESPACE = "ns-label-team";
    private static final String REGION = "rainbond";
    private static final String ALIAS = "svc-label";
    private static final String SERVICE_ID = "9099050505label0001id00000000ab";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;
    @Autowired TenantServiceLabelRepository labelRepo;

    @MockitoBean ServiceLabelOperations labelOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'label-ent', 'LabelTest', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'LabelTeam') "
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
        jdbc.update("DELETE FROM service_labels WHERE service_id = ?", SERVICE_ID);
        jdbc.update("DELETE FROM tenant_service WHERE service_id = ?", SERVICE_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    @BeforeEach
    void clearLabels() {
        jdbc.update("DELETE FROM service_labels WHERE service_id = ?", SERVICE_ID);
    }

    @AfterEach
    void afterEach() {
        jdbc.update("DELETE FROM service_labels WHERE service_id = ?", SERVICE_ID);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, EMAIL, null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void listServiceLabels_returnsLocalRows() throws Exception {
        TenantServiceLabel l = new TenantServiceLabel();
        l.setTenantId(TEAM_ID);
        l.setServiceId(SERVICE_ID);
        l.setLabelId("l-gpu");
        l.setRegion(REGION);
        labelRepo.save(l);

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/labels")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].label_id").value("l-gpu"));

        verifyNoInteractions(labelOps);
    }

    @Test
    void addServiceLabels_writesLocalAndCallsRegion() throws Exception {
        when(labelOps.addServiceNodeLabel(eq(REGION), eq(TEAM), eq(ALIAS), any()))
                .thenReturn(Map.of("event_id", "e1"));

        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + ALIAS + "/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label_ids\":[\"l-gpu\",\"l-fast\"]}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        List<TenantServiceLabel> rows = labelRepo.findByServiceId(SERVICE_ID);
        assertEquals(2, rows.size());
        verify(labelOps).addServiceNodeLabel(eq(REGION), eq(TEAM), eq(ALIAS), any());
    }

    @Test
    void addServiceLabels_emptyList_returns400() throws Exception {
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + ALIAS + "/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label_ids\":[]}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(labelOps);
        assertTrue(labelRepo.findByServiceId(SERVICE_ID).isEmpty());
    }

    @Test
    void addServiceLabels_regionFails_rollsBackLocal() throws Exception {
        doThrow(new RegionApiException("service-label",
                "/v2/tenants/" + TEAM + "/services/" + ALIAS + "/label", "POST",
                500, 500, "region down", "集群故障", Map.of(), null))
                .when(labelOps).addServiceNodeLabel(eq(REGION), eq(TEAM), eq(ALIAS), any());

        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + ALIAS + "/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label_ids\":[\"l-rollback\"]}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().is5xxServerError());

        assertTrue(labelRepo.findByServiceId(SERVICE_ID).isEmpty(), "本地 INSERT 应该被事务回滚");
    }

    @Test
    void deleteServiceLabel_callsRegionThenDeletesLocal() throws Exception {
        TenantServiceLabel l = new TenantServiceLabel();
        l.setTenantId(TEAM_ID);
        l.setServiceId(SERVICE_ID);
        l.setLabelId("l-del");
        l.setRegion(REGION);
        labelRepo.save(l);

        when(labelOps.deleteServiceNodeLabel(eq(REGION), eq(TEAM), eq(ALIAS), any()))
                .thenReturn(Map.of());

        mvc.perform(delete("/console/teams/" + TEAM + "/apps/" + ALIAS + "/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label_id\":\"l-del\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        assertTrue(labelRepo.findByServiceIdAndLabelId(SERVICE_ID, "l-del").isEmpty());
    }

    @Test
    void deleteServiceLabel_region404_stillDeletesLocal() throws Exception {
        TenantServiceLabel l = new TenantServiceLabel();
        l.setTenantId(TEAM_ID);
        l.setServiceId(SERVICE_ID);
        l.setLabelId("l-stale");
        l.setRegion(REGION);
        labelRepo.save(l);

        doThrow(new RegionApiException("service-label",
                "/v2/tenants/" + TEAM + "/services/" + ALIAS + "/label", "DELETE",
                404, 404, "label not found", "标签不存在", Map.of(), null))
                .when(labelOps).deleteServiceNodeLabel(eq(REGION), eq(TEAM), eq(ALIAS), any());

        mvc.perform(delete("/console/teams/" + TEAM + "/apps/" + ALIAS + "/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label_id\":\"l-stale\"}")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        assertTrue(labelRepo.findByServiceIdAndLabelId(SERVICE_ID, "l-stale").isEmpty());
    }

    @Test
    void listAvailableLabels_returnsRegionList() throws Exception {
        when(labelOps.listRegionLabels(eq(REGION), eq(TEAM)))
                .thenReturn(List.of(Map.of("label_id", "l-gpu", "label_alias", "GPU")));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/labels/available")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].label_id").value("l-gpu"));
    }

    @Test
    void listAvailableLabels_regionFails_returnsEmpty() throws Exception {
        when(labelOps.listRegionLabels(eq(REGION), eq(TEAM)))
                .thenThrow(new RegionApiException("service-label",
                        "/v2/resources/labels", "GET",
                        500, 500, "down", "集群故障", Map.of(), null));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/labels/available")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(0));
    }
}
