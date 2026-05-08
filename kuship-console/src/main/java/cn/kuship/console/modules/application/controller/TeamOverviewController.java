package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /console/teams/{team_name}/overview/*}：团队概览（应用 + 组件聚合）。
 *
 * <p>对齐 rainbond {@code ServiceGroupView}（{@code views/public_areas.py:195}）。
 */
@RestController
@RequestMapping("/console/teams/{team_name}/overview")
public class TeamOverviewController {

    private final TenantsRepository tenantsRepo;
    private final ServiceGroupRepository groupRepo;
    private final TenantServiceRepository serviceRepo;
    private final ServiceGroupRelationRepository relationRepo;

    public TeamOverviewController(TenantsRepository tenantsRepo,
                                  ServiceGroupRepository groupRepo,
                                  TenantServiceRepository serviceRepo,
                                  ServiceGroupRelationRepository relationRepo) {
        this.tenantsRepo = tenantsRepo;
        this.groupRepo = groupRepo;
        this.serviceRepo = serviceRepo;
        this.relationRepo = relationRepo;
    }

    @GetMapping(value = {"/groups", "/groups/"})
    public ApiResult overviewGroups(@PathVariable("team_name") String teamName,
                                     @RequestParam(name = "region_name", required = false) String regionName,
                                     @RequestParam(name = "query", required = false) String query,
                                     @RequestParam(name = "app_type", required = false) String appType) {
        Tenants tenant = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));

        List<ServiceGroup> groups = (regionName == null || regionName.isBlank())
                ? groupRepo.findByTenantId(tenant.getTenantId())
                : groupRepo.findByTenantIdAndRegionName(tenant.getTenantId(), regionName);

        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase();
            groups = groups.stream()
                    .filter(g -> g.getGroupName() != null && g.getGroupName().toLowerCase().contains(q))
                    .toList();
        }
        if (appType != null && !appType.isBlank()) {
            groups = groups.stream()
                    .filter(g -> appType.equals(g.getAppType()))
                    .toList();
        }
        if (groups.isEmpty()) {
            return GeneralMessage.okList(List.of());
        }

        List<TenantService> services = (regionName == null || regionName.isBlank())
                ? serviceRepo.findByTenantId(tenant.getTenantId())
                : serviceRepo.findByServiceRegionAndTenantId(regionName, tenant.getTenantId());
        Map<String, TenantService> serviceById = services.stream()
                .collect(Collectors.toMap(TenantService::getServiceId, s -> s, (a, b) -> a));

        List<Integer> groupIds = groups.stream().map(ServiceGroup::getId).toList();
        List<ServiceGroupRelation> relations = relationRepo.findByGroupIdIn(groupIds);

        Map<Integer, List<Map<String, Object>>> groupServiceMap = new HashMap<>();
        for (ServiceGroupRelation r : relations) {
            TenantService s = serviceById.get(r.getServiceId());
            if (s == null) continue;
            Map<String, Object> svc = new LinkedHashMap<>();
            svc.put("service_id", s.getServiceId());
            svc.put("service_cname", s.getServiceCname());
            svc.put("service_alias", s.getServiceAlias());
            groupServiceMap.computeIfAbsent(r.getGroupId(), k -> new ArrayList<>()).add(svc);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ServiceGroup g : groups) {
            Map<String, Object> bean = new LinkedHashMap<>();
            bean.put("group_id", g.getId());
            bean.put("group_name", g.getGroupName());
            bean.put("service_list", groupServiceMap.get(g.getId()));
            result.add(bean);
        }
        // rainbond 用 result.insert(0, ...) 等价倒序输出
        Collections.reverse(result);
        return GeneralMessage.okList(result);
    }
}
