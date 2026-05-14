package cn.kuship.console.modules.team.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.HelmOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.appmarket.helm.entity.HelmRepo;
import cn.kuship.console.modules.appmarket.helm.repository.HelmRepoRepository;
import cn.kuship.console.modules.team.entity.TeamHelmReleaseSource;
import cn.kuship.console.modules.team.repository.TeamHelmReleaseSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * helm release 域业务装配。对齐 rainbond {@code console/views/team_resources.py:20-291}
 * 中的 5 个 helper + 5 个 view。
 */
@Service
public class HelmReleaseService {

    private static final Logger log = LoggerFactory.getLogger(HelmReleaseService.class);

    private final HelmOperations helmOps;
    private final TenantsRepository tenantsRepo;
    private final HelmRepoRepository helmRepoRepo;
    private final TeamHelmReleaseSourceRepository sourceRepo;

    public HelmReleaseService(HelmOperations helmOps,
                                TenantsRepository tenantsRepo,
                                HelmRepoRepository helmRepoRepo,
                                TeamHelmReleaseSourceRepository sourceRepo) {
        this.helmOps = helmOps;
        this.tenantsRepo = tenantsRepo;
        this.helmRepoRepo = helmRepoRepo;
        this.sourceRepo = sourceRepo;
    }

    // ---- public APIs（对齐 5 个 view）----

    public Map<String, Object> listReleases(String teamName, String regionName) {
        String namespace = resolveNamespace(teamName);
        Map<String, Object> bean = safeMap(helmOps.getTenantHelmReleases(regionName, teamName, namespace));
        return enrichReleaseList(bean, regionName, namespace);
    }

    public Map<String, Object> installRelease(String teamName, String regionName,
                                                Map<String, Object> rawBody, String creator) {
        String namespace = resolveNamespace(teamName);
        Map<String, Object> installBody = buildInstallBody(rawBody, namespace);
        Map<String, Object> response = safeMap(helmOps.installTenantHelmRelease(regionName, teamName, installBody));
        safelyPersistSource(rawBody, installBody, response, teamName, regionName, namespace, creator);
        return response;
    }

    public Map<String, Object> previewChart(String teamName, String regionName,
                                              Map<String, Object> rawBody) {
        String namespace = resolveNamespace(teamName);
        Map<String, Object> body = buildInstallBody(rawBody, namespace);
        return safeMap(helmOps.previewTenantHelmChart(regionName, teamName, body));
    }

    public Map<String, Object> getDetail(String teamName, String regionName, String releaseName) {
        String namespace = resolveNamespace(teamName);
        Map<String, Object> bean = safeMap(helmOps.getTenantHelmReleaseDetail(regionName, teamName, releaseName, namespace));
        return enrichReleaseDetail(bean, regionName, namespace, releaseName);
    }

    public Map<String, Object> upgradeRelease(String teamName, String regionName, String releaseName,
                                                Map<String, Object> rawBody, String creator) {
        String namespace = resolveNamespace(teamName);
        Map<String, Object> upgradeBody = buildInstallBody(rawBody, namespace);
        Map<String, Object> response = safeMap(helmOps.upgradeTenantHelmRelease(regionName, teamName, releaseName, upgradeBody));
        // upgrade 响应里不一定有 release_name，落库时显式带上
        Map<String, Object> responseForPersist = new LinkedHashMap<>(response);
        responseForPersist.putIfAbsent("release_name", releaseName);
        safelyPersistSource(rawBody, upgradeBody, responseForPersist, teamName, regionName, namespace, creator);
        return response;
    }

    public void uninstallRelease(String teamName, String regionName, String releaseName) {
        String namespace = resolveNamespace(teamName);
        helmOps.uninstallTenantHelmRelease(regionName, teamName, releaseName, namespace);
        try {
            sourceRepo.deleteByRegionNameAndNamespaceAndReleaseName(regionName, namespace, releaseName);
        } catch (Exception e) {
            log.error("delete helm release source failed: region={} ns={} release={}",
                    regionName, namespace, releaseName, e);
        }
    }

    public Map<String, Object> getHistory(String teamName, String regionName, String releaseName) {
        String namespace = resolveNamespace(teamName);
        return safeMap(helmOps.getTenantHelmReleaseHistory(regionName, teamName, releaseName, namespace));
    }

