package cn.kuship.console.modules.rbac.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.authorization.annotation.PermScope;
import cn.kuship.console.modules.authorization.annotation.RequiresPerms;
import cn.kuship.console.modules.rbac.service.TeamRoleWriteService;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.service.TeamContextResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 团队角色管理写（对齐 rainbond perms.py 的 TeamRoles*View）。
 * 受 TEAM_ROLE_PERMS 鉴权：get=630001 / post=630002 / put=630003 / delete=630004（P1-c 拦截器据 @RequiresPerms 校验）。
 * RUD/perms 接口 7070 继承 RegionTenantHeaderView 需 region_name；角色功能不依赖 region，kuship 接收即忽略。
 */
@RestController
public class TeamRoleController {

    private final TeamRoleWriteService roleService;
    private final TeamContextResolver teamContextResolver;

    public TeamRoleController(TeamRoleWriteService roleService, TeamContextResolver teamContextResolver) {
        this.roleService = roleService;
        this.teamContextResolver = teamContextResolver;
    }

    private String tenantId(String teamName) {
        Tenants team = teamContextResolver.requireTeam(teamName);
        return team.getTenantId();
    }

    // ---- 角色 CRUD ----

    @GetMapping("/console/teams/{team_name}/roles")
    @RequiresPerms(kind = PermScope.TEAM, codes = {630001})
    public ApiResult listRoles(@PathVariable("team_name") String teamName) {
        return GeneralMessage.list(200, "success", null, roleService.listRoles(tenantId(teamName)));
    }

    @PostMapping("/console/teams/{team_name}/roles")
    @RequiresPerms(kind = PermScope.TEAM, codes = {630002})
    public ApiResult createRole(@PathVariable("team_name") String teamName, @RequestBody Map<String, Object> body) {
        String name = body == null ? null : (String) body.get("name");
        Map<String, Object> bean = roleService.createRole(tenantId(teamName), name);
        return GeneralMessage.bean(200, "success", "创建角色成功", bean);
    }

    @GetMapping("/console/teams/{team_name}/roles/{role_id}")
    @RequiresPerms(kind = PermScope.TEAM, codes = {630001})
    public ApiResult getRole(@PathVariable("team_name") String teamName, @PathVariable("role_id") Integer roleId) {
        return GeneralMessage.bean(200, "success", null, roleService.getRole(tenantId(teamName), roleId));
    }

    @PutMapping("/console/teams/{team_name}/roles/{role_id}")
    @RequiresPerms(kind = PermScope.TEAM, codes = {630003})
    public ApiResult updateRole(@PathVariable("team_name") String teamName, @PathVariable("role_id") Integer roleId,
                                @RequestBody Map<String, Object> body) {
        String name = body == null ? null : (String) body.get("name");
        Map<String, Object> bean = roleService.updateRole(tenantId(teamName), roleId, name);
        return GeneralMessage.bean(200, "success", "更新角色成功", bean);
    }

    @DeleteMapping("/console/teams/{team_name}/roles/{role_id}")
    @RequiresPerms(kind = PermScope.TEAM, codes = {630004})
    public ApiResult deleteRole(@PathVariable("team_name") String teamName, @PathVariable("role_id") Integer roleId) {
        roleService.deleteRole(tenantId(teamName), roleId);
        return GeneralMessage.message(200, "success", "删除角色成功");
    }

    // ---- 角色权限树 ----

    @GetMapping("/console/teams/{team_name}/roles/perms")
    @RequiresPerms(kind = PermScope.TEAM, codes = {630001})
    public ApiResult listRolesPerms(@PathVariable("team_name") String teamName) {
        List<Map<String, Object>> list = roleService.listRolesPerms(tenantId(teamName));
        return GeneralMessage.list(200, "success", null, list);
    }

    @GetMapping("/console/teams/{team_name}/roles/{role_id}/perms")
    @RequiresPerms(kind = PermScope.TEAM, codes = {630001})
    public ApiResult getRolePerms(@PathVariable("team_name") String teamName, @PathVariable("role_id") Integer roleId) {
        return GeneralMessage.bean(200, "success", null, roleService.getRolePerms(tenantId(teamName), roleId));
    }

    @PutMapping("/console/teams/{team_name}/roles/{role_id}/perms")
    @RequiresPerms(kind = PermScope.TEAM, codes = {630003})
    @SuppressWarnings("unchecked")
    public ApiResult updateRolePerms(@PathVariable("team_name") String teamName, @PathVariable("role_id") Integer roleId,
                                     @RequestBody Map<String, Object> body) {
        Map<String, Object> permsTree = body == null ? Map.of() : (Map<String, Object>) body.get("permissions");
        Map<String, Object> bean = roleService.updateRolePerms(tenantId(teamName), roleId, permsTree);
        return GeneralMessage.bean(200, "success", null, bean);
    }
}
