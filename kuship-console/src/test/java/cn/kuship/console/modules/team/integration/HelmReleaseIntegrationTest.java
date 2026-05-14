package cn.kuship.console.modules.team.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.api.HelmOperations;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.team.entity.TeamHelmReleaseSource;
import cn.kuship.console.modules.team.repository.TeamHelmReleaseSourceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HelmReleasesController 9 个 HTTP 方法 + helm release 域端到端集成测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>task 1.4：TeamHelmReleaseSourceRepository 三组派生查询经 install/uninstall/list 路径间接验证</li>
 *   <li>task 4.9：9 个 HTTP 方法的路径匹配 / trailing slash / 路径变量 snake_case 解析</li>
 *   <li>task 6.1：列表→安装→详情→升级→卸载完整链路 + team_helm_release_source 表状态变化</li>
 * </ul>
 *
 * <p>依赖真实 MySQL（通过 ActiveProfiles=local）；HelmOperations 用 MockitoBean
 * 替换，无需真实 region 后端。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelmReleaseIntegrationTest {

    private static final int USER_ID = 909701;
    private static final String NICK = "kuship-helm-admin";
    private static final String EMAIL = "helm-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-helm";
    private static final String TEAM = "kuship-helm-team";
    private static final String TEAM_ID = "9097010101010101helm567890123456";
    private static final String NAMESPACE = "ns-helm-team";
    private static final String REGION = "r1";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;
    @Autowired TeamHelmReleaseSourceRepository sourceRepo;

    @MockitoBean HelmOperations helmOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'helm-ent', 'HelmTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'HelmTeam') "
                + "ON DUPLICATE KEY UPDATE namespace=VALUES(namespace)",
                TEAM_ID, TEAM, USER_ID, NAMESPACE, ENT);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM team_helm_release_source WHERE region_name = ? AND namespace = ?", REGION, NAMESPACE);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    @BeforeEach
    void resetTable() {
        jdbc.update("DELETE FROM team_helm_release_source WHERE region_name = ? AND namespace = ?", REGION, NAMESPACE);
    }

    @AfterEach
    void cleanupAfter() {
        jdbc.update("DELETE FROM team_helm_release_source WHERE region_name = ? AND namespace = ?", REGION, NAMESPACE);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, EMAIL, null, null, Map.of()),
                Duration.ofHours(1));
    }

    private String base() {
        return "/console/teams/" + TEAM + "/regions/" + REGION + "/helm";
    }

    // ---------- 1) GET /helm/releases ----------

    @Test
    void list_returns_releases_with_source_info_field() throws Exception {
        when(helmOps.getTenantHelmReleases(eq(REGION), eq(TEAM), eq(NAMESPACE)))
                .thenReturn(Map.of("list", List.of(
                        Map.of("name", "nginx", "chart", "nginx", "chart_version", "1.0", "namespace", NAMESPACE),
                        Map.of("name", "redis", "chart", "redis", "chart_version", "5.0", "namespace", NAMESPACE))));

        mvc.perform(get(base() + "/releases").header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.list.length()").value(2))
                .andExpect(jsonPath("$.data.bean.list[0].source_info.source_type").value("legacy"))
                .andExpect(jsonPath("$.data.bean.list[0].source_info.upgrade_mode").value("manual_select"));
    }

    @Test
    void list_trailing_slash_matches_same_handler() throws Exception {
        when(helmOps.getTenantHelmReleases(any(), any(), any())).thenReturn(Map.of("list", List.of()));
        mvc.perform(get(base() + "/releases/").header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());
    }

    @Test
    void list_unknown_team_returns_404() throws Exception {
        mvc.perform(get("/console/teams/ghost-team/regions/" + REGION + "/helm/releases")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().is(404))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---------- 2) POST /helm/releases （安装 + 落库 + 保留原始 source_type）----------

    @Test
    void install_persists_team_helm_release_source_with_original_source_type() throws Exception {
        when(helmOps.installTenantHelmRelease(eq(REGION), eq(TEAM), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(Map.of("release_name", "nginx", "status", "deployed"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source_type", "store");
        body.put("repo_name", "stable");
        body.put("chart_name", "nginx");
        body.put("release_name", "nginx");
        body.put("version", "1.0");

        mvc.perform(post(base() + "/releases")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_type":"store","repo_name":"stable","chart_name":"nginx","release_name":"nginx","version":"1.0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.release_name").value("nginx"));

        Optional<TeamHelmReleaseSource> stored = sourceRepo
                .findByRegionNameAndNamespaceAndReleaseName(REGION, NAMESPACE, "nginx");
        assertTrue(stored.isPresent(), "row must be persisted after install");
        assertEquals("store", stored.get().getSourceType()); // 关键：保留原始
        assertEquals("nginx", stored.get().getChartName());
        assertEquals("1.0", stored.get().getChartVersion());
        assertEquals(NICK, stored.get().getCreator());
    }

    // ---------- 3) POST /helm/chart-preview ----------

    @Test
    void preview_returns_passthrough_response() throws Exception {
        when(helmOps.previewTenantHelmChart(eq(REGION), eq(TEAM), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(Map.of("rendered", "apiVersion: v1"));

        mvc.perform(post(base() + "/chart-preview")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chart_name\":\"nginx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.rendered").value("apiVersion: v1"));
    }

    // ---------- 4) GET /helm/releases/{release_name} （详情 + values_yaml 覆盖）----------

    @Test
    void detail_overrides_values_with_local_yaml() throws Exception {
        TeamHelmReleaseSource record = new TeamHelmReleaseSource();
        record.setTeamName(TEAM);
        record.setRegionName(REGION);
        record.setNamespace(NAMESPACE);
        record.setReleaseName("nginx");
        record.setSourceType("store");
        record.setRepoName("stable");
        record.setChartName("nginx");
        record.setChartVersion("1.0");
        record.setValuesYaml("replicas: 3\n");
        record.setCreator(NICK);
        record.setCreateTime(java.time.LocalDateTime.now());
        record.setUpdateTime(java.time.LocalDateTime.now());
        sourceRepo.save(record);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("namespace", NAMESPACE);
        summary.put("values", "replicas: 1\n");
        when(helmOps.getTenantHelmReleaseDetail(eq(REGION), eq(TEAM), eq("nginx"), eq(NAMESPACE)))
                .thenReturn(Map.of("summary", summary));

        mvc.perform(get(base() + "/releases/nginx").header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.summary.values").value("replicas: 3\n"))
                .andExpect(jsonPath("$.data.bean.summary.source_info.source_type").value("store"))
                .andExpect(jsonPath("$.data.bean.summary.source_info.upgrade_mode").value("store_locked"));
    }

    // ---------- 5) PUT /helm/releases/{release_name} （升级 + save_or_update）----------

    @Test
    void upgrade_updates_existing_record_keeping_original_source_type() throws Exception {
        TeamHelmReleaseSource existing = new TeamHelmReleaseSource();
        existing.setTeamName(TEAM);
        existing.setRegionName(REGION);
        existing.setNamespace(NAMESPACE);
        existing.setReleaseName("nginx");
        existing.setSourceType("store");
        existing.setChartName("nginx");
        existing.setChartVersion("1.0");
        existing.setCreateTime(java.time.LocalDateTime.now());
        existing.setUpdateTime(java.time.LocalDateTime.now());
        sourceRepo.save(existing);
        Integer existingId = existing.getId();

        when(helmOps.upgradeTenantHelmRelease(eq(REGION), eq(TEAM), eq("nginx"),
                ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(Map.of("status", "deployed"));

        mvc.perform(put(base() + "/releases/nginx")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source_type\":\"store\",\"chart_name\":\"nginx\",\"version\":\"2.0\"}"))
                .andExpect(status().isOk());

        Optional<TeamHelmReleaseSource> after = sourceRepo
                .findByRegionNameAndNamespaceAndReleaseName(REGION, NAMESPACE, "nginx");
        assertTrue(after.isPresent());
        assertEquals(existingId, after.get().getId(), "should reuse same row id");
        assertEquals("2.0", after.get().getChartVersion());
        assertEquals("store", after.get().getSourceType());
    }

    // ---------- 6) DELETE /helm/releases/{release_name} ----------

    @Test
    void uninstall_deletes_record_after_region_call_succeeds() throws Exception {
        TeamHelmReleaseSource existing = new TeamHelmReleaseSource();
        existing.setTeamName(TEAM);
        existing.setRegionName(REGION);
        existing.setNamespace(NAMESPACE);
        existing.setReleaseName("nginx");
        existing.setSourceType("store");
        existing.setCreateTime(java.time.LocalDateTime.now());
        existing.setUpdateTime(java.time.LocalDateTime.now());
        sourceRepo.save(existing);

        doNothing().when(helmOps).uninstallTenantHelmRelease(eq(REGION), eq(TEAM), eq("nginx"), eq(NAMESPACE));

        mvc.perform(delete(base() + "/releases/nginx").header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk());

        verify(helmOps).uninstallTenantHelmRelease(REGION, TEAM, "nginx", NAMESPACE);
        assertFalse(sourceRepo
                .findByRegionNameAndNamespaceAndReleaseName(REGION, NAMESPACE, "nginx").isPresent());
    }

    // ---------- 7) GET /helm/releases/{release_name}/history ----------

    @Test
    void history_passthrough_response() throws Exception {
        when(helmOps.getTenantHelmReleaseHistory(eq(REGION), eq(TEAM), eq("nginx"), eq(NAMESPACE)))
                .thenReturn(Map.of("revisions", List.of(
                        Map.of("revision", 1), Map.of("revision", 2))));

        mvc.perform(get(base() + "/releases/nginx/history").header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.revisions.length()").value(2))
                .andExpect(jsonPath("$.data.bean.revisions[0].revision").value(1));
    }

    // ---------- 8) POST /helm/releases/{release_name}/rollback ----------

    @Test
    @SuppressWarnings("unchecked")
    void rollback_injects_namespace_when_missing_in_body() throws Exception {
        when(helmOps.rollbackTenantHelmRelease(eq(REGION), eq(TEAM), eq("nginx"),
                ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(Map.of("status", "ok"));

        mvc.perform(post(base() + "/releases/nginx/rollback")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1}"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Map<String, Object>> bodyCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(helmOps).rollbackTenantHelmRelease(eq(REGION), eq(TEAM), eq("nginx"), bodyCaptor.capture());
        assertEquals(NAMESPACE, bodyCaptor.getValue().get("namespace"));
        assertEquals(1, bodyCaptor.getValue().get("revision"));
    }
}
