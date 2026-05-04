package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.dto.CreateTeamReq;
import cn.kuship.console.modules.account.dto.UpdateTeamReq;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code /console/teams/init} / {@code /console/teams/{team_name}} / {@code /console/teams/{team_name}/exit}。 */
@RestController
@RequestMapping("/console/teams")
public class TeamController {

    private final TeamService teamService;
    private final RequestContext requestContext;

    public TeamController(TeamService teamService, RequestContext requestContext) {
        this.teamService = teamService;
        this.requestContext = requestContext;
    }

    @PostMapping(value = {"/init", "/init/"})
    public ApiResult create(@RequestBody @Valid CreateTeamReq req) {
        Integer userId = requireUser();
        Tenants team = teamService.createTeam(userId, requestContext.getEnterpriseId(), req);
        return GeneralMessage.ok(serialize(team));
    }

    @PutMapping(value = {"/{team_name}", "/{team_name}/"})
    public ApiResult update(@PathVariable("team_name") String teamName,
                             @RequestBody @Valid UpdateTeamReq req) {
        Integer userId = requireUser();
        Tenants team = teamService.updateTeam(teamName, userId, req);
        return GeneralMessage.ok(serialize(team));
    }

    @DeleteMapping(value = {"/{team_name}", "/{team_name}/"})
    public ApiResult delete(@PathVariable("team_name") String teamName) {
        Integer userId = requireUser();
        teamService.deleteTeam(teamName, userId, requestContext.isSysAdmin());
        return GeneralMessage.ok();
    }

    @PostMapping(value = {"/{team_name}/exit", "/{team_name}/exit/"})
    public ApiResult exit(@PathVariable("team_name") String teamName) {
        Integer userId = requireUser();
        teamService.exitTeam(teamName, userId);
        return GeneralMessage.ok();
    }

    private Integer requireUser() {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        return userId;
    }

    private Map<String, Object> serialize(Tenants t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenant_id", t.getTenantId());
        m.put("team_name", t.getTenantName());
        m.put("team_alias", t.getTenantAlias());
        m.put("namespace", t.getNamespace());
        m.put("enterprise_id", t.getEnterpriseId());
        m.put("creater", t.getCreater());
        return m;
    }
}
