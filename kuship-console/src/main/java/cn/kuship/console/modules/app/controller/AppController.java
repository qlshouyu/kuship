package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.RegionScope;
import cn.kuship.console.modules.app.service.AppReadService;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.service.TeamContextResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用读（对齐 rainbond TenantGroupView）。GET groups 无权限码门槛（APP_CREATE_PERMS 的 get 所需码为空）。
 */
@RestController
public class AppController {

    private final AppReadService appReadService;
    private final TeamContextResolver teamContextResolver;

    public AppController(AppReadService appReadService, TeamContextResolver teamContextResolver) {
        this.appReadService = appReadService;
        this.teamContextResolver = teamContextResolver;
    }

    /** GET /console/teams/{team_name}/groups?region_name= —— 团队某集群下的应用列表。 */
    @GetMapping("/console/teams/{team_name}/groups")
    public ApiResult listApps(@PathVariable("team_name") String teamName,
                              @RequestParam(value = "region_name", required = false) String regionName) {
        RegionScope.require(regionName);
        Tenants team = teamContextResolver.requireTeam(teamName);
        return GeneralMessage.list(200, "success", "查询成功", appReadService.listApps(team.getTenantId(), regionName));
    }
}
