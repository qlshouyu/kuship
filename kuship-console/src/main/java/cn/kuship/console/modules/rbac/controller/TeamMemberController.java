package cn.kuship.console.modules.rbac.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.authorization.annotation.PermScope;
import cn.kuship.console.modules.authorization.annotation.RequiresPerms;
import cn.kuship.console.modules.rbac.service.TeamMemberRoleService;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.service.TeamContextResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 团队成员角色管理（对齐 rainbond TeamUserView / TeamUsersRolesLView / TeamUserRolesRUDView / TeamUserPermsLView）。
 * 受 TEAM_MEMBER_PERMS 鉴权：get=610001 / put=610003 / delete=610004。
 */
@RestController
public class TeamMemberController {

    private final TeamMemberRoleService memberService;
    private final TeamContextResolver teamContextResolver;
    private final RequestContext requestContext;

    public TeamMemberController(TeamMemberRoleService memberService, TeamContextResolver teamContextResolver,
                                RequestContext requestContext) {
        this.memberService = memberService;
        this.teamContextResolver = teamContextResolver;
        this.requestContext = requestContext;
    }

    private Tenants team(String teamName) {
        return teamContextResolver.requireTeam(teamName);
    }

    @GetMapping("/console/teams/{team_name}/users")
    @RequiresPerms(kind = PermScope.TEAM, codes = {610001})
    public ApiResult listUsers(@PathVariable("team_name") String teamName,
                               @RequestParam(value = "page", defaultValue = "1") int page,
                               @RequestParam(value = "query", required = false) String query) {
        Map<String, Object> r = memberService.listUsers(team(teamName), requestContext.getCurrentUser(), query, page);
        @SuppressWarnings("unchecked")
        List<?> list = (List<?>) r.get("list");
        long total = ((Number) r.get("total")).longValue();
        return GeneralMessage.page(200, "team members query success", "查询成功", list, total);
    }

    @GetMapping("/console/teams/{team_name}/users/roles")
    @RequiresPerms(kind = PermScope.TEAM, codes = {610001})
    public ApiResult usersRoles(@PathVariable("team_name") String teamName) {
        return GeneralMessage.list(200, "success", null, memberService.getUsersRoles(team(teamName)));
    }

    @GetMapping("/console/teams/{team_name}/users/{user_id}/roles")
    @RequiresPerms(kind = PermScope.TEAM, codes = {610001})
    public ApiResult getUserRoles(@PathVariable("team_name") String teamName, @PathVariable("user_id") Integer userId) {
        return GeneralMessage.bean(200, "success", null, memberService.getUserRoles(team(teamName), userId));
    }

    @PutMapping("/console/teams/{team_name}/users/{user_id}/roles")
    @RequiresPerms(kind = PermScope.TEAM, codes = {610003})
    public ApiResult updateUserRoles(@PathVariable("team_name") String teamName, @PathVariable("user_id") Integer userId,
                                     @RequestBody Map<String, Object> body) {
        Map<String, Object> bean = memberService.updateUserRoles(team(teamName), userId, parseRoleIds(body));
        return GeneralMessage.bean(200, "success", null, bean);
    }

    @DeleteMapping("/console/teams/{team_name}/users/{user_id}/roles")
    @RequiresPerms(kind = PermScope.TEAM, codes = {610004})
    public ApiResult deleteUserRoles(@PathVariable("team_name") String teamName, @PathVariable("user_id") Integer userId) {
        return GeneralMessage.bean(200, "success", null, memberService.deleteUserRoles(team(teamName), userId));
    }

    @GetMapping("/console/teams/{team_name}/users/{user_id}/perms")
    @RequiresPerms(kind = PermScope.TEAM, codes = {610001})
    public ApiResult getUserPerms(@PathVariable("team_name") String teamName, @PathVariable("user_id") Integer userId) {
        Map<String, Object> bean = memberService.getUserPerms(team(teamName), userId, requestContext.getCurrentUser());
        return GeneralMessage.bean(200, "success", null, bean);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> parseRoleIds(Map<String, Object> body) {
        List<Integer> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        Object roles = body.get("roles");
        if (roles instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Number n) {
                    out.add(n.intValue());
                } else if (o != null) {
                    out.add(Integer.valueOf(o.toString()));
                }
            }
        }
        return out;
    }
}
