package cn.kuship.console.modules.team.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.HelmOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.appmarket.helm.entity.HelmRepo;
import cn.kuship.console.modules.appmarket.helm.repository.HelmRepoRepository;
import cn.kuship.console.modules.team.entity.TeamHelmReleaseSource;
import cn.kuship.console.modules.team.repository.TeamHelmReleaseSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HelmReleaseService 业务规则单测：buildInstallBody / enrichReleaseList /
 * persistReleaseSource / normalizeYaml / resolveNamespace。
 */
@ExtendWith(MockitoExtension.class)
class HelmReleaseServiceTest {

    @Mock HelmOperations helmOps;
    @Mock TenantsRepository tenantsRepo;
    @Mock HelmRepoRepository helmRepoRepo;
    @Mock TeamHelmReleaseSourceRepository sourceRepo;

    @InjectMocks HelmReleaseService service;

    private Tenants tenant;

    @BeforeEach
    void setup() {
        tenant = new Tenants();
        tenant.setTenantName("default");
        tenant.setNamespace("ns-default");
    }

    // ---------- resolveNamespace ----------

    @Test
    void resolveNamespace_prefers_tenant_namespace() {
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(tenant));
        assertEquals("ns-default", service.resolveNamespace("default"));
    }

    @Test
    void resolveNamespace_falls_back_to_tenant_name() {
        tenant.setNamespace(null);
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(tenant));
        assertEquals("default", service.resolveNamespace("default"));
    }

    @Test
    void resolveNamespace_throws_404_when_team_missing() {
        when(tenantsRepo.findByTenantName("ghost")).thenReturn(Optional.empty());
        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> service.resolveNamespace("ghost"));
        assertEquals(404, ex.getCode());
        assertEquals("团队不存在", ex.getMsgShow());
    }

    // ---------- buildInstallBody ----------

    @Test
    void buildInstallBody_store_with_known_repo_converts_to_repo() {
        HelmRepo repo = new HelmRepo();
        repo.setRepoName("stable");
        repo.setRepoUrl("https://charts.example.com");
        repo.setUsername("u");
        repo.setPassword("p");
        when(helmRepoRepo.findByRepoName("stable")).thenReturn(Optional.of(repo));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("source_type", "store");
        raw.put("repo_name", "stable");
        raw.put("chart_name", "nginx");

        Map<String, Object> result = service.buildInstallBody(raw, "ns-1");

        assertEquals("repo", result.get("source_type"));
        assertEquals("https://charts.example.com", result.get("repo_url"));
        assertEquals("u", result.get("username"));
        assertEquals("p", result.get("password"));
        assertEquals("nginx", result.get("chart_name"));
        assertEquals("ns-1", result.get("namespace"));
        // raw 没被修改
        assertEquals("store", raw.get("source_type"));
    }

    @Test
    void buildInstallBody_store_without_repo_name_passes_through() {
        Map<String, Object> raw = Map.of("source_type", "store");
        Map<String, Object> result = service.buildInstallBody(raw, "ns-1");
        assertEquals("store", result.get("source_type"));
        verify(helmRepoRepo, never()).findByRepoName(anyString());
    }

    @Test
    void buildInstallBody_store_with_unknown_repo_passes_through() {
        when(helmRepoRepo.findByRepoName("missing")).thenReturn(Optional.empty());
        Map<String, Object> raw = Map.of("source_type", "store", "repo_name", "missing");
        Map<String, Object> result = service.buildInstallBody(raw, "ns-1");
        assertEquals("store", result.get("source_type"));
    }

    @Test
    void buildInstallBody_non_store_passes_through_untouched() {
        Map<String, Object> raw = Map.of("source_type", "upload", "chart_name", "x");
        Map<String, Object> result = service.buildInstallBody(raw, "ns-1");
        assertEquals("upload", result.get("source_type"));
        verify(helmRepoRepo, never()).findByRepoName(anyString());
    }

    @Test
    void buildInstallBody_default_source_type_treated_as_store() {
        when(helmRepoRepo.findByRepoName("stable")).thenReturn(Optional.empty());
        Map<String, Object> raw = Map.of("repo_name", "stable");
        Map<String, Object> result = service.buildInstallBody(raw, "ns-1");
        // 没改，因为 unknown repo → 透传
        assertFalse(result.containsKey("source_type"));
    }

    // ---------- enrichReleaseList ----------

    @Test
    void enrichReleaseList_merges_source_info_for_matching_releases() {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("list", List.of(
                Map.of("name", "nginx", "chart", "nginx", "chart_version", "1.0"),
                Map.of("name", "redis", "chart", "redis", "chart_version", "5.0")));

        TeamHelmReleaseSource nginxRecord = new TeamHelmReleaseSource();
        nginxRecord.setNamespace("ns-1");
        nginxRecord.setReleaseName("nginx");
        nginxRecord.setSourceType("store");
        nginxRecord.setRepoName("stable");
        nginxRecord.setRepoUrl("https://example.com");
        nginxRecord.setChartName("nginx");
        nginxRecord.setChartVersion("1.0");
        when(sourceRepo.findByRegionNameAndNamespaceAndReleaseNameIn(anyString(), anyString(), anyCollection()))
                .thenReturn(List.of(nginxRecord));

        Map<String, Object> result = service.enrichReleaseList(bean, "rainbond", "ns-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> releases = (List<Map<String, Object>>) result.get("list");
        Map<String, Object> nginxInfo = (Map<String, Object>) releases.get(0).get("source_info");
        Map<String, Object> redisInfo = (Map<String, Object>) releases.get(1).get("source_info");

        assertEquals("store", nginxInfo.get("source_type"));
        assertEquals("store_locked", nginxInfo.get("upgrade_mode"));
        assertEquals("stable", nginxInfo.get("repo_name"));

        // redis 没记录 → legacy
        assertEquals("legacy", redisInfo.get("source_type"));
        assertEquals("manual_select", redisInfo.get("upgrade_mode"));
        // chart_name fallback 自 release 自身
        assertEquals("redis", redisInfo.get("chart_name"));
    }

    @Test
    void enrichReleaseList_handles_empty_list() {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("list", List.of());
        Map<String, Object> result = service.enrichReleaseList(bean, "rainbond", "ns-1");
        assertTrue(((List<?>) result.get("list")).isEmpty());
        verify(sourceRepo, never()).findByRegionNameAndNamespaceAndReleaseNameIn(anyString(), anyString(), anyCollection());
    }

    @Test
    void enrichReleaseList_handles_null_bean() {
        Map<String, Object> result = service.enrichReleaseList(null, "rainbond", "ns-1");
        assertTrue(((List<?>) result.get("list")).isEmpty());
    }

    // ---------- enrichReleaseDetail ----------

    @Test
    void enrichReleaseDetail_overrides_values_when_local_yaml_present() {
        TeamHelmReleaseSource record = new TeamHelmReleaseSource();
        record.setSourceType("store");
        record.setValuesYaml("replicas: 3\n");
        when(sourceRepo.findByRegionNameAndNamespaceAndReleaseName("rainbond", "ns-1", "nginx"))
                .thenReturn(Optional.of(record));

        Map<String, Object> bean = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("namespace", "ns-1");
        summary.put("values", "replicas: 1\n");
        bean.put("summary", summary);

        Map<String, Object> result = service.enrichReleaseDetail(bean, "rainbond", "ns-1", "nginx");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultSummary = (Map<String, Object>) result.get("summary");
        assertEquals("replicas: 3\n", resultSummary.get("values"));
        assertEquals("store", ((Map<?, ?>) resultSummary.get("source_info")).get("source_type"));
    }

    @Test
    void enrichReleaseDetail_keeps_remote_values_when_no_local_record() {
        when(sourceRepo.findByRegionNameAndNamespaceAndReleaseName(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        Map<String, Object> bean = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("namespace", "ns-1");
        summary.put("values", "remote-values\n");
        bean.put("summary", summary);

        Map<String, Object> result = service.enrichReleaseDetail(bean, "rainbond", "ns-1", "nginx");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultSummary = (Map<String, Object>) result.get("summary");
        assertEquals("remote-values\n", resultSummary.get("values"));
        assertEquals("legacy", ((Map<?, ?>) resultSummary.get("source_info")).get("source_type"));
    }

    @Test
    void enrichReleaseDetail_handles_missing_summary() {
        when(sourceRepo.findByRegionNameAndNamespaceAndReleaseName(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        Map<String, Object> result = service.enrichReleaseDetail(Map.of(), "rainbond", "ns-1", "nginx");
        assertTrue(result.containsKey("summary"));
    }

    // ---------- persistReleaseSource ----------

    @Test
    void persistReleaseSource_keeps_original_source_type_after_store_to_repo_conversion() {
        when(sourceRepo.findByRegionNameAndNamespaceAndReleaseName(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        // raw_body 是用户原始（store），install_body 是转换后（repo）
        Map<String, Object> raw = Map.of("source_type", "store", "repo_name", "stable",
                "release_name", "nginx", "chart_name", "nginx", "version", "1.0");
        Map<String, Object> install = Map.of("source_type", "repo", "repo_name", "stable",
                "release_name", "nginx", "chart_name", "nginx", "version", "1.0",
                "repo_url", "https://example.com");
        Map<String, Object> response = Map.of("release_name", "nginx");

        service.persistReleaseSource(raw, install, response, "team-x", "rainbond", "ns-1", "alice");

        ArgumentCaptor<TeamHelmReleaseSource> captor = ArgumentCaptor.forClass(TeamHelmReleaseSource.class);
        verify(sourceRepo, times(1)).save(captor.capture());
        TeamHelmReleaseSource saved = captor.getValue();
        assertEquals("store", saved.getSourceType()); // 关键：保留原始
        assertEquals("nginx", saved.getReleaseName());
        assertEquals("ns-1", saved.getNamespace());
        assertEquals("alice", saved.getCreator());
        assertEquals("1.0", saved.getChartVersion());
    }

    @Test
    void persistReleaseSource_skips_when_release_name_missing() {
        Map<String, Object> raw = Map.of();
        Map<String, Object> install = Map.of();
        service.persistReleaseSource(raw, install, Map.of(), "team-x", "rainbond", "ns-1", "alice");
        verify(sourceRepo, never()).save(any());
    }

    @Test
    void persistReleaseSource_updates_existing_record() {
        TeamHelmReleaseSource existing = new TeamHelmReleaseSource();
        existing.setId(42);
        existing.setSourceType("repo");
        existing.setChartVersion("0.9");
        when(sourceRepo.findByRegionNameAndNamespaceAndReleaseName("rainbond", "ns-1", "nginx"))
                .thenReturn(Optional.of(existing));

        Map<String, Object> raw = Map.of("source_type", "store", "release_name", "nginx",
                "chart_name", "nginx", "version", "1.0");
        Map<String, Object> install = Map.of("release_name", "nginx");
        service.persistReleaseSource(raw, install, Map.of(), "team-x", "rainbond", "ns-1", "alice");

        ArgumentCaptor<TeamHelmReleaseSource> captor = ArgumentCaptor.forClass(TeamHelmReleaseSource.class);
        verify(sourceRepo, times(1)).save(captor.capture());
        TeamHelmReleaseSource saved = captor.getValue();
        assertEquals(42, saved.getId()); // 复用原 id
        assertEquals("store", saved.getSourceType()); // 被升级时改回 store
        assertEquals("1.0", saved.getChartVersion());
    }

    // ---------- normalizeYaml ----------

    @Test
    void normalizeYaml_returns_first_string_directly() {
        assertEquals("a: 1\n", service.normalizeYaml("a: 1\n"));
    }

    @Test
    void normalizeYaml_dumps_map_to_yaml() {
        String dumped = service.normalizeYaml(null, Map.of("replicas", 3));
        assertTrue(dumped.contains("replicas: 3"));
    }

    @Test
    void normalizeYaml_returns_empty_for_all_nulls() {
        assertEquals("", service.normalizeYaml(null, null));
    }

    @Test
    void normalizeYaml_skips_empty_string_then_uses_next() {
        assertEquals("kept", service.normalizeYaml("", "kept"));
    }
}
