package cn.kuship.console.modules.app.service;

import cn.kuship.console.modules.app.entity.ServiceGroup;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用（ServiceGroup）读（对齐 rainbond TenantGroupView.get + list_tenant_group_on_region）。
 */
@Service
public class AppReadService {

    private final ServiceGroupRepository serviceGroupRepository;

    public AppReadService(ServiceGroupRepository serviceGroupRepository) {
        this.serviceGroupRepository = serviceGroupRepository;
    }

    /** 团队某集群下的应用列表，每项 {group_name, group_id, group_note}。 */
    public List<Map<String, Object>> listApps(String tenantId, String regionName) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ServiceGroup g : serviceGroupRepository
                .findByTenantIdAndRegionNameOrderByUpdateTimeDescOrderIndexDesc(tenantId, regionName)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("group_name", g.getGroupName());
            m.put("group_id", g.getId());
            m.put("group_note", g.getNote());
            out.add(m);
        }
        return out;
    }
}
