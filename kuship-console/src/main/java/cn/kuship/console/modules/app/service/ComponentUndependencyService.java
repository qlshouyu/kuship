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
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件“可依赖但未依赖”的组件列表读（对齐 AppNotDependencyView.get + get_undependencies）。
 * team region 组件(排除自己 + 须有 inner_service 端口 + 排除已正向依赖)→dep_info(6 字段)；
 * search/condition 过滤(condition 非法且有候选→400)；本应用在前/其他在后；分页。
 */
@Service
public class ComponentUndependencyService {

    private final TenantServiceInfoRepository serviceRepository;
    private final TenantsRepository tenantsRepository;
    private final TenantServiceRelationRepository relationRepository;
    private final TenantServicesPortRepository portRepository;
    private final ServiceGroupRelationRepository groupRelationRepository;
    private final ServiceGroupRepository groupRepository;

    public ComponentUndependencyService(TenantServiceInfoRepository serviceRepository,
                                        TenantsRepository tenantsRepository,
                                        TenantServiceRelationRepository relationRepository,
                                        TenantServicesPortRepository portRepository,
                                        ServiceGroupRelationRepository groupRelationRepository,
                                        ServiceGroupRepository groupRepository) {
        this.serviceRepository = serviceRepository;
        this.tenantsRepository = tenantsRepository;
        this.relationRepository = relationRepository;
        this.portRepository = portRepository;
        this.groupRelationRepository = groupRelationRepository;
        this.groupRepository = groupRepository;
    }

    public record Result(List<Map<String, Object>> list, int total) {
    }

    public Result list(String tenantName, String serviceAlias, int page, int pageSize, String searchKey, String condition) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        Tenants tenant = tenantsRepository.findByTenantName(tenantName)
                .orElseThrow(() -> ServiceHandleException.notFound("team not found", "团队不存在"));

        // 本组件已正向依赖的 dep_service_id
        List<String> depIds = relationRepository
                .findByTenantIdAndServiceId(tenant.getTenantId(), service.getServiceId())
                .stream().map(TenantServiceRelation::getDepServiceId).toList();

        int currentGroupId = groupRelationRepository.findByServiceId(service.getServiceId())
                .map(ServiceGroupRelation::getGroupId).orElse(-1);

        List<Map<String, Object>> currentApp = new ArrayList<>();
        List<Map<String, Object>> otherApp = new ArrayList<>();
        for (TenantServiceInfo s : serviceRepository.findByTenantIdAndServiceRegion(
                tenant.getTenantId(), service.getServiceRegion())) {
            if (s.getServiceId().equals(service.getServiceId()) || depIds.contains(s.getServiceId())) {
                continue;
            }
            // 必须打开对内端口才能被依赖
            boolean hasInner = portRepository
                    .findByTenantIdAndServiceIdOrderById(tenant.getTenantId(), s.getServiceId())
                    .stream().anyMatch(TenantServicesPort::isInnerService);
            if (!hasInner) {
                continue;
            }
            int[] gid = {-1};
            String groupName = groupName(s.getServiceId(), gid);
            if (!matchSearch(searchKey, condition, groupName, s.getServiceCname())) {
                continue;
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("service_cname", s.getServiceCname());
            info.put("service_id", s.getServiceId());
            info.put("service_type", s.getServiceType());
            info.put("service_alias", s.getServiceAlias());
            info.put("group_name", groupName);
            info.put("group_id", gid[0]);
            if (gid[0] == currentGroupId) {
                currentApp.add(info);
            } else {
                otherApp.add(info);
            }
        }
        List<Map<String, Object>> all = new ArrayList<>(currentApp);
        all.addAll(otherApp);

        int from = Math.min((page - 1) * pageSize, all.size());
        int to = Math.min(page * pageSize, all.size());
        List<Map<String, Object>> rt = from < 0 ? List.of() : new ArrayList<>(all.subList(Math.max(from, 0), to));
        return new Result(rt, all.size());
    }

    /** 对齐 view：condition 非法(且本次有候选被处理)→400 condition参数错误。 */
    private boolean matchSearch(String searchKey, String condition, String groupName, String serviceCname) {
        String gn = groupName == null ? "" : groupName.toLowerCase();
        String cn = serviceCname == null ? "" : serviceCname.toLowerCase();
        if (searchKey != null && condition != null && !condition.isEmpty()) {
            String k = searchKey.toLowerCase();
            if ("group_name".equals(condition)) {
                return gn.contains(k);
            }
            if ("service_name".equals(condition)) {
                return cn.contains(k);
            }
            throw new ServiceHandleException(400, "error", "condition参数错误");
        } else if (searchKey != null) {
            String k = searchKey.toLowerCase();
            return gn.contains(k) || cn.contains(k);
        }
        return true;
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
