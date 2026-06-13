package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
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
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 组件详情读（对齐 AppDetailView.get 非 vm/非 market 路径，纯 DB）。
 * bean={service: to_dict全列+group_name/group_id/disk_cap, event_websocket_url, is_third}。
 */
@Service
public class ComponentDetailService {

    private final TenantServiceInfoRepository serviceRepository;
    private final TenantsRepository tenantsRepository;
    private final ServiceGroupRelationRepository relationRepository;
    private final ServiceGroupRepository groupRepository;
    private final RegionConfigRepository regionConfigRepository;

    public ComponentDetailService(TenantServiceInfoRepository serviceRepository,
                                  TenantsRepository tenantsRepository,
                                  ServiceGroupRelationRepository relationRepository,
                                  ServiceGroupRepository groupRepository,
                                  RegionConfigRepository regionConfigRepository) {
        this.serviceRepository = serviceRepository;
        this.tenantsRepository = tenantsRepository;
        this.relationRepository = relationRepository;
        this.groupRepository = groupRepository;
        this.regionConfigRepository = regionConfigRepository;
    }

    /** @param host 请求 Host（仅 region.wsurl=="auto" 时用于拼 ws url）。 */
    public Map<String, Object> getDetail(String tenantName, String serviceAlias, String regionName, String host) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        Tenants tenant = tenantsRepository.findByTenantName(tenantName)
                .orElseThrow(() -> ServiceHandleException.notFound("team not found", "团队不存在"));
        String region = (regionName == null || regionName.isBlank()) ? service.getServiceRegion() : regionName;

        Map<String, Object> serviceModel = service.toDict();
        // 组名/组 id（对齐 get_services_group_name：无关系 → 未分组/-1）
        ServiceGroupRelation rel = relationRepository.findByServiceId(service.getServiceId()).orElse(null);
        String groupName;
        int groupId;
        if (rel != null && rel.getGroupId() != null) {
            groupId = rel.getGroupId();
            groupName = groupRepository.findById(groupId).map(ServiceGroup::getGroupName).orElse("");
        } else {
            groupName = "未分组";
            groupId = -1;
        }
        serviceModel.put("namespace", tenant.getNamespace()); // 原位覆盖
        serviceModel.put("group_name", groupName);
        serviceModel.put("group_id", groupId);
        serviceModel.put("disk_cap", "vm".equals(service.getExtendMethod()) ? 30 : 10);

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("service", serviceModel);
        bean.put("event_websocket_url", eventWebsocketUrl(region, host));
        bean.put("is_third", "third_party".equals(service.getServiceSource()));
        return bean;
    }

    /** 对齐 __event_ws：region.wsurl=="auto" → ws://{host}:6060/event_log，否则 wsurl+"/event_log"。 */
    private String eventWebsocketUrl(String regionName, String host) {
        String wsurl = regionConfigRepository.findByRegionName(regionName)
                .map(RegionConfig::getWsurl).orElse(null);
        if (wsurl == null || "auto".equals(wsurl)) {
            return "ws://" + (host == null ? "" : host) + ":6060/event_log";
        }
        return wsurl + "/event_log";
    }
}
