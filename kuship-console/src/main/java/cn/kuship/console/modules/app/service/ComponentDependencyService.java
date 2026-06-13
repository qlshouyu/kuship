package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.ServiceGroup;
import cn.kuship.console.modules.app.entity.ServiceGroupRelation;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.entity.TenantServiceRelation;
import cn.kuship.console.modules.app.entity.TenantServicesPort;
import cn.kuship.console.modules.app.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.app.repository.TenantServiceRelationRepository;
import cn.kuship.console.modules.app.repository.TenantServicesPortRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件依赖读（对齐 AppDependencyView.get 正向 / AppDependencyViewList.get 反向）。
 * dep 项按本应用(current_group_id)在前、其他应用在后；分页(默认 25)；bean 含本组件 port_list+total。
 */
@Service
public class ComponentDependencyService {

    private final TenantServiceInfoRepository serviceRepository;
    private final TenantServiceRelationRepository relationRepository;
    private final TenantServicesPortRepository portRepository;
    private final ServiceGroupRelationRepository groupRelationRepository;
    private final ServiceGroupRepository groupRepository;

    public ComponentDependencyService(TenantServiceInfoRepository serviceRepository,
                                      TenantServiceRelationRepository relationRepository,
                                      TenantServicesPortRepository portRepository,
                                      ServiceGroupRelationRepository groupRelationRepository,
                                      ServiceGroupRepository groupRepository) {
        this.serviceRepository = serviceRepository;
        this.relationRepository = relationRepository;
        this.portRepository = portRepository;
        this.groupRelationRepository = groupRelationRepository;
        this.groupRepository = groupRepository;
    }

    public record Result(Map<String, Object> bean, List<Map<String, Object>> list, int total) {
    }

    /** 正向：本组件依赖的组件（/dependency）。bean={port_list, total}。 */
    public Result forward(String serviceAlias, int page, int pageSize) {
        TenantServiceInfo service = service(serviceAlias);
        List<String> depIds = relationRepository
                .findByTenantIdAndServiceId(service.getTenantId(), service.getServiceId())
                .stream().map(TenantServiceRelation::getDepServiceId).toList();
        return build(service, depIds, page, pageSize, false);
    }

    /** 反向：依赖本组件的组件（/dependency-list）。bean={service_id, port_list, total}。 */
    public Result reverse(String serviceAlias, int page, int pageSize) {
        TenantServiceInfo service = service(serviceAlias);
        List<String> depIds = relationRepository
                .findByTenantIdAndDepServiceId(service.getTenantId(), service.getServiceId())
                .stream().map(TenantServiceRelation::getServiceId).toList();
        return build(service, depIds, page, pageSize, true);
    }

    private TenantServiceInfo service(String serviceAlias) {
        return serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
    }

    private Result build(TenantServiceInfo service, List<String> depIds, int page, int pageSize, boolean withServiceId) {
        int pageNum = Math.max(page, 1);
        int currentGroupId = groupRelationRepository.findByServiceId(service.getServiceId())
                .map(ServiceGroupRelation::getGroupId).orElse(-1);

        List<TenantServiceInfo> deps = depIds.isEmpty() ? List.of() : serviceRepository.findByServiceIdIn(depIds);
        List<Map<String, Object>> currentApp = new ArrayList<>();
        List<Map<String, Object>> otherApp = new ArrayList<>();
        for (TenantServiceInfo dep : deps) {
            int[] gid = {-1};
            String groupName = groupName(dep.getServiceId(), gid);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("service_cname", dep.getServiceCname());
            info.put("service_id", dep.getServiceId());
            info.put("service_type", dep.getServiceType());
            info.put("service_alias", dep.getServiceAlias());
            info.put("group_name", groupName);
            info.put("group_id", gid[0]);
            info.put("ports_list", portList(dep));
            if (gid[0] == currentGroupId) {
                currentApp.add(info);
            } else {
                otherApp.add(info);
            }
        }
        List<Map<String, Object>> depList = new ArrayList<>(currentApp);
        depList.addAll(otherApp);

        // 对齐 rainbond 分页（含 start>=len 时的 clamp）
        int start = (pageNum - 1) * pageSize;
        int end = pageNum * pageSize;
        if (start >= depList.size()) {
            start = depList.size() - 1;
            end = depList.size() - 1;
        }
        List<Map<String, Object>> rt = start < 0 ? List.of() : depList.subList(Math.max(start, 0), Math.min(Math.max(end, 0), depList.size()));

        Map<String, Object> bean = new LinkedHashMap<>();
        if (withServiceId) {
            bean.put("service_id", service.getServiceId());
        }
        bean.put("port_list", portList(service));
        bean.put("total", depList.size());
        return new Result(bean, new ArrayList<>(rt), depList.size());
    }

    private List<Integer> portList(TenantServiceInfo svc) {
        List<Integer> ports = new ArrayList<>();
        for (TenantServicesPort p : portRepository.findByTenantIdAndServiceIdOrderById(svc.getTenantId(), svc.getServiceId())) {
            ports.add(p.getContainerPort());
        }
        return ports;
    }

    private String groupName(String serviceId, int[] gidOut) {
        ServiceGroupRelation rel = groupRelationRepository.findByServiceId(serviceId).orElse(null);
        if (rel != null && rel.getGroupId() != null) {
            gidOut[0] = rel.getGroupId();
            return groupRepository.findById(rel.getGroupId()).map(ServiceGroup::getGroupName).orElse("");
        }
        gidOut[0] = -1;
        return "未分组";
    }
}
