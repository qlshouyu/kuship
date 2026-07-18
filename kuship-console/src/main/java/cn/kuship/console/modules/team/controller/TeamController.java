package cn.kuship.console.modules.team.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.RegionScope;
import cn.kuship.console.modules.authorization.annotation.PermScope;
import cn.kuship.console.modules.authorization.annotation.RequiresPerms;
import cn.kuship.console.modules.enterprise.service.EnterpriseContextResolver;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.service.TeamContextResolver;
import cn.kuship.console.modules.team.service.TeamReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 团队读路径，路径显式 /console/... 前缀，变量保持 snake_case。 */
@RestController
public class TeamController {

    private final TeamReadService teamReadService;
    private final EnterpriseContextResolver enterpriseContextResolver;
    private final TeamContextResolver teamContextResolver;

    public TeamController(TeamReadService teamReadService,
                          EnterpriseContextResolver enterpriseContextResolver,
                          TeamContextResolver teamContextResolver) {
        this.teamReadService = teamReadService;
        this.enterpriseContextResolver = enterpriseContextResolver;
        this.teamContextResolver = teamContextResolver;
    }

    /** GET /console/enterprise/{enterprise_id}/teams */
    @GetMapping("/console/enterprise/{enterprise_id}/teams")
    public ApiResult enterpriseTeams(@PathVariable("enterprise_id") String enterpriseId,
                                     @RequestParam(value = "page", defaultValue = "1") int page,
                                     @RequestParam(value = "page_size", defaultValue = "10") int pageSize) {
        enterpriseContextResolver.requireEnterprise(enterpriseId);
        Map<String, Object> bean = teamReadService.listEnterpriseTeams(enterpriseId, page, pageSize);
        return GeneralMessage.bean(200, "success", null, bean);
    }

    /** GET /console/enterprise/{enterprise_id}/user/{user_id}/teams */
    @GetMapping("/console/enterprise/{enterprise_id}/user/{user_id}/teams")
    public ApiResult userTeams(@PathVariable("enterprise_id") String enterpriseId,
                               @PathVariable("user_id") Integer userId) {
        enterpriseContextResolver.requireEnterprise(enterpriseId);
        List<Map<String, Object>> list = teamReadService.listUserTeams(enterpriseId, userId);
        return GeneralMessage.list(200, "team query success", "查询成功", list);
    }

    /** GET /console/teams/{team_name}/overview?region_name=... （region 作用域，缺 region_name → 400；需团队 describe 200001） */
    @GetMapping("/console/teams/{team_name}/overview")
    @RequiresPerms(kind = PermScope.TEAM, codes = {200001})
    public ApiResult teamOverview(@PathVariable("team_name") String teamName,
                                  @RequestParam(value = "region_name", required = false) String regionName) {
        RegionScope.require(regionName);
        Tenants team = teamContextResolver.requireTeam(teamName);
        Map<String, Object> bean = teamReadService.teamOverview(team, regionName);
        return GeneralMessage.bean(200, "success", "查询成功", bean);
    }
}

