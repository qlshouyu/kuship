package cn.kuship.console.modules.region.resource.service;

import cn.kuship.console.modules.team.entity.TeamHelmReleaseSource;
import cn.kuship.console.modules.team.repository.TeamHelmReleaseSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HelmReleaseSourceService 单元测试（不需 DB，使用 Mockito mock 仓库）。
 */
class HelmReleaseSourceServiceTest {

    private TeamHelmReleaseSourceRepository repo;
    private HelmReleaseSourceService service;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(TeamHelmReleaseSourceRepository.class);
        service = new HelmReleaseSourceService(repo);
    }

    // ---- saveOrUpdate ----

    @Test
    void saveOrUpdate_whenNotExists_createsNewEntity() {
        Mockito.when(repo.findByRegionNameAndNamespaceAndReleaseName("r1", "ns1", "rel1"))
                .thenReturn(Optional.empty());
        Mockito.when(repo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveOrUpdate("team1", "r1", "ns1", "rel1", "repo",
                "my-repo", "https://charts.example.com",
                "nginx", "1.0.0", "key: value", "admin");

        ArgumentCaptor<TeamHelmReleaseSource> captor = ArgumentCaptor.forClass(TeamHelmReleaseSource.class);
        Mockito.verify(repo).save(captor.capture());
        TeamHelmReleaseSource saved = captor.getValue();
        assertThat(saved.getReleaseName()).isEqualTo("rel1");
        assertThat(saved.getSourceType()).isEqualTo("repo");
        assertThat(saved.getRepoName()).isEqualTo("my-repo");
    }

    @Test
    void saveOrUpdate_whenExists_updatesEntity() {
        TeamHelmReleaseSource existing = new TeamHelmReleaseSource();
        existing.setId(99);
        existing.setReleaseName("rel2");
        existing.setSourceType("legacy");
        Mockito.when(repo.findByRegionNameAndNamespaceAndReleaseName("r1", "ns1", "rel2"))
                .thenReturn(Optional.of(existing));
        Mockito.when(repo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveOrUpdate("team1", "r1", "ns1", "rel2", "store",
                "new-repo", "", "chart-x", "2.0.0", "", "user2");

        assertThat(existing.getSourceType()).isEqualTo("store");
        assertThat(existing.getRepoName()).isEqualTo("new-repo");
    }

    @Test
    void saveOrUpdate_withBlankReleaseName_skips() {
        service.saveOrUpdate("team", "region", "ns", "", "repo",
                "", "", "", "", "", "");
        Mockito.verifyNoInteractions(repo);
    }

    // ---- getSourceInfo 缺失时返回 legacy 默认 ----

    @Test
    void getSourceInfo_whenNoRecord_returnsLegacyDefault() {
        Mockito.when(repo.findByRegionNameAndNamespaceAndReleaseName("r", "ns", "myrel"))
                .thenReturn(Optional.empty());

        Map<String, Object> info = service.getSourceInfo("r", "ns", "myrel", Map.of("chart", "nginx"));
        assertThat(info.get("source_type")).isEqualTo("legacy");
        assertThat(info.get("upgrade_mode")).isEqualTo("manual_select");
        assertThat(info.get("chart_name")).isEqualTo("nginx");
    }

    @Test
    void getSourceInfo_whenStoreRecord_returnsStoreLocked() {
        TeamHelmReleaseSource src = new TeamHelmReleaseSource();
        src.setSourceType("store");
        src.setRepoName("stable");
        src.setChartName("nginx");
        src.setChartVersion("1.0.0");
        Mockito.when(repo.findByRegionNameAndNamespaceAndReleaseName("r", "ns", "myrel"))
                .thenReturn(Optional.of(src));

        Map<String, Object> info = service.getSourceInfo("r", "ns", "myrel", Map.of());
        assertThat(info.get("upgrade_mode")).isEqualTo("store_locked");
        assertThat(info.get("source_type")).isEqualTo("store");
    }

    // ---- listSourceInfoByReleases 批量 map key 格式 ----

    @Test
    void listSourceInfoByReleases_keyFormat_is_namespace_slash_releaseName() {
        TeamHelmReleaseSource src = new TeamHelmReleaseSource();
        src.setNamespace("ns1");
        src.setReleaseName("rel1");
        src.setSourceType("repo");
        Mockito.when(repo.findByRegionNameAndNamespaceAndReleaseNameIn("r", "ns1", List.of("rel1")))
                .thenReturn(List.of(src));

        Map<String, Map<String, Object>> result = service.listSourceInfoByReleases("r", "ns1", List.of("rel1"));
        assertThat(result).containsKey("ns1/rel1");
        assertThat(result.get("ns1/rel1").get("source_type")).isEqualTo("repo");
    }

    @Test
    void listSourceInfoByReleases_emptyNames_returnsEmpty() {
        Map<String, Map<String, Object>> result = service.listSourceInfoByReleases("r", "ns", List.of());
        assertThat(result).isEmpty();
        Mockito.verifyNoInteractions(repo);
    }
}
