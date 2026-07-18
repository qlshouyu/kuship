package cn.kuship.console.modules.region.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import cn.kuship.console.modules.region.entity.AppMarket;
import cn.kuship.console.modules.region.entity.RegionApp;
import cn.kuship.console.modules.region.entity.TenantServiceGroup;
import cn.kuship.console.modules.region.repository.AppMarketRepository;
import cn.kuship.console.modules.region.repository.RegionAppRepository;
import cn.kuship.console.modules.region.repository.TenantServiceGroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台插件读（对齐 rainbond platform_plugin_service.list_platform_plugins，1:1 移植）。
 *
 * <p>数据源：云端应用市场 {@code {market_url}/app-server/openapi/apps/platform-plugins}
 * （marketDomain 恒为 enterprise，Authorization=app_market.access_key），叠加
 * region 已安装插件（/v2/cluster/plugins?official=false）、集群架构（/v2/cluster/nodes/arch）
 * 与授权（/v2/license/status 的 plugin_mapping）过滤。
 *
 * <p>合并规则（与 rainbond 相同）：按 plugin_id 分组 → 按集群 arch 过滤候选（混合架构 amd64 优先）
 * → free → license app_key → first 选中 SKU → 有效授权下过滤未授权企业级插件
 * → 已安装 SKU 锚定 latest_version（跨源安装强制不报可升级）。
 * 市场与集群架构结果各缓存 60s（对齐 MARKET_PLUGIN_CACHE_TTL_SECONDS/REGION_ARCH_CACHE_TTL_SECONDS）。
 */
@Service
public class PlatformPluginService {

