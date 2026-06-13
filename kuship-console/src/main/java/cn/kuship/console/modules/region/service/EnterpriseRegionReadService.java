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

    private final RegionConfigRepository regionConfigRepository;
    private final TenantEnterpriseRepository enterpriseRepository;

    public EnterpriseRegionReadService(RegionConfigRepository regionConfigRepository,
                                       TenantEnterpriseRepository enterpriseRepository) {
        this.regionConfigRepository = regionConfigRepository;
        this.enterpriseRepository = enterpriseRepository;
    }

    /** 企业集群列表（safe 级，资源默认）。status 非空时按状态过滤。 */
    public List<Map<String, Object>> listRegions(String enterpriseId, String status) {
        List<RegionConfig> regions = (status == null || status.isBlank())
                ? regionConfigRepository.findByEnterpriseIdOrderById(enterpriseId)
                : regionConfigRepository.findByEnterpriseIdAndStatusOrderById(enterpriseId, status);
        String enterpriseAlias = enterpriseRepository.findByEnterpriseId(enterpriseId)
                .map(TenantEnterprise::getEnterpriseAlias).orElse(null);
        List<Map<String, Object>> out = new ArrayList<>();
        for (RegionConfig r : regions) {
            out.add(toSafeDict(r, enterpriseAlias));
        }
        return out;
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
        m.put("create_time", r.getCreateTime()); // ISO（Jackson 默认），对齐 DRF
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
