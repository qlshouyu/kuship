package cn.kuship.console.modules.region.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集群插件读（对齐 rainbond rbd_plugin_service.list_plugins）。
 * 调 region GET /v2/cluster/plugins?official={bool}，映射 region 返回的插件字段；need_authz 标识是否存在企业级插件。
 * official=true 对齐 officialplugins；official=false 对齐 plugins。
 *
 * <p>注：rainbond 还会从云端应用市场补 app_level、并从 console 库补 app_id/urls 等富化字段；
 * 该市场客户端尚未移植，本实现以 region 返回为主、富化字段尽力而为，need_authz 在无市场元数据时为 false。
 * region 不可达 → 优雅返回空列表（与 7070 在无插件/不可达环境下的出参一致）。
 */
@Service
public class RainbondPluginService {

    private static final Logger log = LoggerFactory.getLogger(RainbondPluginService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RegionClient regionClient;

    public RainbondPluginService(RegionClient regionClient) {
        this.regionClient = regionClient;
    }

    /** 官方插件列表 + need_authz（official=true）。 */
    public PluginResult listOfficialPlugins(String enterpriseId, String regionName) {
        return listPlugins(enterpriseId, regionName, true);
    }

    /** 插件列表 + need_authz（official 由调用方指定）。 */
    @SuppressWarnings("unchecked")
    public PluginResult listPlugins(String enterpriseId, String regionName, boolean official) {
        List<Map<String, Object>> plugins = new ArrayList<>();
        boolean needAuthz = false;
        try {
            String body = regionClient.exchange(regionName, "GET", "/v2/cluster/plugins?official=" + official,
                    null, TimeoutTier.NORMAL, null);
            Map<String, Object> root = MAPPER.readValue(body, Map.class);
            Object listObj = root.get("list");
            if (listObj instanceof List<?> rl) {
                for (Object o : rl) {
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> p = (Map<String, Object>) o;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", p.get("name"));
                    m.put("category", p.get("category"));
                    m.put("alias", p.get("alias"));
                    m.put("region_app_id", p.get("region_app_id"));
                    m.put("team_name", p.get("team_name"));
                    m.put("status", p.get("status"));
                    m.put("app_level", p.get("app_level"));
                    m.put("display_name", p.get("alias"));
                    m.put("backend", p.get("backend"));
                    m.put("frontend_service", p.get("frontend_service"));
                    m.put("access_urls", p.getOrDefault("access_urls", List.of()));
                    m.put("urls", p.getOrDefault("access_urls", List.of()));
                    m.put("plugin_views", p.getOrDefault("plugin_views", List.of()));
                    m.put("plugin_type", p.get("plugin_type"));
                    if ("enterprise".equals(p.get("app_level"))) {
                        needAuthz = true;
                    }
                    plugins.add(m);
                }
            }
        } catch (Exception e) {
            log.warn("list plugins(official={}) from region {}: {}", official, regionName, e.getMessage());
        }
        return new PluginResult(plugins, needAuthz);
    }

    /** (plugins, need_authz)。 */
    public record PluginResult(List<Map<String, Object>> plugins, boolean needAuthz) {
    }
}
