package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.app.support.StatusTranslate;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 组件状态读（对齐 AppStatusView.get → app_service.get_service_status）。
 * region 取实时 cur_status/start_time/vm_restore，console 侧按 status_map 计算动作策略；region 异常 → unKnow。
 */
@Service
public class ComponentStatusService {

    private static final Logger log = LoggerFactory.getLogger(ComponentStatusService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TenantServiceInfoRepository serviceRepository;
    private final TenantsRepository tenantsRepository;
    private final TenantRegionInfoRepository tenantRegionInfoRepository;
    private final RegionClient regionClient;

    public ComponentStatusService(TenantServiceInfoRepository serviceRepository,
                                  TenantsRepository tenantsRepository,
                                  TenantRegionInfoRepository tenantRegionInfoRepository,
                                  RegionClient regionClient) {
        this.serviceRepository = serviceRepository;
        this.tenantsRepository = tenantsRepository;
        this.tenantRegionInfoRepository = tenantRegionInfoRepository;
        this.regionClient = regionClient;
    }

    /** 组件状态 bean：check_uuid + (status,status_cn,disabledAction,activeAction) + start_time + vm_restore。 */
    public Map<String, Object> getServiceStatus(String tenantName, String serviceAlias, String regionName) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        Tenants tenant = tenantsRepository.findByTenantName(tenantName)
                .orElseThrow(() -> ServiceHandleException.notFound("team not found", "团队不存在"));
        String region = (regionName == null || regionName.isBlank()) ? service.getServiceRegion() : regionName;
        String regionTenantName = tenantRegionInfoRepository
                .findByTenantIdAndRegionName(tenant.getTenantId(), region)
                .map(TenantRegionInfo::getRegionTenantName)
                .orElse(tenant.getTenantName());

        String status = "unKnow";
        String startTime = "";
        Object vmRestore = new LinkedHashMap<>();
        try {
            String path = "/v2/tenants/" + regionTenantName + "/services/" + serviceAlias
                    + "/status?enterprise_id=" + enc(tenant.getEnterpriseId());
            String body = regionClient.exchange(region, "GET", path, null, TimeoutTier.NORMAL, null);
            @SuppressWarnings("unchecked")
            Map<String, Object> bean = (Map<String, Object>) MAPPER.readValue(body, Map.class).get("bean");
            if (bean != null) {
                status = String.valueOf(bean.getOrDefault("cur_status", "unKnow"));
                Object st = bean.get("start_time");
                startTime = st == null ? "" : st.toString();
                Object vm = bean.get("vm_restore");
                vmRestore = vm == null ? new LinkedHashMap<>() : vm;
            }
        } catch (Exception e) {
            // 对齐 get_service_status 异常分支：status=unKnow, vm_restore={}
            log.warn("check_service_status failed: {}/{}", tenantName, serviceAlias, e);
            status = "unKnow";
            vmRestore = new LinkedHashMap<>();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("check_uuid", service.getCheckUuid() == null ? "" : service.getCheckUuid());
        out.putAll(StatusTranslate.getStatusInfoMap(status));
        out.put("start_time", startTime);
        out.put("vm_restore", vmRestore);
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
