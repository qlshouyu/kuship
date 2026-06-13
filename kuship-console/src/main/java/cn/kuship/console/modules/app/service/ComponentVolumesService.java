package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.entity.TenantServiceVolume;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.app.repository.TenantServiceVolumeRepository;
import cn.kuship.console.modules.team.entity.TenantRegionInfo;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件持久化列表读（对齐 AppVolumeView.get 非 config 路径）。
 * 排除 config-file；create_status!=complete → status=not_bound(纯 DB)，否则按 region 卷状态(READY→bound)；
 * dep_services 单组件为 null（多组件挂载依赖 defer）；[0].first=true。
 */
@Service
public class ComponentVolumesService {

    private static final Logger log = LoggerFactory.getLogger(ComponentVolumesService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NOT_BOUND = "not_bound";
    private static final String BOUND = "bound";
    private static final String READY = "READY";

    private final TenantServiceInfoRepository serviceRepository;
    private final TenantsRepository tenantsRepository;
    private final TenantRegionInfoRepository tenantRegionInfoRepository;
    private final TenantServiceVolumeRepository volumeRepository;
    private final RegionClient regionClient;

    public ComponentVolumesService(TenantServiceInfoRepository serviceRepository,
                                   TenantsRepository tenantsRepository,
                                   TenantRegionInfoRepository tenantRegionInfoRepository,
                                   TenantServiceVolumeRepository volumeRepository,
                                   RegionClient regionClient) {
        this.serviceRepository = serviceRepository;
        this.tenantsRepository = tenantsRepository;
        this.tenantRegionInfoRepository = tenantRegionInfoRepository;
        this.volumeRepository = volumeRepository;
        this.regionClient = regionClient;
    }

    public List<Map<String, Object>> listVolumes(String tenantName, String serviceAlias, String regionName) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        Tenants tenant = tenantsRepository.findByTenantName(tenantName)
                .orElseThrow(() -> ServiceHandleException.notFound("team not found", "团队不存在"));
        String region = (regionName == null || regionName.isBlank()) ? service.getServiceRegion() : regionName;

        List<TenantServiceVolume> volumes = volumeRepository
                .findByServiceIdAndVolumeTypeNotOrderById(service.getServiceId(), "config-file");

        // 卷状态：create_status!=complete → 全 not_bound；否则查 region
        Map<String, String> statusMap = new HashMap<>();
        boolean complete = "complete".equals(service.getCreateStatus());
        if (complete) {
            statusMap = regionVolumeStatus(region, tenant, serviceAlias);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (TenantServiceVolume v : volumes) {
            Map<String, Object> vo = v.toDict();
            String st = NOT_BOUND;
            if (complete && READY.equals(statusMap.get(v.getVolumeName()))) {
                st = BOUND;
            }
            vo.put("status", st);
            vo.put("dep_services", null); // 单组件无挂载依赖；多组件挂载依赖 defer
            out.add(vo);
        }
        if (!out.isEmpty()) {
            out.get(0).put("first", true);
        }
        return out;
    }

    /** region /v2/tenants/{region_tenant_name}/services/{alias}/volumes → body.list volume_name→status。 */
    @SuppressWarnings("unchecked")
    private Map<String, String> regionVolumeStatus(String region, Tenants tenant, String serviceAlias) {
        Map<String, String> map = new HashMap<>();
        try {
            String regionTenantName = tenantRegionInfoRepository
                    .findByTenantIdAndRegionName(tenant.getTenantId(), region)
                    .map(TenantRegionInfo::getRegionTenantName).orElse(tenant.getTenantName());
            String path = "/v2/tenants/" + regionTenantName + "/services/" + serviceAlias
                    + "/volumes?enterprise_id=" + enc(tenant.getEnterpriseId());
            String body = regionClient.exchange(region, "GET", path, null, TimeoutTier.NORMAL, null);
            Object list = MAPPER.readValue(body, Map.class).get("list");
            if (list instanceof List<?> l) {
                for (Object o : l) {
                    Map<String, Object> vol = (Map<String, Object>) o;
                    map.put(String.valueOf(vol.get("volume_name")), String.valueOf(vol.get("status")));
                }
            }
        } catch (Exception e) {
            log.warn("region volume status failed: {}/{}", tenant.getTenantName(), serviceAlias, e);
        }
        return map;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
