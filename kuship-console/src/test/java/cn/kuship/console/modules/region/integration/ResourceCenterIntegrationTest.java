package cn.kuship.console.modules.region.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.api.ResourceCenterOperations;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * 资源中心端点集成测试。
 *
 * <p>使用 {@link MockitoBean ResourceCenterOperations} 跳过真实 region 调用，
 * 只验证 URL 路由 / 响应形状 / source 持久化。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResourceCenterIntegrationTest {

    private static final int USER_ID = 909091;
    private static final String NICK = "rc-test-user";
    private static final String ENT = "kuship-test-ent-rc";
    private static final String TEAM = "rc-test-team";
    private static final String TENANT_ID = "rc-test-tenant-0001";
    private static final String REGION = "rc-test-region";
    private static final String RELEASE_NAME = "rc-helm-test-release";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean
    ResourceCenterOperations ops;

    @BeforeAll
    void seed() {
        // enterprise
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'rc-ent', 'RcTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        // user
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "rc-admin@kuship.local", NICK,
                encoder.encode("rc-admin@kuship.localpwd"), ENT);
        // tenant/team
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'RcTeam') ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TENANT_ID, TEAM, USER_ID, TEAM, ENT);
        // region
        jdbc.update("INSERT INTO region_info (region_id, region_name, region_alias, url, wsurl, httpdomain, tcpdomain, "
                + "ssl_ca_cert, cert_file, key_file, enterprise_id, status, region_type, `desc`, create_time, scope) "
                + "VALUES (?, ?, 'RC Test Region', 'https://172.20.0.199:8443', 'ws://172.20.0.199:6060', 'http.test', 'tcp.test', "
                + "'test-ca', 'test-cert', 'test-key', ?, '1', '[]', '', NOW(), 'private') "
                + "ON DUPLICATE KEY UPDATE region_name=VALUES(region_name)",
                "rc-test-rid-001", REGION, ENT);
        // cleanup any leftover source
        jdbc.update("DELETE FROM team_helm_release_source WHERE release_name = ?", RELEASE_NAME);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM team_helm_release_source WHERE release_name = ?", RELEASE_NAME);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM region_info WHERE region_id = ?", "rc-test-rid-001");
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "rc-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    // ---- NS 资源端点 ----

    @Test
    void getNsResourceTypes_returns200WithBean() throws Exception {
        Mockito.when(ops.getNsResourceTypes(REGION, TEAM))
                .thenReturn(Map.of("types", List.of("Deployment", "Service")));

        mvc.perform(get("/console/teams/{team}/regions/{region}/ns-resource-types", TEAM, REGION)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.types").isArray());
    }

    @Test
    void getNsResources_returns200WithBean() throws Exception {
        Mockito.when(ops.getNsResources(Mockito.eq(REGION), Mockito.eq(TEAM), Mockito.any()))
                .thenReturn(Map.of("list", List.of()));

        mvc.perform(get("/console/teams/{team}/regions/{region}/ns-resources", TEAM, REGION)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ---- 团队组件列表 ----

    @Test
    void getComponents_returns200WithList() throws Exception {
        mvc.perform(get("/console/teams/{team}/regions/{region}/components", TEAM, REGION)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    // ---- Helm Release 端点 ----

    @org.junit.jupiter.api.Disabled("Leader 整合：删除 #5 自带 HelmReleaseController（与主工作树 helm-release HelmReleasesController URL 1:1 重叠），helm-release 集成测试由 modules/team 自有 HelmReleaseIntegrationTest 承担")
    @Test
    void listHelmReleases_returnsEnrichedList() throws Exception {
        Map<String, Object> fakeBean = new java.util.LinkedHashMap<>();
        fakeBean.put("list", List.of(Map.of("name", RELEASE_NAME, "namespace", TEAM)));
        Mockito.when(ops.getHelmReleases(Mockito.eq(REGION), Mockito.eq(TEAM), Mockito.any()))
                .thenReturn(fakeBean);

        mvc.perform(get("/console/teams/{team}/regions/{region}/helm/releases", TEAM, REGION)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.list[0].source_info").exists());
    }

    @org.junit.jupiter.api.Disabled("同上：helm-release 端点已由主工作树 HelmReleasesController 接管")
    @Test
    void installHelmRelease_persistsSource_and_returns200() throws Exception {
        Map<String, Object> respBean = Map.of("release_name", RELEASE_NAME);
        Mockito.when(ops.installHelmRelease(Mockito.eq(REGION), Mockito.eq(TEAM), Mockito.any()))
                .thenReturn(respBean);

        mvc.perform(post("/console/teams/{team}/regions/{region}/helm/releases", TEAM, REGION)
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"release_name\":\"" + RELEASE_NAME + "\",\"source_type\":\"repo\","
                                + "\"repo_name\":\"my-repo\",\"repo_url\":\"https://charts.example.com\","
                                + "\"chart_name\":\"nginx\",\"version\":\"1.2.3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // source 已写入 DB
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_helm_release_source WHERE release_name = ?",
                Integer.class, RELEASE_NAME);
        assert count != null && count > 0 : "source should be persisted, count=" + count;
    }

    @org.junit.jupiter.api.Disabled("同上：helm-release 端点已由主工作树 HelmReleasesController 接管")
    @Test
    void deleteHelmRelease_deletesSource_and_returns200() throws Exception {
        Mockito.doNothing().when(ops).uninstallHelmRelease(
                Mockito.eq(REGION), Mockito.eq(TEAM), Mockito.eq(RELEASE_NAME), Mockito.any());

        mvc.perform(delete("/console/teams/{team}/regions/{region}/helm/releases/{name}",
                        TEAM, REGION, RELEASE_NAME)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // source 已被删除
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_helm_release_source WHERE release_name = ?",
                Integer.class, RELEASE_NAME);
        assert count != null && count == 0 : "source should be deleted, count=" + count;
    }

    // ---- 资源中心端点 ----

    @Test
    void getResourceCenterEvents_returns200() throws Exception {
        Mockito.when(ops.getEvents(Mockito.eq(REGION), Mockito.eq(TEAM), Mockito.any()))
                .thenReturn(Map.of("list", List.of()));

        mvc.perform(get("/console/teams/{team}/regions/{region}/resource-center/events", TEAM, REGION)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ---- WsInfo 端点 ----

    @Test
    void getWsInfo_returns200WithEventWebsocketUrl() throws Exception {
        mvc.perform(get("/console/teams/{team}/regions/{region}/resource-center/ws-info", TEAM, REGION)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.event_websocket_url").exists())
                .andExpect(jsonPath("$.data.bean.namespace").exists())
                .andExpect(jsonPath("$.data.bean.tenant_name").value(TEAM));
    }
}
