package cn.kuship.console.modules.team.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.team.service.TeamLifecycleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 团队生命周期（对齐 rainbond AddTeamView / TeamDelView，无 region 绑定路径）。
 * add-teams 为企业级（无团队权限码门槛）；delete 无 perms 标签（对齐 rainbond 路由）。
 */
@RestController
public class TeamLifecycleController {

    private final TeamLifecycleService lifecycleService;
    private final RequestContext requestContext;

    public TeamLifecycleController(TeamLifecycleService lifecycleService, RequestContext requestContext) {
        this.lifecycleService = lifecycleService;
        this.requestContext = requestContext;
    }

    /** POST /console/teams/add-teams body {team_alias, namespace, logo?, useable_regions?(本轮不绑定)} */
    @PostMapping("/console/teams/add-teams")
    public ApiResult addTeam(@RequestBody Map<String, Object> body) {
        UserInfo user = requestContext.getCurrentUser();
        String alias = str(body, "team_alias");
        String namespace = str(body, "namespace");
        String logo = str(body, "logo");
        Map<String, Object> bean = lifecycleService.createTeam(
                user.getUserId(), user.getEnterpriseId(), alias, namespace, logo);
        return GeneralMessage.bean(200, "success", "团队添加成功", bean);
    }

    /** DELETE /console/teams/{team_name}/delete —— 删除团队（无 region 路径）。 */
    @DeleteMapping("/console/teams/{team_name}/delete")
    public ApiResult deleteTeam(@PathVariable("team_name") String teamName) {
        lifecycleService.deleteTeam(teamName, requestContext.getCurrentUser().getEnterpriseId());
        return GeneralMessage.message(200, "delete a tenant successfully", "删除团队成功");
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
