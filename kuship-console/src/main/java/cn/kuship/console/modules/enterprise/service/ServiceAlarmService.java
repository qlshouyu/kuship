package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import cn.kuship.console.modules.app.entity.ServiceGroup;
import cn.kuship.console.modules.app.entity.ServiceGroupRelation;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业异常组件告警列表（对齐 rainbond ServiceAlarm.get）。
 * 企业无团队 → 空列表；否则遍历可用集群取异常组件 id（/v2/enterprise/{eid}/abnormal_status），再由 DB 富化。
 * 注：本环境 region 不可达，异常 id 取空 → 列表为空（与 7070 无异常环境出参一致）。
 */
@Service
public class ServiceAlarmService {

    private static final Logger log = LoggerFactory.getLogger(ServiceAlarmService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TenantsRepository tenantsRepository;
    private final RegionConfigRepository regionConfigRepository;
    private final TenantServiceInfoRepository serviceInfoRepository;
    private final ServiceGroupRelationRepository serviceGroupRelationRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final RegionClient regionClient;

    public ServiceAlarmService(TenantsRepository tenantsRepository,
                               RegionConfigRepository regionConfigRepository,
                               TenantServiceInfoRepository serviceInfoRepository,
                               ServiceGroupRelationRepository serviceGroupRelationRepository,
                               ServiceGroupRepository serviceGroupRepository,
                               RegionClient regionClient) {
        this.tenantsRepository = tenantsRepository;
        this.regionConfigRepository = regionConfigRepository;
        this.serviceInfoRepository = serviceInfoRepository;
        this.serviceGroupRelationRepository = serviceGroupRelationRepository;
        this.serviceGroupRepository = serviceGroupRepository;
        this.regionClient = regionClient;
    }

    /** 异常组件列表（无团队/无异常时为空）。 */
    public List<Map<String, Object>> listServiceAlarm(String enterpriseId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (tenantsRepository.countByEnterpriseId(enterpriseId) <= 0) {
            return result;
        }
        List<RegionConfig> regions = regionConfigRepository
                .findByEnterpriseIdAndStatusOrderById(enterpriseId, "1");
        List<String> abnormalIds = new ArrayList<>();
        for (RegionConfig region : regions) {
            try {
                String body = regionClient.exchange(region.getRegionName(), "GET",
                        "/v2/enterprise/" + enterpriseId + "/abnormal_status", null,
                        TimeoutTier.LIGHT_QUERY, null);
                Object ids = MAPPER.readValue(body, Map.class).get("service_ids");
                if (ids instanceof List<?> il) {
                    for (Object o : il) {
                        abnormalIds.add(String.valueOf(o));
                    }
                }
            } catch (Exception e) {
                log.debug("get abnormal status {}: {}", region.getRegionName(), e.getMessage());
            }
        }
        if (abnormalIds.isEmpty()) {
            return result;
        }

        for (TenantServiceInfo svc : serviceInfoRepository.findByServiceIdIn(abnormalIds)) {
            Map<String, Object> item = new LinkedHashMap<>();
            ServiceGroupRelation rel = serviceGroupRelationRepository.findByServiceId(svc.getServiceId()).orElse(null);
            Integer groupId = rel == null ? null : rel.getGroupId();
            String groupName = "";
            if (groupId != null) {
                ServiceGroup group = serviceGroupRepository.findById(groupId).orElse(null);
                if (group != null) {
                    groupName = group.getGroupName();
                }
            }
            Tenants team = tenantsRepository.findByTenantId(svc.getTenantId()).orElse(null);
            item.put("service_cname", svc.getServiceCname());
            item.put("group_id", groupId);
            item.put("group_name", groupName);
            item.put("service_alias", svc.getServiceAlias());
            item.put("service_id", svc.getServiceId());
            item.put("tenant_id", svc.getTenantId());
            item.put("region_name", svc.getServiceRegion());
            item.put("tenant_name", team == null ? null : team.getTenantName());
            item.put("tenant_alias", team == null ? null : team.getTenantAlias());
            result.add(item);
        }
        return result;
    }
}
