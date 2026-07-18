package cn.kuship.console.modules.region.service;

import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业集群列表读（safe 级，对齐 rainbond get_enterprise_regions(level=safe) + __init_region_resource_data）。
 * check_status 为空 → 资源/健康字段用固定默认，不调 region 后端。
 */
@Service
public class EnterpriseRegionReadService {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final RegionConfigRepository regionConfigRepository;
    private final TenantEnterpriseRepository enterpriseRepository;
    private final cn.kuship.console.infrastructure.region.RegionClient regionClient;

    public EnterpriseRegionReadService(RegionConfigRepository regionConfigRepository,
                                       TenantEnterpriseRepository enterpriseRepository,
                                       cn.kuship.console.infrastructure.region.RegionClient regionClient) {
        this.regionConfigRepository = regionConfigRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.regionClient = regionClient;
    }

    /** 企业集群列表。status 非空按状态过滤；checkStatus="yes" 时实时拉取 region 资源/版本/节点（对齐 conver_region_info）。 */
    public List<Map<String, Object>> listRegions(String enterpriseId, String status, String checkStatus) {
        List<RegionConfig> regions = (status == null || status.isBlank())
                ? regionConfigRepository.findByEnterpriseIdOrderById(enterpriseId)
                : regionConfigRepository.findByEnterpriseIdAndStatusOrderById(enterpriseId, status);
        String enterpriseAlias = enterpriseRepository.findByEnterpriseId(enterpriseId)
                .map(TenantEnterprise::getEnterpriseAlias).orElse(null);
        boolean check = "yes".equals(checkStatus);
        List<Map<String, Object>> out = new ArrayList<>();
        for (RegionConfig r : regions) {
            Map<String, Object> dict = toSafeDict(r, enterpriseAlias);
            if (check) {
                enrichWithRegion(r.getRegionName(), dict);
            }
            out.add(dict);
        }
        return out;
    }

    /**
     * 单集群详情（open 级，对齐 EnterpriseRegionsRUDView.get → get_enterprise_region(check_status=False)
     * → conver_region_info(check_status=False) → __init_region_resource_data(level="open")）。
     * check_status=False → 不调 region 后端，资源/健康用固定默认。region_id 不存在 → null。
     */
    public Map<String, Object> getRegion(String enterpriseId, String regionId) {
        RegionConfig r = regionConfigRepository.findByRegionId(regionId).orElse(null);
        if (r == null) {
            return null;
        }
        String enterpriseAlias = enterpriseRepository.findByEnterpriseId(r.getEnterpriseId())
                .map(TenantEnterprise::getEnterpriseAlias).orElse(null);
        return toOpenDict(r, enterpriseAlias);
    }

