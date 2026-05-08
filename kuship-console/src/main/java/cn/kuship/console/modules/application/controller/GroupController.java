package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.dto.CreateGroupReq;
import cn.kuship.console.modules.application.dto.UpdateGroupReq;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.application.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /console/teams/{team_name}/groups}：应用主体 CRUD + governance + status。 */
@RestController
@RequestMapping("/console/teams/{team_name}/groups")
public class GroupController {

    private final GroupService groupService;
    private final TenantServiceRepository serviceRepo;

    public GroupController(GroupService groupService, TenantServiceRepository serviceRepo) {
        this.groupService = groupService;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult list(@PathVariable("team_name") String teamName) {
        return GeneralMessage.okList(groupService.listByTeam(teamName).stream().map(this::serialize).toList());
    }

    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult create(@PathVariable("team_name") String teamName,
                              @RequestBody @Valid CreateGroupReq req) {
        return GeneralMessage.ok(serialize(groupService.create(teamName, req)));
    }

    @GetMapping(value = {"/{app_id}", "/{app_id}/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult get(@PathVariable("team_name") String teamName,
                          @PathVariable("app_id") Integer appId) {
        return GeneralMessage.ok(serialize(groupService.get(appId)));
    }

    @PutMapping(value = {"/{app_id}", "/{app_id}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult update(@PathVariable("team_name") String teamName,
                              @PathVariable("app_id") Integer appId,
                              @RequestBody @Valid UpdateGroupReq req) {
        return GeneralMessage.ok(serialize(groupService.update(appId, req)));
    }

    @DeleteMapping(value = {"/{app_id}", "/{app_id}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult delete(@PathVariable("team_name") String teamName,
                              @PathVariable("app_id") Integer appId) {
        groupService.delete(appId);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/{app_id}/status", "/{app_id}/status/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult status(@PathVariable("team_name") String teamName,
                              @PathVariable("app_id") Integer appId) {
        // 简版：聚合所有组件的本地 create_status；运行状态等 app-runtime change 实现
        List<ServiceGroupRelation> relations = groupService.componentRelations(appId);
        List<String> serviceIds = relations.stream().map(ServiceGroupRelation::getServiceId).toList();
        List<TenantService> services = serviceIds.isEmpty() ? List.of() : serviceRepo.findByServiceIdIn(serviceIds);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("app_id", appId);
        bean.put("component_count", services.size());
        bean.put("create_status_summary",
                services.stream().map(TenantService::getCreateStatus).filter(java.util.Objects::nonNull).distinct().toList());
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/{app_id}/component_names", "/{app_id}/component_names/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult componentNames(@PathVariable("team_name") String teamName,
                                      @PathVariable("app_id") Integer appId) {
        List<ServiceGroupRelation> relations = groupService.componentRelations(appId);
        List<String> serviceIds = relations.stream().map(ServiceGroupRelation::getServiceId).toList();
        List<Map<String, Object>> rows = (serviceIds.isEmpty() ? List.<TenantService>of()
                : serviceRepo.findByServiceIdIn(serviceIds))
                .stream().map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("service_id", s.getServiceId());
                    m.put("service_alias", s.getServiceAlias());
                    m.put("service_cname", s.getServiceCname());
                    m.put("k8s_component_name", s.getK8sComponentName());
                    return m;
                }).toList();
        return GeneralMessage.okList(rows);
    }

    @GetMapping(value = {"/{app_id}/governancemode", "/{app_id}/governancemode/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult getGovernance(@PathVariable("team_name") String teamName,
                                     @PathVariable("app_id") Integer appId) {
        return GeneralMessage.ok(Map.of("governance_mode", groupService.governanceMode(appId)));
    }

    @PutMapping(value = {"/{app_id}/governancemode", "/{app_id}/governancemode/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult setGovernance(@PathVariable("team_name") String teamName,
                                     @PathVariable("app_id") Integer appId,
                                     @RequestBody Map<String, String> body) {
        String mode = body.get("governance_mode");
        return GeneralMessage.ok(serialize(groupService.setGovernanceMode(appId, mode)));
    }

    private Map<String, Object> serialize(ServiceGroup g) {
        Map<String, Object> m = new LinkedHashMap<>();
        // app_id / group_id 同值兼容：rainbond-ui 历史在 addGroup callback 里读 group_id
        // (CreateComponentModal::handleMarketAppSubmit 用 res.group_id 决定是否调 installApp)，
        // 而其它入口/列表项又读 app_id。两个字段都返同一个 PK 值。
        m.put("app_id", g.getId());
        m.put("group_id", g.getId());
        m.put("ID", g.getId());
        m.put("group_name", g.getGroupName());
        m.put("note", g.getNote());
        m.put("region_name", g.getRegionName());
        m.put("tenant_id", g.getTenantId());
        m.put("k8s_app", g.getK8sApp());
        m.put("governance_mode", g.getGovernanceMode());
        m.put("app_type", g.getAppType());
        m.put("logo", g.getLogo());
        m.put("create_time", g.getCreateTime());
        m.put("update_time", g.getUpdateTime());
        return m;
    }
}
