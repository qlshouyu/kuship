package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.appruntime.api.MonitorOperations;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 监控端点：query / query_range / batch_query / resource。 */
@RestController
public class AppMonitorController {

    private final MonitorOperations monitor;
    private final RuntimeContextLoader loader;
    private final ServiceGroupRepository groupRepo;
    private final ServiceGroupRelationRepository relationRepo;
    private final TenantServiceRepository serviceRepo;

    public AppMonitorController(MonitorOperations monitor,
                                  RuntimeContextLoader loader,
                                  ServiceGroupRepository groupRepo,
                                  ServiceGroupRelationRepository relationRepo,
                                  TenantServiceRepository serviceRepo) {
        this.monitor = monitor;
        this.loader = loader;
        this.groupRepo = groupRepo;
        this.relationRepo = relationRepo;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/monitor/query",
                          "/console/teams/{team_name}/apps/{service_alias}/monitor/query/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult query(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String alias,
                              @RequestParam Map<String, String> queryParams) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = monitor.query(s.getServiceRegion(), teamName, queryParams);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/monitor/query_range",
                          "/console/teams/{team_name}/apps/{service_alias}/monitor/query_range/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult queryRange(@PathVariable("team_name") String teamName,
                                   @PathVariable("service_alias") String alias,
                                   @RequestParam Map<String, String> queryParams) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = monitor.queryRange(s.getServiceRegion(), teamName, queryParams);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/groups/{group_id}/monitor/batch_query",
                          "/console/teams/{team_name}/groups/{group_id}/monitor/batch_query/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult batchQuery(@PathVariable("team_name") String teamName,
                                   @PathVariable("group_id") Integer groupId,
                                   @RequestParam Map<String, String> queryParams) {
        loader.requireTeam(teamName);
        ServiceGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ServiceHandleException(404, "group not found", "应用不存在"));
        List<ServiceGroupRelation> rels = relationRepo.findByGroupId(groupId);
        List<String> serviceIds = rels.stream().map(ServiceGroupRelation::getServiceId).toList();
        String region = group.getRegionName();
        if (region == null && !serviceIds.isEmpty()) {
            region = serviceRepo.findByServiceIdIn(serviceIds).get(0).getServiceRegion();
        }
        Map<String, String> q = new LinkedHashMap<>(queryParams);
        q.put("service_ids", String.join(",", serviceIds));
        Map<String, Object> resp = monitor.batchQuery(region != null ? region : "default", teamName, q);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/resource",
                          "/console/teams/{team_name}/apps/{service_alias}/resource/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult resource(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String alias) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = monitor.getServiceResources(s.getServiceRegion(), teamName, alias);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }
}
