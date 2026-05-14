package cn.kuship.console.modules.region.resource.service;

import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.team.entity.TeamHelmReleaseSource;
import cn.kuship.console.modules.team.repository.TeamHelmReleaseSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Helm Release 来源辅助记录 service。
 *
 * <p>对应 rainbond {@code views/team_resources.py} 中：
 * <ul>
 *   <li>{@code build_helm_release_source_info}</li>
 *   <li>{@code persist_helm_release_source}</li>
 *   <li>{@code enrich_helm_release_list}</li>
 *   <li>{@code enrich_helm_release_detail}</li>
 * </ul>
 */
@Service
public class HelmReleaseSourceService {

    private static final Logger log = LoggerFactory.getLogger(HelmReleaseSourceService.class);

    private final TeamHelmReleaseSourceRepository repo;

    public HelmReleaseSourceService(TeamHelmReleaseSourceRepository repo) {
        this.repo = repo;
    }

    /**
     * 保存或更新 helm release 来源记录（upsert）。
     * 对应 rainbond {@code helm_release_source_repo.save_or_update}。
     */
    @Transactional
    public void saveOrUpdate(String teamName, String regionName, String namespace,
                              String releaseName, String sourceType,
                              String repoName, String repoUrl,
                              String chartName, String chartVersion,
                              String valuesYaml, String creator) {
        if (releaseName == null || releaseName.isBlank()) {
            log.warn("[HelmReleaseSource] releaseName is blank, skip persist");
            return;
        }
        String st = (sourceType != null && !sourceType.isBlank()) ? sourceType.strip() : "store";
        try {
            Optional<TeamHelmReleaseSource> existing =
                    repo.findByRegionNameAndNamespaceAndReleaseName(regionName, namespace, releaseName);
            TeamHelmReleaseSource entity = existing.orElseGet(TeamHelmReleaseSource::new);
            entity.setTeamName(firstNonEmpty(teamName, ""));
            entity.setRegionName(regionName);
            entity.setNamespace(namespace);
            entity.setReleaseName(releaseName);
            entity.setSourceType(st);
            entity.setRepoName(firstNonEmpty(repoName, ""));
            entity.setRepoUrl(firstNonEmpty(repoUrl, ""));
            entity.setChartName(firstNonEmpty(chartName, ""));
            entity.setChartVersion(firstNonEmpty(chartVersion, ""));
            entity.setValuesYaml(firstNonEmpty(valuesYaml, ""));
            entity.setCreator(firstNonEmpty(creator, ""));
            repo.save(entity);
        } catch (DataIntegrityViolationException e) {
            // 并发冲突：再读一次然后 update
            log.warn("[HelmReleaseSource] upsert conflict, retrying find+update for release={}", releaseName);
            repo.findByRegionNameAndNamespaceAndReleaseName(regionName, namespace, releaseName)
                    .ifPresent(entity -> {
                        entity.setSourceType(st);
                        entity.setRepoName(firstNonEmpty(repoName, ""));
                        entity.setRepoUrl(firstNonEmpty(repoUrl, ""));
                        entity.setChartName(firstNonEmpty(chartName, ""));
                        entity.setChartVersion(firstNonEmpty(chartVersion, ""));
                        entity.setValuesYaml(firstNonEmpty(valuesYaml, ""));
                        entity.setCreator(firstNonEmpty(creator, ""));
                        repo.save(entity);
                    });
        }
    }

    /**
     * 构建单个 release 的 source_info map。
     * 对应 rainbond {@code build_helm_release_source_info(record, release)}。
     */
    public Map<String, Object> getSourceInfo(String regionName, String namespace, String releaseName,
                                              Map<String, Object> releaseBean) {
        Optional<TeamHelmReleaseSource> opt =
                repo.findByRegionNameAndNamespaceAndReleaseName(regionName, namespace, releaseName);
        return buildSourceInfo(opt.map(this::toMap).orElse(null), releaseBean);
    }

    /**
     * 批量查询 release source info，key = {@code namespace/releaseName}。
     * 对应 rainbond {@code helm_release_source_repo.list_by_releases}。
     */
    public Map<String, Map<String, Object>> listSourceInfoByReleases(String regionName, String namespace,
                                                                      List<String> releaseNames) {
        if (releaseNames == null || releaseNames.isEmpty()) return Map.of();
        List<String> names = releaseNames.stream().filter(n -> n != null && !n.isBlank()).toList();
        if (names.isEmpty()) return Map.of();
        List<TeamHelmReleaseSource> records =
                repo.findByRegionNameAndNamespaceAndReleaseNameIn(regionName, namespace, names);
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (TeamHelmReleaseSource r : records) {
            String key = r.getNamespace() + "/" + r.getReleaseName();
            result.put(key, toMap(r));
        }
        return result;
    }

