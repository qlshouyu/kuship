package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.page.PageRequestAdapter;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.dto.AddTeamMembersReq;
import cn.kuship.console.modules.account.dto.BatchUserIdsReq;
import cn.kuship.console.modules.account.dto.PemTransferReq;
import cn.kuship.console.modules.account.entity.PermRelTenant;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.perm.PermService;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.account.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** team 成员管理（列表 / 添加 / 批量删除 / 未加入 / 转让 owner）。 */
@RestController
@RequestMapping("/console/teams/{team_name}")
public class TeamMemberController {

    private final TeamService teamService;
    private final UserInfoRepository userRepo;
    private final RequestContext requestContext;
    private final PermService permService;
    private final PageRequestAdapter pageAdapter;

    public TeamMemberController(TeamService teamService,
                                 UserInfoRepository userRepo,
                                 RequestContext requestContext,
                                 PermService permService,
                                 PageRequestAdapter pageAdapter) {
        this.teamService = teamService;
        this.userRepo = userRepo;
        this.requestContext = requestContext;
        this.permService = permService;
        this.pageAdapter = pageAdapter;
    }

    @GetMapping(value = {"/users", "/users/"})
    @RequirePerm(cn.kuship.console.modules.account.perm.PermCode.TEAM_MEMBER_PERMS)
    public ApiResult listMembers(@PathVariable("team_name") String teamName) {
        List<PermRelTenant> rels = teamService.teamMembers(teamName);
        List<Map<String, Object>> users = rels.stream().map(rel -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("identity", rel.getIdentity());
            m.put("role_id", rel.getRoleId());
            userRepo.findById(rel.getUserId()).ifPresent(u -> {
                m.put("user_id", u.getUserId());
                m.put("nick_name", u.getNickName());
                m.put("email", u.getEmail());
                m.put("phone", u.getPhone());
            });
            return m;
        }).toList();
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("total", users.size());
        return GeneralMessage.okWithExtras(bean, users, null);
    }

    @PostMapping(value = {"/users", "/users/"})
    @RequirePerm(cn.kuship.console.modules.account.perm.PermCode.TEAM_MEMBER_PERMS)
    public ApiResult addMembers(@PathVariable("team_name") String teamName,
                                 @RequestBody @Valid AddTeamMembersReq req) {
        teamService.addMembers(teamName, req);
        for (Integer uid : req.userIds()) {
            permService.evictUserTeamPerms(uid, teamName);
        }
        return GeneralMessage.ok();
    }

    @DeleteMapping(value = {"/users/batch/delete", "/users/batch/delete/"})
    @RequirePerm(cn.kuship.console.modules.account.perm.PermCode.TEAM_MEMBER_PERMS)
    public ApiResult batchDelete(@PathVariable("team_name") String teamName,
                                  @RequestBody @Valid BatchUserIdsReq req) {
        teamService.removeMembers(teamName, req.userIds());
        for (Integer uid : req.userIds()) {
            permService.evictUserTeamPerms(uid, teamName);
        }
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/notjoinusers", "/notjoinusers/"})
    @RequirePerm(cn.kuship.console.modules.account.perm.PermCode.TEAM_MEMBER_PERMS)
    public ApiResult notJoinUsers(@PathVariable("team_name") String teamName) {
        var team = teamService.requireTeam(teamName);
        java.util.Set<Integer> joined = teamService.teamMembers(teamName).stream()
                .map(PermRelTenant::getUserId).collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> users = userRepo.findByEnterpriseId(team.getEnterpriseId()).stream()
                .filter(u -> !joined.contains(u.getUserId()))
                .map(this::serializeUser).toList();
        return GeneralMessage.okList(users);
    }

    @PostMapping(value = {"/pemtransfer", "/pemtransfer/"})
    public ApiResult transfer(@PathVariable("team_name") String teamName,
                               @RequestBody @Valid PemTransferReq req) {
        Integer userId = requireUser();
        teamService.transferOwner(teamName, userId, req.userId());
        permService.evictUserTeamPerms(userId, teamName);
        permService.evictUserTeamPerms(req.userId(), teamName);
        return GeneralMessage.ok();
    }

    private Integer requireUser() {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        return userId;
    }

    private Map<String, Object> serializeUser(UserInfo u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user_id", u.getUserId());
        m.put("nick_name", u.getNickName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        return m;
    }
}