    /** open 级集群字典（在 safe 22 字段基础上，于 provider_cluster_id 与 desc 之间插入 6 个 open 专属字段）。 */
    private Map<String, Object> toOpenDict(RegionConfig r, String enterpriseAlias) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("region_id", r.getRegionId());
        m.put("region_alias", r.getRegionAlias());
        m.put("region_name", r.getRegionName());
        m.put("status", r.getStatus());
        m.put("region_type", parseRegionType(r.getRegionType()));
        m.put("enterprise_id", r.getEnterpriseId());
        m.put("url", r.getUrl());
        m.put("scope", resolveScope(r));
        m.put("provider", r.getProvider());
        m.put("provider_cluster_id", r.getProviderClusterId());
        // open 专属
        m.put("wsurl", r.getWsurl());
        m.put("httpdomain", r.getHttpdomain());
        m.put("tcpdomain", r.getTcpdomain());
        m.put("ssl_ca_cert", r.getSslCaCert());
        m.put("cert_file", r.getCertFile());
        m.put("key_file", r.getKeyFile());
        m.put("desc", r.getDesc());
        m.put("total_memory", 0);
        m.put("used_memory", 0);
        m.put("total_cpu", 0);
        m.put("used_cpu", 0);
        m.put("total_disk", 0);
        m.put("used_disk", 0);
        m.put("rbd_version", "unknown");
        m.put("health_status", "ok");
        m.put("resource_proxy_status", false);
        m.put("create_time", cn.kuship.console.common.util.PyIsoDateTime.iso(r.getCreateTime())); // ISO 微秒不截尾零，对齐 DRF
        m.put("enterprise_alias", enterpriseAlias);
        return m;
    }

    /** scope = os.getenv("IS_STANDALONE", region.scope)：环境变量优先，否则库值。 */
    private static String resolveScope(RegionConfig r) {
        String standalone = System.getenv("IS_STANDALONE");
        return (standalone != null && !standalone.isEmpty()) ? standalone : r.getScope();
    }

    /** 实时拉取 region 资源（/v2/cluster）、版本（/v2/show）、节点架构（/v2/cluster/nodes），对齐 conver_region_info(check_status=yes)。 */
    @SuppressWarnings("unchecked")
    private void enrichWithRegion(String regionName, Map<String, Object> dict) {
        try {
            String rbdVersion = regionClient.exchange(regionName, "GET", "/v2/show", null,
                    cn.kuship.console.infrastructure.region.TimeoutTier.NORMAL, null);
            String clusterBody = regionClient.exchange(regionName, "GET", "/v2/cluster", null,
                    cn.kuship.console.infrastructure.region.TimeoutTier.NORMAL, null);
            Map<String, Object> bean = (Map<String, Object>) MAPPER.readValue(clusterBody, Map.class).get("bean");
            dict.put("total_memory", bean.get("cap_mem"));
            dict.put("used_memory", bean.get("req_mem"));
            dict.put("total_cpu", bean.get("cap_cpu"));
            dict.put("used_cpu", bean.get("req_cpu"));
            dict.put("total_disk", toLong(bean.get("cap_disk")) / 1024.0 / 1024 / 1024);
            dict.put("used_disk", toLong(bean.get("req_disk")) / 1024.0 / 1024 / 1024);
            dict.put("rbd_version", rbdVersion == null ? "" : rbdVersion.trim());
            dict.put("resource_proxy_status", bean.get("resource_proxy_status"));
            dict.put("k8s_version", bean.get("k8s_version"));
            dict.put("all_nodes", bean.get("all_node"));
            Map<String, Object> servicesStatus = new LinkedHashMap<>();
            servicesStatus.put("running", bean.get("run_pod_number"));
            dict.put("services_status", servicesStatus);
            // pods 空态对齐 7070：缺省为 {} 而非 null
            Object pods = bean.get("pods");
            dict.put("pods", pods == null ? new LinkedHashMap<>() : pods);
            dict.put("run_pod_number", bean.get("run_pod_number"));
            dict.put("node_ready", bean.get("node_ready"));
            // 节点架构
            String nodesBody = regionClient.exchange(regionName, "GET", "/v2/cluster/nodes", null,
                    cn.kuship.console.infrastructure.region.TimeoutTier.NORMAL, null);
            List<Object> nodes = (List<Object>) MAPPER.readValue(nodesBody, Map.class).getOrDefault("list", List.of());
            java.util.LinkedHashSet<String> arch = new java.util.LinkedHashSet<>();
            for (Object n : nodes) {
                Object a = ((Map<String, Object>) n).get("architecture");
                if (a != null) {
                    arch.add(a.toString());
                }
            }
            dict.put("arch", new ArrayList<>(arch));
        } catch (Exception e) {
            // 对齐 conver_region_info 异常分支
            dict.put("rbd_version", "");
            dict.put("health_status", "failure");
        }
    }

    private static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private Map<String, Object> toSafeDict(RegionConfig r, String enterpriseAlias) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("region_id", r.getRegionId());
        m.put("region_alias", r.getRegionAlias());
        m.put("region_name", r.getRegionName());
        m.put("status", r.getStatus());
        m.put("region_type", parseRegionType(r.getRegionType()));
        m.put("enterprise_id", r.getEnterpriseId());
        m.put("url", r.getUrl());
        m.put("scope", r.getScope());
        m.put("provider", r.getProvider());
        m.put("provider_cluster_id", r.getProviderClusterId());
        m.put("desc", r.getDesc());
        m.put("total_memory", 0);
        m.put("used_memory", 0);
        m.put("total_cpu", 0);
        m.put("used_cpu", 0);
        m.put("total_disk", 0);
        m.put("used_disk", 0);
        m.put("rbd_version", "unknown");
        m.put("health_status", "ok");
        m.put("resource_proxy_status", false);
        m.put("create_time", cn.kuship.console.common.util.PyIsoDateTime.iso(r.getCreateTime())); // ISO 微秒不截尾零，对齐 DRF
        m.put("enterprise_alias", enterpriseAlias);
        return m;
    }

    /**
     * region_type 列 JSON 解析为 list（对齐 json.loads(region_type) if region_type else []）。
     * 字符串数组的极简解析：空/非数组 → []；否则去 [] 后按逗号拆、去引号与空白。
     */
    private static Object parseRegionType(String regionType) {
        List<String> out = new ArrayList<>();
        if (regionType == null) {
            return out;
        }
        String s = regionType.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) {
            return out;
        }
        String inner = s.substring(1, s.length() - 1).trim();
        if (inner.isEmpty()) {
            return out;
        }
        for (String part : inner.split(",")) {
            String v = part.trim();
            if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
                v = v.substring(1, v.length() - 1);
            }
            out.add(v);
        }
        return out;
    }
}