    public Map<String, Object> rollbackRelease(String teamName, String regionName, String releaseName,
                                                 Map<String, Object> rawBody) {
        String namespace = resolveNamespace(teamName);
        Map<String, Object> body = rawBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawBody);
        if (namespace != null && !namespace.isBlank() && isBlank(asString(body.get("namespace")))) {
            body.put("namespace", namespace);
        }
        return safeMap(helmOps.rollbackTenantHelmRelease(regionName, teamName, releaseName, body));
    }

    // ---- helpers（对齐 Python 端 5 个 helper）----

    /** 对齐 Python {@code get_team_resource_namespace}：tenant.namespace → tenant.tenant_name → 404。 */
    String resolveNamespace(String teamName) {
        Tenants tenant = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        if (tenant.getNamespace() != null && !tenant.getNamespace().isBlank()) {
            return tenant.getNamespace();
        }
        return tenant.getTenantName();
    }

    /**
     * 对齐 Python {@code build_helm_install_body}：当 source_type=store 且 repo_name 已知时，
     * 查 helm_repo 改写为 source_type=repo 并注入 repo_url/username/password。
     */
    Map<String, Object> buildInstallBody(Map<String, Object> rawBody, String namespace) {
        Map<String, Object> payload = rawBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawBody);
        if (namespace != null && !namespace.isBlank() && isBlank(asString(payload.get("namespace")))) {
            payload.put("namespace", namespace);
        }
        String sourceType = Optional.ofNullable(asString(payload.get("source_type"))).orElse("store");
        if (!"store".equals(sourceType)) {
            return payload;
        }
        String repoName = asString(payload.get("repo_name"));
        if (isBlank(repoName)) {
            return payload;
        }
        Optional<HelmRepo> repoOpt = helmRepoRepo.findByRepoName(repoName);
        if (repoOpt.isEmpty()) {
            return payload;
        }
        HelmRepo repo = repoOpt.get();
        String chartName = firstNonBlank(asString(payload.get("chart_name")), asString(payload.get("chart")));
        payload.put("source_type", "repo");
        payload.put("repo_url", firstNonBlank(asString(payload.get("repo_url")), repo.getRepoUrl()));
        if (!isBlank(chartName)) {
            payload.put("chart_name", chartName);
        }
        payload.put("username", firstNonBlank(asString(payload.get("username")), nullToEmpty(repo.getUsername())));
        payload.put("password", firstNonBlank(asString(payload.get("password")), nullToEmpty(repo.getPassword())));
        return payload;
    }

    /** 对齐 Python {@code enrich_helm_release_list}：列表内每个 release 注入 source_info 字段。 */
    Map<String, Object> enrichReleaseList(Map<String, Object> bean, String regionName, String namespace) {
        Map<String, Object> safe = bean == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bean);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> releases = (List<Map<String, Object>>) Optional.ofNullable(safe.get("list"))
                .filter(o -> o instanceof List)
                .orElse(List.of());
        if (releases.isEmpty()) {
            safe.put("list", new ArrayList<>(releases));
            return safe;
        }
        List<String> names = releases.stream()
                .map(item -> asString(item.get("name")))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        Map<String, TeamHelmReleaseSource> sourceMap = lookupSources(regionName, namespace, names);
        List<Map<String, Object>> enriched = new ArrayList<>(releases.size());
        for (Map<String, Object> item : releases) {
            Map<String, Object> mutable = new LinkedHashMap<>(item);
            String itemNs = firstNonBlank(asString(mutable.get("namespace")), namespace);
            String name = asString(mutable.get("name"));
            TeamHelmReleaseSource record = sourceMap.get(itemNs + "/" + name);
            mutable.put("source_info", buildSourceInfo(record, mutable));
            enriched.add(mutable);
        }
        safe.put("list", enriched);
        return safe;
    }

    /** 对齐 Python {@code enrich_helm_release_detail}：summary 注入 source_info；values_yaml 非空覆盖 summary.values。 */
    Map<String, Object> enrichReleaseDetail(Map<String, Object> bean, String regionName, String namespace, String releaseName) {
        Map<String, Object> safe = bean == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bean);
        @SuppressWarnings("unchecked")
        Map<String, Object> summarySrc = (Map<String, Object>) Optional.ofNullable(safe.get("summary"))
                .filter(o -> o instanceof Map)
                .orElse(Map.of());
        Map<String, Object> summary = new LinkedHashMap<>(summarySrc);
        String itemNs = firstNonBlank(asString(summary.get("namespace")), namespace);
        TeamHelmReleaseSource record = null;
        try {
            record = sourceRepo.findByRegionNameAndNamespaceAndReleaseName(regionName, itemNs, releaseName).orElse(null);
        } catch (Exception e) {
            log.error("get helm release source failed: region={} ns={} release={}", regionName, itemNs, releaseName, e);
        }
        String valuesYaml = record == null ? null : record.getValuesYaml();
        if (!isBlank(valuesYaml)) {
            summary.put("values", valuesYaml);
        }
        summary.put("source_info", buildSourceInfo(record, summary));
        safe.put("summary", summary);
        return safe;
    }

    private void safelyPersistSource(Map<String, Object> rawBody, Map<String, Object> installBody,
                                       Map<String, Object> responseBean,
                                       String teamName, String regionName, String namespace, String creator) {
        try {
            persistReleaseSource(rawBody, installBody, responseBean, teamName, regionName, namespace, creator);
        } catch (Exception e) {
            log.error("persist helm release source failed: team={} region={} ns={}", teamName, regionName, namespace, e);
        }
    }

    /** 对齐 Python {@code persist_helm_release_source}：保留**原始 raw_body.source_type**。 */
    void persistReleaseSource(Map<String, Object> rawBody, Map<String, Object> installBody,
                                Map<String, Object> responseBean,
                                String teamName, String regionName, String namespace, String creator) {
        String releaseName = firstNonBlank(
                asString(safeMap(responseBean).get("release_name")),
                asString(installBody.get("release_name")),
                asString(rawBody == null ? null : rawBody.get("release_name")),
                asString(rawBody == null ? null : rawBody.get("name")));
        if (isBlank(releaseName)) {
            return;
        }
        String sourceType = firstNonBlank(asString(rawBody == null ? null : rawBody.get("source_type")), "store");

        TeamHelmReleaseSource record = sourceRepo.findByRegionNameAndNamespaceAndReleaseName(regionName, namespace, releaseName)
                .orElseGet(TeamHelmReleaseSource::new);
        boolean isNew = record.getId() == null;
        if (isNew) {
            record.setCreateTime(LocalDateTime.now());
        }
        record.setTeamName(teamName);
        record.setRegionName(regionName);
        record.setNamespace(namespace);
        record.setReleaseName(releaseName);
        record.setSourceType(sourceType);
        record.setRepoName(firstNonBlank(asString(rawBody == null ? null : rawBody.get("repo_name")), asString(installBody.get("repo_name"))));
        record.setRepoUrl(firstNonBlank(asString(rawBody == null ? null : rawBody.get("repo_url")), asString(installBody.get("repo_url"))));
        record.setChartName(firstNonBlank(
                asString(rawBody == null ? null : rawBody.get("chart_name")),
                asString(rawBody == null ? null : rawBody.get("chart")),
                asString(installBody.get("chart_name")),
                asString(installBody.get("chart"))));
        record.setChartVersion(firstNonBlank(
                asString(rawBody == null ? null : rawBody.get("version")),
                asString(installBody.get("version"))));
        record.setValuesYaml(normalizeYaml(
                rawBody == null ? null : rawBody.get("values"),
                installBody.get("values")));
        record.setCreator(nullToEmpty(creator));
        record.setUpdateTime(LocalDateTime.now());
        sourceRepo.save(record);
    }

    private Map<String, TeamHelmReleaseSource> lookupSources(String regionName, String namespace, Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Map.of();
        }
        try {
            List<TeamHelmReleaseSource> records = sourceRepo
                    .findByRegionNameAndNamespaceAndReleaseNameIn(regionName, namespace, names);
            Map<String, TeamHelmReleaseSource> out = new HashMap<>(records.size());
            for (TeamHelmReleaseSource r : records) {
                out.put(r.getNamespace() + "/" + r.getReleaseName(), r);
            }
            return out;
        } catch (Exception e) {
            log.error("list helm release source failed: region={} ns={}", regionName, namespace, e);
            return Map.of();
        }
    }

    /** 对齐 Python {@code build_helm_release_source_info}。 */
    private Map<String, Object> buildSourceInfo(TeamHelmReleaseSource record, Map<String, Object> release) {
        String sourceType = record == null || isBlank(record.getSourceType()) ? "legacy" : record.getSourceType();
        String repoName = record == null ? "" : nullToEmpty(record.getRepoName());
        String repoUrl = record == null ? "" : nullToEmpty(record.getRepoUrl());
        String chartName = firstNonBlank(record == null ? "" : nullToEmpty(record.getChartName()),
                release == null ? "" : asString(release.get("chart")));
        String chartVersion = firstNonBlank(record == null ? "" : nullToEmpty(record.getChartVersion()),
                release == null ? "" : asString(release.get("chart_version")));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("source_type", sourceType);
        info.put("repo_name", repoName);
        info.put("repo_url", repoUrl);
        info.put("chart_name", nullToEmpty(chartName));
        info.put("chart_version", nullToEmpty(chartVersion));
        info.put("upgrade_mode", "store".equals(sourceType) ? "store_locked" : "manual_select");
        return info;
    }

    /** 对齐 Python {@code normalize_helm_values_yaml}：dict/list → SnakeYAML dump，其他 → toString。 */
    String normalizeYaml(Object... candidates) {
        for (Object value : candidates) {
            if (value == null) continue;
            if (value instanceof String s) {
                if (s.isEmpty()) continue;
                return s;
            }
            if (value instanceof byte[] bytes) {
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
            if (value instanceof Map || value instanceof Collection) {
                DumperOptions opts = new DumperOptions();
                opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                opts.setAllowUnicode(true);
                return new Yaml(opts).dump(value);
            }
            return value.toString();
        }
        return "";
    }

    // ---- micro-utils ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeMap(Object o) {
        if (o instanceof Map m) return (Map) m;
        return new LinkedHashMap<>();
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) return v;
        }
        return "";
    }
}
