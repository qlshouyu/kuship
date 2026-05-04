package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceStatusOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/** 应用拓扑：返回 services 列表 + 状态 + extend_method / icon / cname enrich。 */
@RestController
@RequestMapping("/console/teams/{team_name}/groups/{group_id}")
public class AppTopologyController {

    private final ServiceGroupRepository groupRepo;
    private final ServiceGroupRelationRepository relationRepo;
    private final TenantServiceRepository serviceRepo;
    private final ServiceStatusOperations status;
    private final RuntimeContextLoader loader;

    public AppTopologyController(ServiceGroupRepository groupRepo,
                                   ServiceGroupRelationRepository relationRepo,
                                   TenantServiceRepository serviceRepo,
                                   ServiceStatusOperations status,
                                   RuntimeContextLoader loader) {
        this.groupRepo = groupRepo;
        this.relationRepo = relationRepo;
        this.serviceRepo = serviceRepo;
        this.status = status;
        this.loader = loader;
    }

    @GetMapping(value = {"/topological", "/topological/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult topological(@PathVariable("team_name") String teamName,
                                   @PathVariable("group_id") Integer groupId) {
        loader.requireTeam(teamName);
        ServiceGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ServiceHandleException(404, "group not found", "应用不存在"));
        List<ServiceGroupRelation> rels = relationRepo.findByGroupId(groupId);
        List<String> serviceIds = rels.stream().map(ServiceGroupRelation::getServiceId).toList();
        List<TenantService> services = serviceIds.isEmpty() ? List.of()
                : serviceRepo.findByServiceIdIn(serviceIds);

        List<Map<String, Object>> nodes = services.stream().map(s -> {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("service_id", s.getServiceId());
            n.put("service_alias", s.getServiceAlias());
            n.put("service_cname", s.getServiceCname());
            n.put("extend_method", s.getExtendMethod());
            n.put("service_region", s.getServiceRegion());
            return n;
        }).collect(Collectors.toList());

        // 调 region 拿状态汇总
        Map<String, Object> regionStatus = Map.of();
        if (!serviceIds.isEmpty()) {
            String region = group.getRegionName() != null ? group.getRegionName() : services.get(0).getServiceRegion();
            try {
                regionStatus = status.serviceStatus(region, teamName, Map.of("service_ids", String.join(",", serviceIds)));
            } catch (Exception ignore) {
                // region 异常不致拓扑失败，继续返回本地节点
            }
        }
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("group_id", groupId);
        bean.put("group_name", group.getGroupName());
        bean.put("services", nodes);
        bean.put("region_status", regionStatus);
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/topological/internet", "/topological/internet/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult topologicalInternet(@PathVariable("team_name") String teamName,
                                            @PathVariable("group_id") Integer groupId) {
        loader.requireTeam(teamName);
        groupRepo.findById(groupId)
                .orElseThrow(() -> new ServiceHandleException(404, "group not found", "应用不存在"));
        // 简化：仅返回有外部端口的 service 列表（依赖 ports 子资源；本接口在没有公开端口表查询时返回空）
        return GeneralMessage.okList(List.of());
    }
}
