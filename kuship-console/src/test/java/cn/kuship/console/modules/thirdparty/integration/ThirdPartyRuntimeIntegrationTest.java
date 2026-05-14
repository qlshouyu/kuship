package cn.kuship.console.modules.thirdparty.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.thirdparty.api.ThirdPartyServiceOperations;
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
import java.util.Map;

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
 * 第三方组件 endpoint + health 端到端集成测试。
 *
 * <p>策略：mock {@link ThirdPartyServiceOperations} 跳过 region 调用，但保留真实 DB +
 * 真实 ThirdPartyEndpointService 校验链（组件存在 + serviceSource=third_party）。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThirdPartyRuntimeIntegrationTest {

    private static final int USER_ID = 909901;
    private static final String NICK = "kuship-3rdparty-admin";
    private static final String EMAIL = "thirdparty-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-3rdparty";
    private static final String TEAM = "kuship-3rdparty-team";
    private static final String TEAM_ID = "9099010101010101third789012345xx";
    private static final String NAMESPACE = "ns-3rdparty-team";
    private static final String REGION = "rainbond";
    private static final String THIRD_PARTY_ALIAS = "third1";
    private static final String INTERNAL_ALIAS = "internal1";
    private static final String THIRD_PARTY_SERVICE_ID = "9099010101third0001id00000000abc";
    private static final String INTERNAL_SERVICE_ID = "9099010101internal0001id00000abc";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean ThirdPartyServiceOperations regionOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, '3rdparty-ent', '3rdPartyTest', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, '3rdPartyTeam') "
                + "ON DUPLICATE KEY UPDATE namespace=VALUES(namespace)",
                TEAM_ID, TEAM, USER_ID, NAMESPACE, ENT);

        // third-party service (服务源 third_party)
        seedService(THIRD_PARTY_SERVICE_ID, THIRD_PARTY_ALIAS, "third_party", "third_party");
        // internal service (服务源 docker_run)
        seedService(INTERNAL_SERVICE_ID, INTERNAL_ALIAS, "assistant", "docker_run");
    }

    private void seedService(String serviceId, String alias, String origin, String source) {
        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, container_gpu, "
                + "min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, creater, protocol, "
                + "total_memory, is_service, namespace, volume_type, port_type, service_origin, service_source, create_status, "
                + "tenant_service_group_id, open_webhooks, server_type, is_upgrate, build_upgrade, service_name, k8s_component_name, update_time, secret) "
                + "VALUES (?, ?, 'app', ?, '组件', ?, 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, 256, "
                + "'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', ?, ?, 'complete', "
                + "0, 1, 'tcp', 0, 0, "
                + "?, ?, NOW(), 'sec') "
                + "ON DUPLICATE KEY UPDATE service_source=VALUES(service_source)",
                serviceId, TEAM_ID, alias, REGION, USER_ID, TEAM, origin, source,
                "kuship-" + alias, "kuship-" + alias);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM tenant_service WHERE service_id IN (?, ?)",
                THIRD_PARTY_SERVICE_ID, INTERNAL_SERVICE_ID);
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
    void getEndpoints_happyPath() throws Exception {
        when(regionOps.getEndpoints(eq(REGION), eq(TEAM), eq(THIRD_PARTY_ALIAS))).thenReturn(Map.of(
                "endpoints", java.util.List.of(Map.of("address", "10.0.0.1:80"))));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + THIRD_PARTY_ALIAS + "/third_party/pods")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.endpoints[0].address").value("10.0.0.1:80"));

        verify(regionOps).getEndpoints(REGION, TEAM, THIRD_PARTY_ALIAS);
    }

    @Test
    void postEndpoints_singleEndpoint() throws Exception {
        when(regionOps.postEndpoints(eq(REGION), eq(TEAM), eq(THIRD_PARTY_ALIAS), any())).thenReturn(Map.of("ep_id", "new1"));

        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + THIRD_PARTY_ALIAS + "/third_party/pods")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"10.0.0.1:80\",\"is_online\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.ep_id").value("new1"));
    }

    @Test
    void postEndpoints_batch() throws Exception {
        when(regionOps.postEndpoints(eq(REGION), eq(TEAM), eq(THIRD_PARTY_ALIAS), any())).thenReturn(Map.of());

        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + THIRD_PARTY_ALIAS + "/third_party/pods")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoints\":[{\"address\":\"10.0.0.1:80\",\"is_online\":true},{\"address\":\"10.0.0.2:80\",\"is_online\":true}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void putEndpoints_updateOnlineStatus() throws Exception {
        when(regionOps.putEndpoints(eq(REGION), eq(TEAM), eq(THIRD_PARTY_ALIAS), any())).thenReturn(Map.of());

        mvc.perform(put("/console/teams/" + TEAM + "/apps/" + THIRD_PARTY_ALIAS + "/third_party/pods")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ep_id\":\"abc\",\"is_online\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteEndpoints_byEpId() throws Exception {
        when(regionOps.deleteEndpoints(eq(REGION), eq(TEAM), eq(THIRD_PARTY_ALIAS), any())).thenReturn(Map.of());

        mvc.perform(delete("/console/teams/" + TEAM + "/apps/" + THIRD_PARTY_ALIAS + "/third_party/pods")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ep_id\":\"abc\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void internalService_post_returns400() throws Exception {
        // 非 third_party serviceSource 应在 service 层抛 400，不发起 region 调用
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + INTERNAL_ALIAS + "/third_party/pods")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"10.0.0.1:80\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg_show").value("组件不是第三方组件"));
    }

    @Test
    void notExistAlias_returns404() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/apps/no-such-svc/third_party/pods")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg_show").value("组件不存在"));
    }

    @Test
    void getHealth_happyPath() throws Exception {
        when(regionOps.getHealth(eq(REGION), eq(TEAM), eq(THIRD_PARTY_ALIAS))).thenReturn(Map.of(
                "mode", "tcp", "port", 80, "period", 30, "timeout", 3));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + THIRD_PARTY_ALIAS + "/3rd-party/health")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.mode").value("tcp"))
                .andExpect(jsonPath("$.data.bean.port").value(80));
    }

    @Test
    void putHealth_setProbe() throws Exception {
        when(regionOps.putHealth(eq(REGION), eq(TEAM), eq(THIRD_PARTY_ALIAS), any())).thenReturn(Map.of());

        mvc.perform(put("/console/teams/" + TEAM + "/apps/" + THIRD_PARTY_ALIAS + "/3rd-party/health")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"tcp\",\"port\":80,\"period\":30,\"timeout\":3}"))
                .andExpect(status().isOk());
    }

    @Test
    void putHealth_internalService_returns400() throws Exception {
        mvc.perform(put("/console/teams/" + TEAM + "/apps/" + INTERNAL_ALIAS + "/3rd-party/health")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"tcp\",\"port\":80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg_show").value("组件不是第三方组件"));
    }

    @Test
    void region5xx_passesThrough() throws Exception {
        when(regionOps.getEndpoints(eq(REGION), eq(TEAM), eq(THIRD_PARTY_ALIAS))).thenThrow(
                new RegionApiException("third_party_service",
                        "/v2/tenants/" + NAMESPACE + "/services/" + THIRD_PARTY_ALIAS + "/endpoints",
                        "GET", 503, 503, "region down", "集群不可用", Map.of(), null));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + THIRD_PARTY_ALIAS + "/third_party/pods")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.msg_show").value("集群不可用"));
    }
}