    private static final Logger log = LoggerFactory.getLogger(PlatformPluginService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PLATFORM_PLUGIN_DEFAULT_URL = "https://hub.grapps.cn";
    private static final String PLATFORM_PLUGIN_MARKET_DOMAIN = "enterprise";
    private static final Set<String> KNOWN_ARCHES = Set.of("amd64", "arm64");
    private static final String DEFAULT_ARCH = "amd64";
    private static final List<String> ARCH_PLUGIN_SUFFIXES = List.of("-ARM64", "-AMD64");
    private static final long MARKET_PLUGIN_CACHE_TTL_MS = 60_000;
    private static final long REGION_ARCH_CACHE_TTL_MS = 60_000;

    private final AppMarketRepository appMarketRepository;
    private final RegionAppRepository regionAppRepository;
    private final TenantServiceGroupRepository tenantServiceGroupRepository;
    private final RegionClient regionClient;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private record CacheEntry(long expiresAt, Object value) {
    }

    private final Map<String, CacheEntry> marketPluginCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> regionArchCache = new ConcurrentHashMap<>();

    public PlatformPluginService(AppMarketRepository appMarketRepository,
                                 RegionAppRepository regionAppRepository,
                                 TenantServiceGroupRepository tenantServiceGroupRepository,
                                 RegionClient regionClient) {
        this.appMarketRepository = appMarketRepository;
        this.regionAppRepository = regionAppRepository;
        this.tenantServiceGroupRepository = tenantServiceGroupRepository;
        this.regionClient = regionClient;
    }

    /** 平台插件候选列表（对齐 list_platform_plugins）。 */
    public List<Map<String, Object>> listPlatformPlugins(String enterpriseId, String regionName) {
        if (isCloudMarketDisabled()) {
            return List.of();
        }
        Map<String, Object> licenseBean = getLicenseBean(regionName);
        Map<String, Object> pluginMapping = asMap(licenseBean.get("plugin_mapping"));
        boolean hasValidLicense = Boolean.TRUE.equals(licenseBean.get("valid"));
        Map<String, Map<String, Object>> installedPlugins = getInstalledPlugins(regionName);
        Map<String, Integer> regionAppIdMap = getRegionAppIdMap(regionName, installedPlugins);
        Set<String> regionArches = getRegionArches(regionName);
        List<Map<String, Object>> marketPlugins;
        try {
            marketPlugins = getMarketPlatformPluginsCached(enterpriseId);
        } catch (Exception e) {
            log.warn("get market platform plugins failed: {}", e.getMessage());
            marketPlugins = List.of();
        }

        // 按 plugin_id 分组（保留市场返回的出现顺序）
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> mp : marketPlugins) {
            String pid = str(mp.get("plugin_id"));
            if (!pid.isEmpty()) {
                grouped.computeIfAbsent(pid, k -> new ArrayList<>()).add(mp);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            String pluginId = entry.getKey();
            List<Map<String, Object>> candidates = entry.getValue();

            // 1. 按集群 arch 过滤候选
            List<Map<String, Object>> archMatched = candidates.stream()
                    .filter(c -> regionArches.contains(getPluginArch(c)))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (archMatched.isEmpty()) {
                continue;
            }
            // 2. 混合架构 tie-breaker：amd64 优先（稳定排序）
            archMatched.sort(java.util.Comparator.comparingInt(c -> DEFAULT_ARCH.equals(getPluginArch(c)) ? 0 : 1));

            // 3. free → license app_key → first
            Map<String, Object> selected = selectMarketPlugin(archMatched, pluginId, pluginMapping);
            if (selected == null) {
                continue;
            }

            // 4. license 过滤（有效授权时未授权的非 free 插件不展示）
            String appLevel = normalizeAppLevel(selected);
            if (hasValidLicense && !"free".equals(appLevel) && !isPluginAuthorized(pluginMapping, pluginId)) {
                continue;
            }

            // 5. installed SKU 锚定 latest_version
            Map<String, Object> installedSku = null;
            if (installedPlugins.containsKey(pluginId)) {
                installedSku = resolveInstalledSku(installedPlugins.get(pluginId), regionAppIdMap, candidates);
            }
            Map<String, Object> displaySku = installedSku != null ? installedSku : selected;

            result.add(buildPluginInfo(pluginId, selected, displaySku, installedSku,
                    installedPlugins, regionAppIdMap, candidates));
        }
        return result;
    }

    // ---------- 数据源 ----------

    private static boolean isCloudMarketDisabled() {
        return envIsTrue("DISABLE_DEFAULT_APP_MARKET") || envIsTrue("DISABLE_CLOUD_MARKET");
    }

    private static boolean envIsTrue(String key) {
        String v = System.getenv(key);
        return v != null && List.of("true", "1", "yes", "on").contains(v.trim().toLowerCase(Locale.ROOT));
    }

    /** region /v2/license/status 的 bean；失败 → 空 map（对齐 _get_license_bean）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getLicenseBean(String regionName) {
        try {
            String body = regionClient.exchange(regionName, "GET", "/v2/license/status",
                    null, TimeoutTier.NORMAL, null);
            Object bean = MAPPER.readValue(body, Map.class).get("bean");
            return bean instanceof Map ? (Map<String, Object>) bean : new LinkedHashMap<>();
        } catch (Exception e) {
            log.warn("get license status from region {}: {}", regionName, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** region 已安装平台插件，按 name 索引（对齐 _get_installed_plugins）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> getInstalledPlugins(String regionName) {
        Map<String, Map<String, Object>> installed = new LinkedHashMap<>();
        try {
            String body = regionClient.exchange(regionName, "GET", "/v2/cluster/plugins?official=False",
                    null, TimeoutTier.NORMAL, null);
            Object listObj = MAPPER.readValue(body, Map.class).get("list");
            if (listObj instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map) {
                        Map<String, Object> p = (Map<String, Object>) o;
                        installed.put(str(p.get("name")), p);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("list region plugins failed region={}: {}", regionName, e.getMessage());
        }
        return installed;
    }

    /** region_app_id → console app_id（对齐 _get_region_app_id_map）。 */
    private Map<String, Integer> getRegionAppIdMap(String regionName,
                                                   Map<String, Map<String, Object>> installedPlugins) {
        Map<String, Integer> map = new LinkedHashMap<>();
        List<String> regionAppIds = installedPlugins.values().stream()
                .map(p -> str(p.get("region_app_id")))
                .filter(s -> !s.isEmpty())
                .toList();
        if (regionAppIds.isEmpty()) {
            return map;
        }
        try {
            for (RegionApp ra : regionAppRepository.findByRegionNameAndRegionAppIdIn(regionName, regionAppIds)) {
                map.put(ra.getRegionAppId(), ra.getAppId());
            }
        } catch (Exception e) {
            log.warn("map region_app_ids failed: {}", e.getMessage());
        }
        return map;
    }

    /** 集群节点架构集合，60s 缓存；失败回退全集（对齐 _get_region_arches）。 */
    @SuppressWarnings("unchecked")
    private Set<String> getRegionArches(String regionName) {
        CacheEntry entry = regionArchCache.get(regionName);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expiresAt() > now) {
            return (Set<String>) entry.value();
        }
        Set<String> arches = new TreeSet<>();
        try {
            String body = regionClient.exchange(regionName, "GET", "/v2/cluster/nodes/arch",
                    null, TimeoutTier.NORMAL, null);
            Object listObj = MAPPER.readValue(body, Map.class).get("list");
            if (listObj instanceof List<?> l) {
                for (Object o : l) {
                    String a = str(o).trim().toLowerCase(Locale.ROOT);
                    if (KNOWN_ARCHES.contains(a)) {
                        arches.add(a);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("get region arches failed region={}: {}", regionName, e.getMessage());
        }
        if (arches.isEmpty()) {
            arches = new TreeSet<>(KNOWN_ARCHES);
        }
        regionArchCache.put(regionName, new CacheEntry(now + REGION_ARCH_CACHE_TTL_MS, arches));
        return arches;
    }

    /** 云端市场平台插件列表，60s 缓存（对齐 _get_market_platform_plugins_cached）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMarketPlatformPluginsCached(String enterpriseId) throws Exception {
        CacheEntry entry = marketPluginCache.get(enterpriseId);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expiresAt() > now) {
            return (List<Map<String, Object>>) entry.value();
        }
        List<Map<String, Object>> plugins = fetchMarketPlatformPlugins(enterpriseId);
        marketPluginCache.put(enterpriseId, new CacheEntry(now + MARKET_PLUGIN_CACHE_TTL_MS, plugins));
        return plugins;
    }

    /**
     * GET {market_url}/app-server/openapi/apps/platform-plugins?marketDomain=enterprise&query=&page=1&pageSize=-1
     * （对齐 app_store.get_platform_plugins；url/access_key 取企业默认市场，无市场行则用兜底 URL）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchMarketPlatformPlugins(String enterpriseId) throws Exception {
        AppMarket market = appMarketRepository.findFirstByEnterpriseIdOrderByIdAsc(enterpriseId).orElse(null);
        String url = market != null && market.getUrl() != null && !market.getUrl().isBlank()
                ? market.getUrl() : PLATFORM_PLUGIN_DEFAULT_URL;
        String accessKey = market != null ? market.getAccessKey() : null;

        String full = url.replaceAll("/+$", "")
                + "/app-server/openapi/apps/platform-plugins?marketDomain=" + PLATFORM_PLUGIN_MARKET_DOMAIN
                + "&query=&page=1&pageSize=-1";
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(full))
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (accessKey != null && !accessKey.isBlank()) {
            rb.header("Authorization", accessKey);
        }
        HttpResponse<String> resp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("market http " + resp.statusCode());
        }
        Object pluginsObj = MAPPER.readValue(resp.body(), Map.class).get("plugins");
        List<Map<String, Object>> plugins = new ArrayList<>();
        if (pluginsObj instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map) {
                    plugins.add((Map<String, Object>) o);
                }
            }
        }
        return plugins;
    }

    // ---------- 合并规则 ----------

    /** SKU 的 arch，非法/缺失兜底 amd64（对齐 _get_plugin_arch）。 */
    private static String getPluginArch(Map<String, Object> pluginInfo) {
        String arch = str(pluginInfo.get("arch")).trim().toLowerCase(Locale.ROOT);
        return KNOWN_ARCHES.contains(arch) ? arch : DEFAULT_ARCH;
    }

    /** appLevel || app_level || "enterprise"（对齐 _normalize_app_level，Python or 语义：空串/空值均视为缺失）。 */
    private static String normalizeAppLevel(Map<String, Object> pluginInfo) {
        String v = str(pluginInfo.get("appLevel"));
        if (!v.isEmpty()) {
            return v;
        }
        v = str(pluginInfo.get("app_level"));
        return v.isEmpty() ? "enterprise" : v;
    }

    private static String stripPluginArchSuffix(String pluginId) {
        String upper = pluginId.toUpperCase(Locale.ROOT);
        for (String suffix : ARCH_PLUGIN_SUFFIXES) {
            if (upper.endsWith(suffix)) {
                return pluginId.substring(0, pluginId.length() - suffix.length());
            }
        }
        return pluginId;
    }

    /** plugin_mapping 里按 id（含 -ARM64/-AMD64 基名归一）解析 app_key（对齐 _resolve_plugin_mapping_app_key）。 */
    private static Object resolvePluginMappingAppKey(Map<String, Object> pluginMapping, String pluginId) {
        if (pluginId.isEmpty() || pluginMapping.isEmpty()) {
            return null;
        }
        if (pluginMapping.containsKey(pluginId)) {
            return pluginMapping.get(pluginId);
        }
        String normalized = stripPluginArchSuffix(pluginId);
        for (Map.Entry<String, Object> e : pluginMapping.entrySet()) {
            if (stripPluginArchSuffix(e.getKey()).equals(normalized)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean isPluginAuthorized(Map<String, Object> pluginMapping, String pluginId) {
        Object key = resolvePluginMappingAppKey(pluginMapping, pluginId);
        return key != null && !str(key).isEmpty();
    }

    /** free → license app_key → first（对齐 _select_market_plugin）。 */
    private static Map<String, Object> selectMarketPlugin(List<Map<String, Object>> marketPlugins,
                                                          String pluginId, Map<String, Object> pluginMapping) {
        List<Map<String, Object>> candidates = marketPlugins.stream()
                .filter(item -> pluginId.equals(item.get("plugin_id")))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        for (Map<String, Object> item : candidates) {
            if ("free".equals(normalizeAppLevel(item))) {
                return item;
            }
        }
        Object appKey = resolvePluginMappingAppKey(pluginMapping, pluginId);
        if (appKey != null && !str(appKey).isEmpty()) {
            for (Map<String, Object> item : candidates) {
                Object itemKey = truthyOr(item.get("appKeyID"), item.get("app_key"));
                if (appKey.equals(itemKey)) {
                    return item;
                }
            }
        }
        return candidates.get(0);
    }

    /** 已安装 RBDPlugin 反查安装时的 market SKU（对齐 _resolve_installed_sku）。 */
    private Map<String, Object> resolveInstalledSku(Map<String, Object> installedPlugin,
                                                    Map<String, Integer> regionAppIdMap,
                                                    List<Map<String, Object>> candidates) {
        if (installedPlugin == null || candidates.isEmpty()) {
            return null;
        }
        Integer consoleAppId = regionAppIdMap.get(str(installedPlugin.get("region_app_id")));
        if (consoleAppId == null) {
            return null;
        }
        String installedAppKey;
        try {
            TenantServiceGroup g = tenantServiceGroupRepository
                    .findTopByServiceGroupIdOrderByIdDesc(consoleAppId).orElse(null);
            if (g == null) {
                return null;
            }
            installedAppKey = g.getGroupKey();
        } catch (Exception e) {
            log.warn("resolve installed SKU failed app_id={}: {}", consoleAppId, e.getMessage());
            return null;
        }
        if (installedAppKey == null || installedAppKey.isEmpty()) {
            return null;
        }
        for (Map<String, Object> c : candidates) {
            if (installedAppKey.equals(truthyOr(c.get("appKeyID"), c.get("app_key")))) {
                return c;
            }
        }
        return null;
    }

    /** 单条返回 dict，键序与 rainbond _build_plugin_info 一致。 */
    private Map<String, Object> buildPluginInfo(String pluginId,
                                                Map<String, Object> selected,
                                                Map<String, Object> displaySku,
                                                Map<String, Object> installedSku,
                                                Map<String, Map<String, Object>> installedPlugins,
                                                Map<String, Integer> regionAppIdMap,
                                                List<Map<String, Object>> candidates) {
        String appLevel = normalizeAppLevel(selected);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("plugin_id", pluginId);
        info.put("app_key", truthyOr(displaySku.get("appKeyID"), getOr(displaySku, "app_key", "")));
        info.put("plugin_name", truthyElse(displaySku.get("plugin_name"), pluginId));
        info.put("name", truthyElse(displaySku.get("name"), pluginId));
        info.put("description", getOr(displaySku, "description", ""));
        info.put("logo", getOr(displaySku, "logo", ""));
        info.put("app_level", appLevel);
        info.put("latest_version", getOr(displaySku, "latest_version", ""));
        info.put("plugin_type", getOr(displaySku, "plugin_type", ""));
        info.put("plugin_views", getOr(displaySku, "plugin_views", List.of()));
        info.put("frontend_component", getOr(displaySku, "frontend_component", ""));
        info.put("entry_path", getOr(displaySku, "entry_path", ""));
        info.put("menu_title", getOr(displaySku, "menu_title", ""));
        info.put("route_path", getOr(displaySku, "route_path", ""));
        info.put("installed", false);
        info.put("status", "");
        info.put("installed_version", "");
        info.put("upgradeable", false);
        info.put("can_upgrade", false);
        info.put("team_name", "");
        info.put("app_id", -1);
        info.put("author", "Rainbond 官方");
        info.put("selected_arch", getPluginArch(selected));
        info.put("installed_arch", installedSku != null ? getPluginArch(installedSku) : null);
        List<String> availableArches = candidates.stream()
                .map(PlatformPluginService::getPluginArch)
                .distinct().sorted().toList();
        info.put("available_arches", availableArches);

        if (installedPlugins.containsKey(pluginId)) {
            Map<String, Object> installedPlugin = installedPlugins.get(pluginId);
            info.put("installed", true);
            info.put("status", getOr(installedPlugin, "status", ""));
            info.put("team_name", getOr(installedPlugin, "team_name", ""));
            int consoleAppId = regionAppIdMap.getOrDefault(str(installedPlugin.get("region_app_id")), -1);
            info.put("app_id", consoleAppId);
            info.put("plugin_type", getOr(installedPlugin, "plugin_type", info.get("plugin_type")));
            info.put("plugin_views", getOr(installedPlugin, "plugin_views", info.get("plugin_views")));
            if (consoleAppId > 0) {
                tenantServiceGroupRepository.findTopByServiceGroupIdOrderByIdDesc(consoleAppId)
                        .ifPresent(g -> info.put("installed_version",
                                g.getGroupVersion() == null ? "" : g.getGroupVersion()));
            }
            // 跨源安装（installed_sku 为 null）强制不报可升级；同源按版本号比对
            String latest = str(info.get("latest_version"));
            String installedVersion = str(info.get("installed_version"));
            if (installedSku == null) {
                info.put("latest_version", installedVersion);
                info.put("upgradeable", false);
                info.put("can_upgrade", false);
            } else if (!latest.isEmpty() && !installedVersion.isEmpty()) {
                boolean upgradeable = !latest.equals(installedVersion);
                info.put("upgradeable", upgradeable);
                info.put("can_upgrade", upgradeable);
            }
        }
        return info;
    }

    // ---------- 小工具（Python 语义辅助） ----------

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    /** Python `d.get(k, def)`：键缺失才用默认值，键存在但为 null 原样保留。 */
    private static Object getOr(Map<String, Object> m, String key, Object def) {
        return m.containsKey(key) ? m.get(key) : def;
    }

    /** Python `a or b`：a 为 None/空串 时取 b。 */
    private static Object truthyOr(Object a, Object b) {
        return (a == null || "".equals(a)) ? b : a;
    }

    private static Object truthyElse(Object a, Object def) {
        return (a == null || "".equals(a)) ? def : a;
    }
}
