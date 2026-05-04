package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.region.dto.RegistryAuthReq;
import cn.kuship.console.modules.region.entity.TeamRegistryAuth;
import cn.kuship.console.modules.region.service.RegistryAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 团队级镜像仓库凭据（{@code /console/teams/{team_name}/registry/auth}）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/registry/auth")
public class TeamRegistryAuthController {

    private final RegistryAuthService service;
    private final TenantsRepository tenantsRepo;
    private final RequestContext requestContext;

    public TeamRegistryAuthController(RegistryAuthService service,
                                        TenantsRepository tenantsRepo,
                                        RequestContext requestContext) {
        this.service = service;
        this.tenantsRepo = tenantsRepo;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"", "/"})
    @RequirePerm(PermCode.TEAM_REGISTRY_AUTH)
    public ApiResult list(@PathVariable("team_name") String teamName) {
        Tenants team = requireTeam(teamName);
        List<Map<String, Object>> rows = service.listTeam(team.getTenantId()).stream()
                .map(this::serialize).toList();
        return GeneralMessage.okList(rows);
    }

    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.TEAM_REGISTRY_AUTH)
    public ApiResult create(@PathVariable("team_name") String teamName,
                              @RequestBody @Valid RegistryAuthReq req) {
        Tenants team = requireTeam(teamName);
        Integer userId = requireUser();
        TeamRegistryAuth saved = service.create(team.getTenantId(), userId, req);
        return GeneralMessage.ok(serialize(saved));
    }

    @GetMapping(value = {"/{secret_id}", "/{secret_id}/"})
    @RequirePerm(PermCode.TEAM_REGISTRY_AUTH)
    public ApiResult get(@PathVariable("team_name") String teamName,
                           @PathVariable("secret_id") String secretId) {
        TeamRegistryAuth a = service.requireBySecret(secretId);
        return GeneralMessage.ok(serialize(a));
    }

    @PutMapping(value = {"/{secret_id}", "/{secret_id}/"})
    @RequirePerm(PermCode.TEAM_REGISTRY_AUTH)
    public ApiResult update(@PathVariable("team_name") String teamName,
                              @PathVariable("secret_id") String secretId,
                              @RequestBody @Valid RegistryAuthReq req) {
        TeamRegistryAuth saved = service.update(secretId, req);
        return GeneralMessage.ok(serialize(saved));
    }

    @DeleteMapping(value = {"/{secret_id}", "/{secret_id}/"})
    @RequirePerm(PermCode.TEAM_REGISTRY_AUTH)
    public ApiResult delete(@PathVariable("team_name") String teamName,
                              @PathVariable("secret_id") String secretId) {
        service.delete(secretId);
        return GeneralMessage.ok();
    }

    private Tenants requireTeam(String teamName) {
        return tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }

    private Integer requireUser() {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        return userId;
    }

    private Map<String, Object> serialize(TeamRegistryAuth a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("secret_id", a.getSecretId());
        m.put("tenant_id", a.getTenantId());
        m.put("domain", a.getDomain());
        m.put("username", a.getUsername());
        m.put("region_name", a.getRegionName());
        m.put("hub_type", a.getHubType());
        m.put("create_time", a.getCreateTime());
        m.put("update_time", a.getUpdateTime());
        return m;
    }
}
