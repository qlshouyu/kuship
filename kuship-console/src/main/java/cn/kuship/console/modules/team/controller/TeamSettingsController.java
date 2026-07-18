package cn.kuship.console.modules.team.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.RegionScope;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.service.TeamContextResolver;
import cn.kuship.console.modules.team.service.TeamSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 团队设置写（对齐 rainbond UserPemTraView / TeamNameModView / TeamExitView）。
 * pemtransfer 为 owner-only（服务内校验）；modifyname/exit 无权限码门槛（团队成员/登录即可）。
 */
@RestController
public class TeamSettingsController {

    private final TeamSettingsService settingsService;
    private final TeamContextResolver teamContextResolver;
    private final RequestContext requestContext;

    public TeamSettingsController(TeamSettingsService settingsService, TeamContextResolver teamContextResolver,
                                  RequestContext requestContext) {
        this.settingsService = settingsService;
        this.teamContextResolver = teamContextResolver;
        this.requestContext = requestContext;
    }

    /** POST /console/teams/{team_name}/pemtransfer  body {user_id}  —— 移交管理权（owner-only）。 */
    @PostMapping("/console/teams/{team_name}/pemtransfer")
    public ApiResult pemTransfer(@PathVariable("team_name") String teamName,
                                 @RequestParam(value = "region_name", required = false) String regionName,
                                 @RequestBody Map<String, Object> body) {
        RegionScope.require(regionName);
        Tenants team = teamContextResolver.requireTeam(teamName);
        Integer targetUserId = toInt(body == null ? null : body.get("user_id"));
        settingsService.transferOwnership(team, requestContext.getCurrentUser().getUserId(), targetUserId);
        return GeneralMessage.message(200, "success", "移交成功");
    }

    /** POST /console/teams/{team_name}/modifyname  body {new_team_alias, new_logo?} —— 改名。 */
    @PostMapping("/console/teams/{team_name}/modifyname")
    public ApiResult modifyName(@PathVariable("team_name") String teamName,
                                @RequestParam(value = "region_name", required = false) String regionName,
                                @RequestBody Map<String, Object> body) {
        RegionScope.require(regionName);
        Tenants team = teamContextResolver.requireTeam(teamName);
        String newAlias = body == null ? null : (String) body.get("new_team_alias");
        String newLogo = body == null ? null : (String) body.get("new_logo");
        Map<String, Object> bean = settingsService.updateTenantInfo(team, newAlias, newLogo);
        return GeneralMessage.bean(200, "update success", "团队信息修改成功", bean);
    }

    /** GET /console/teams/{team_name}/exit —— 退出团队（创建者不可退）。 */
    @GetMapping("/console/teams/{team_name}/exit")
    public ApiResult exit(@PathVariable("team_name") String teamName,
                          @RequestParam(value = "region_name", required = false) String regionName) {
        RegionScope.require(regionName);
        Tenants team = teamContextResolver.requireTeam(teamName);
        settingsService.exitTeam(team, requestContext.getCurrentUser().getUserId());
        return GeneralMessage.message(200, "success", "退出团队成功");
    }

    private static Integer toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return o == null ? null : Integer.valueOf(o.toString());
    }
}
