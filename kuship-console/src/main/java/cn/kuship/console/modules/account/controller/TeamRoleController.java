package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.dto.CreateRoleReq;
import cn.kuship.console.modules.account.dto.UpdateRolePermsReq;
import cn.kuship.console.modules.account.dto.UpdateUserRolesReq;
import cn.kuship.console.modules.account.entity.PermsInfo;
import cn.kuship.console.modules.account.entity.RoleInfo;
import cn.kuship.console.modules.account.entity.RolePerms;
import cn.kuship.console.modules.account.entity.UserRole;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.PermService;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.PermsInfoRepository;
import cn.kuship.console.modules.account.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** team 角色与权限管理。 */
@RestController
@RequestMapping("/console/teams/{team_name}")
public class TeamRoleController {

    private final RoleService roleService;
    private final PermService permService;
    private final PermsInfoRepository permsInfoRepo;

    public TeamRoleController(RoleService roleService,
                                PermService permService,
                                PermsInfoRepository permsInfoRepo) {
        this.roleService = roleService;
        this.permService = permService;
        this.permsInfoRepo = permsInfoRepo;
    }

    @GetMapping(value = {"/roles", "/roles/"})
    @RequirePerm(PermCode.TEAM_ROLE_PERMS)
    public ApiResult listRoles(@PathVariable("team_name") String teamName) {
        List<Map<String, Object>> rows = roleService.listTeamRoles(teamName).stream()
                .map(this::serializeRole).toList();
        return GeneralMessage.okList(rows);
    }

    @PostMapping(value = {"/roles", "/roles/"})
    @RequirePerm(PermCode.TEAM_ROLE_PERMS)
    public ApiResult createRole(@PathVariable("team_name") String teamName,
                                  @RequestBody @Valid CreateRoleReq req) {
        RoleInfo role = roleService.createRole(teamName, req);
        return GeneralMessage.ok(serializeRole(role));
    }

    @PutMapping(value = {"/roles/{role_id}", "/roles/{role_id}/"})
    @RequirePerm(PermCode.TEAM_ROLE_PERMS)
    public ApiResult updateRole(@PathVariable("team_name") String teamName,
                                  @PathVariable("role_id") Integer roleId,
                                  @RequestParam(value = "name", required = false) String name) {
        RoleInfo role = roleService.updateRole(teamName, roleId, name);
        return GeneralMessage.ok(serializeRole(role));
    }

    @DeleteMapping(value = {"/roles/{role_id}", "/roles/{role_id}/"})
    @RequirePerm(PermCode.TEAM_ROLE_PERMS)
    public ApiResult deleteRole(@PathVariable("team_name") String teamName,
                                  @PathVariable("role_id") Integer roleId) {
        roleService.deleteRole(teamName, roleId);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/roles/perms", "/roles/perms/"})
    @RequirePerm(PermCode.TEAM_ROLE_PERMS)
    public ApiResult allRolePerms(@PathVariable("team_name") String teamName) {
        List<RoleInfo> roles = roleService.listTeamRoles(teamName);
        List<Map<String, Object>> rows = roles.stream().map(role -> {
            List<RolePerms> perms = roleService.rolePerms(role.getId());
            Map<String, Object> m = serializeRole(role);
            m.put("perms", perms.stream().map(RolePerms::getPermCode).toList());
            return m;
        }).toList();
        return GeneralMessage.okList(rows);
    }

    @GetMapping(value = {"/roles/{role_id}/perms", "/roles/{role_id}/perms/"})
    @RequirePerm(PermCode.TEAM_ROLE_PERMS)
    public ApiResult getRolePerms(@PathVariable("team_name") String teamName,
                                    @PathVariable("role_id") Integer roleId) {
        List<RolePerms> perms = roleService.rolePerms(roleId);
        List<Map<String, Object>> rows = perms.stream().map(rp -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("perm_code", rp.getPermCode());
            permsInfoRepo.findByCode(rp.getPermCode()).ifPresent(info -> {
                m.put("name", info.getName());
                m.put("desc", info.getDescription());
            });
            return m;
        }).toList();
        return GeneralMessage.okList(rows);
    }

    @PutMapping(value = {"/roles/{role_id}/perms", "/roles/{role_id}/perms/"})
    @RequirePerm(PermCode.TEAM_ROLE_PERMS)
    public ApiResult updateRolePerms(@PathVariable("team_name") String teamName,
                                       @PathVariable("role_id") Integer roleId,
                                       @RequestBody @Valid UpdateRolePermsReq req) {
        roleService.updateRolePerms(teamName, roleId, req);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/users/roles", "/users/roles/"})
    @RequirePerm(PermCode.TEAM_MEMBER_PERMS)
    public ApiResult userRoles(@PathVariable("team_name") String teamName) {
        // 简化版：列出该 team 下所有 role + 一份 user_role 关联快照（rainbond 原版含分页 + 用户名映射；
        // 留待后续业务 change 增强）
        List<RoleInfo> roles = roleService.listTeamRoles(teamName);
        return GeneralMessage.okList(roles.stream().map(this::serializeRole).toList());
    }

    @PutMapping(value = {"/users/{user_id}/roles", "/users/{user_id}/roles/"})
    @RequirePerm(PermCode.TEAM_MEMBER_PERMS)
    public ApiResult replaceUserRoles(@PathVariable("team_name") String teamName,
                                        @PathVariable("user_id") Integer userId,
                                        @RequestBody @Valid UpdateUserRolesReq req) {
        roleService.replaceUserRoles(userId, req);
        permService.evictUserTeamPerms(userId, teamName);
        return GeneralMessage.ok();
    }

    @DeleteMapping(value = {"/users/{user_id}/roles", "/users/{user_id}/roles/"})
    @RequirePerm(PermCode.TEAM_MEMBER_PERMS)
    public ApiResult removeUserRoles(@PathVariable("team_name") String teamName,
                                       @PathVariable("user_id") Integer userId,
                                       @RequestBody @Valid UpdateUserRolesReq req) {
        roleService.removeUserRoles(userId, req.roleIds());
        permService.evictUserTeamPerms(userId, teamName);
        return GeneralMessage.ok();
    }

    private Map<String, Object> serializeRole(RoleInfo r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role_id", r.getId());
        m.put("name", r.getName());
        m.put("kind", r.getKind());
        m.put("kind_id", r.getKindId());
        return m;
    }
}