    /**
     * 删除 release source 记录（卸载时调用）。
     * 对应 rainbond {@code helm_release_source_repo.delete_by_release}。
     */
    @Transactional
    public void deleteByRelease(String regionName, String namespace, String releaseName) {
        try {
            repo.deleteByRegionNameAndNamespaceAndReleaseName(regionName, namespace, releaseName);
        } catch (Exception e) {
            log.warn("[HelmReleaseSource] delete source failed for release={}: {}", releaseName, e.getMessage());
        }
    }

    /**
     * 把 list 里每个 release item 注入 source_info。
     * 对应 rainbond {@code enrich_helm_release_list}。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> enrichList(Map<String, Object> bean, String regionName, String namespace) {
        if (bean == null) return Map.of();
        Object listObj = bean.get("list");
        if (!(listObj instanceof List)) return bean;
        List<Map<String, Object>> releases = (List<Map<String, Object>>) (List) listObj;
        List<String> names = releases.stream()
                .map(item -> (String) item.get("name"))
                .filter(n -> n != null && !n.isBlank())
                .toList();
        Map<String, Map<String, Object>> sourceMap = listSourceInfoByReleases(regionName, namespace, names);
        for (Map<String, Object> item : releases) {
            String itemNs = (String) item.getOrDefault("namespace", namespace);
            String itemName = (String) item.get("name");
            String key = itemNs + "/" + itemName;
            Map<String, Object> record = sourceMap.get(key);
            item.put("source_info", buildSourceInfo(record, item));
        }
        return bean;
    }

    /**
     * 注入 detail 中 summary.source_info 和 values_yaml。
     * 对应 rainbond {@code enrich_helm_release_detail}。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> enrichDetail(Map<String, Object> bean, String regionName, String namespace,
                                             String releaseName) {
        if (bean == null) return Map.of();
        Object summaryObj = bean.get("summary");
        Map<String, Object> summary = (summaryObj instanceof Map) ? (Map<String, Object>) (Map) summaryObj : new LinkedHashMap<>();
        String itemNs = (String) summary.getOrDefault("namespace", namespace);
        Optional<TeamHelmReleaseSource> opt =
                repo.findByRegionNameAndNamespaceAndReleaseName(regionName, itemNs, releaseName);
        opt.ifPresent(record -> {
            if (record.getValuesYaml() != null && !record.getValuesYaml().isBlank()) {
                summary.put("values", record.getValuesYaml());
            }
        });
        summary.put("source_info", buildSourceInfo(opt.map(this::toMap).orElse(null), summary));
        bean.put("summary", summary);
        return bean;
    }

    /**
     * 解析 namespace：优先用 {@code Tenants.namespace}，缺失时用 team_name。
     * 对应 rainbond {@code get_team_resource_namespace}。
     */
    public static String resolveNamespace(String tenantName, TenantsRepository tenantsRepo) {
        if (tenantName == null || tenantName.isBlank()) return "";
        try {
            return tenantsRepo.findByTenantName(tenantName)
                    .map(Tenants::getNamespace)
                    .filter(ns -> ns != null && !ns.isBlank())
                    .orElse(tenantName);
        } catch (Exception e) {
            return tenantName;
        }
    }

    // ---- 私有工具 ----

    private Map<String, Object> buildSourceInfo(Map<String, Object> record, Map<String, Object> releaseBean) {
        Map<String, Object> r = record != null ? record : Map.of();
        Map<String, Object> rb = releaseBean != null ? releaseBean : Map.of();
        String sourceType = firstNonEmpty(
                (String) r.get("source_type"),
                "legacy"
        );
        String chartName = firstNonEmpty(
                (String) r.get("chart_name"),
                (String) rb.get("chart"),
                ""
        );
        String chartVersion = firstNonEmpty(
                (String) r.get("chart_version"),
                (String) rb.get("chart_version"),
                ""
        );
        String upgradeMode = "store".equals(sourceType) ? "store_locked" : "manual_select";
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("source_type", sourceType);
        info.put("repo_name", firstNonEmpty((String) r.get("repo_name"), ""));
        info.put("repo_url", firstNonEmpty((String) r.get("repo_url"), ""));
        info.put("chart_name", chartName);
        info.put("chart_version", chartVersion);
        info.put("upgrade_mode", upgradeMode);
        return info;
    }

    private Map<String, Object> toMap(TeamHelmReleaseSource e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_type", firstNonEmpty(e.getSourceType(), "legacy"));
        m.put("repo_name", firstNonEmpty(e.getRepoName(), ""));
        m.put("repo_url", firstNonEmpty(e.getRepoUrl(), ""));
        m.put("chart_name", firstNonEmpty(e.getChartName(), ""));
        m.put("chart_version", firstNonEmpty(e.getChartVersion(), ""));
        m.put("values_yaml", firstNonEmpty(e.getValuesYaml(), ""));
        return m;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }
}
