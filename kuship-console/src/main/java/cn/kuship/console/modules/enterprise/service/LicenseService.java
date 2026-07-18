package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import cn.kuship.console.modules.enterprise.entity.ConsoleSysConfig;
import cn.kuship.console.modules.enterprise.repository.ConsoleSysConfigRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业 license 读（对齐 rainbond LicenseService.get_licenses）。
 * <ul>
 *   <li>无 AUTHZ_CODE → ("", null)，view 返回 400 invalid authz code，bean.authz_code=""。</li>
 *   <li>有 AUTHZ_CODE、无可用集群 → (code, {valid:false, reason:"no_region", ...})。</li>
 *   <li>有 AUTHZ_CODE、有集群 → 调 region /v2/license/status 组装授权信息。</li>
 * </ul>
 * 注：rainbond 在 no_region 分支会本地 base64 解码 authz_code 取 plugin_mapping/plugin_names；
 * 该解码逻辑尚未移植，本实现该分支 plugins 暂返回 []（不影响无授权码主流程的出参对齐）。
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConsoleSysConfigRepository sysConfigRepository;
    private final RegionConfigRepository regionConfigRepository;
    private final RegionClient regionClient;

    public LicenseService(ConsoleSysConfigRepository sysConfigRepository,
                          RegionConfigRepository regionConfigRepository,
                          RegionClient regionClient) {
        this.sysConfigRepository = sysConfigRepository;
        this.regionConfigRepository = regionConfigRepository;
        this.regionClient = regionClient;
    }

    /** (authz_code, license)；二者任一为空时 controller 返回 400（对齐 get_licenses 返回值约定）。 */
    public LicenseResult getLicenses(String enterpriseId) {
        ConsoleSysConfig authz = sysConfigRepository.findFirstByKey("AUTHZ_CODE").orElse(null);
        if (authz == null || authz.getValue() == null || authz.getValue().isBlank()) {
            return new LicenseResult("", null);
        }
        String authzCode = authz.getValue();

        RegionConfig region = regionConfigRepository.findByEnterpriseIdOrderById(enterpriseId).stream()
                .filter(r -> "1".equals(r.getStatus()) || "3".equals(r.getStatus()))
                .findFirst()
                .orElse(null);
        if (region == null) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("authz_code", authzCode);
            resp.put("valid", false);
            resp.put("reason", "no_region");
            resp.put("plugins", List.of());
            return new LicenseResult(authzCode, resp);
        }

        Map<String, Object> bean = new LinkedHashMap<>();
        try {
            String body = regionClient.exchange(region.getRegionName(), "GET", "/v2/license/status",
                    null, TimeoutTier.NORMAL, null);
            Object b = MAPPER.readValue(body, Map.class).get("bean");
            if (b instanceof Map<?, ?> bm) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) bm;
                bean = casted;
            }
        } catch (Exception e) {
            log.warn("get license status from region {}: {}", region.getRegionName(), e.getMessage());
        }

        Object pluginMapping = bean.getOrDefault("plugin_mapping", new LinkedHashMap<>());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("authz_code", authzCode);
        resp.put("valid", bean.getOrDefault("valid", false));
        resp.put("reason", bean.getOrDefault("reason", ""));
        resp.put("code", bean.getOrDefault("code", ""));
        resp.put("enterprise_id", bean.getOrDefault("enterprise_id", ""));
        resp.put("company", bean.getOrDefault("company", ""));
        resp.put("contact", bean.getOrDefault("contact", ""));
        resp.put("tier", bean.getOrDefault("tier", ""));
        resp.put("cluster_id", bean.getOrDefault("cluster_id", ""));
        resp.put("plugin_mapping", pluginMapping);
        resp.put("plugins", buildPlugins(pluginMapping, bean.get("plugin_names")));
        resp.put("start_at", bean.getOrDefault("start_at", 0));
        resp.put("expire_at", bean.getOrDefault("expire_at", 0));
        resp.put("subscribe_until", bean.getOrDefault("subscribe_until", 0));
        resp.put("cluster_limit", bean.getOrDefault("cluster_limit", 0));
        resp.put("node_limit", bean.getOrDefault("node_limit", 0));
        resp.put("memory_limit", bean.getOrDefault("memory_limit", 0));
        resp.put("cpu_limit", bean.getOrDefault("cpu_limit", 0));
        resp.put("access_key", bean.getOrDefault("access_key", ""));
        return new LicenseResult(authzCode, resp);
    }

    /** 对齐 _build_plugins_list：按 plugin_mapping(pid->app_key) 组装 [{plugin_id, app_key, name}]，name 取自 plugin_names。 */
    private List<Map<String, Object>> buildPlugins(Object pluginMapping, Object pluginNames) {
        List<Map<String, Object>> plugins = new ArrayList<>();
        if (!(pluginMapping instanceof Map<?, ?> pm)) {
            return plugins;
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> pn = pluginNames instanceof Map ? (Map<Object, Object>) pluginNames : Map.of();
        for (Map.Entry<?, ?> e : pm.entrySet()) {
            String pid = String.valueOf(e.getKey());
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("plugin_id", pid);
            p.put("app_key", e.getValue());
            p.put("name", pn.getOrDefault(pid, pid));
            plugins.add(p);
        }
        return plugins;
    }

    /** get_licenses 返回值：authz_code（授权码原文，无则 ""）+ license（无效时 null）。 */
    public record LicenseResult(String authzCode, Map<String, Object> license) {
    }
}
