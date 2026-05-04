package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.region.dto.RegistryAuthReq;
import cn.kuship.console.modules.region.entity.TeamRegistryAuth;
import cn.kuship.console.modules.region.service.RegistryAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台级镜像仓库凭据（{@code /console/hub/registry}）。复用 {@code team_registry_auths} 表，
 * 通过 {@code tenant_id="" + region_name=""} 区分平台级 vs 团队级。
 *
 * <p>写入要求 sys_admin（{@code RequestContext.sysAdmin=true}）。
 */
@RestController
@RequestMapping("/console/hub/registry")
public class HubRegistryController {

    private final RegistryAuthService service;
    private final RequestContext requestContext;

    public HubRegistryController(RegistryAuthService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"", "/"})
    public ApiResult list() {
        Integer userId = requireUser();
        List<Map<String, Object>> rows = service.listHub(userId).stream()
                .map(this::serialize).toList();
        return GeneralMessage.okList(rows);
    }

    @PostMapping(value = {"", "/"})
    public ApiResult create(@RequestBody @Valid RegistryAuthReq req) {
        requireSysAdmin();
        Integer userId = requireUser();
        TeamRegistryAuth saved = service.create("", userId,
                new RegistryAuthReq(req.domain(), req.username(), req.password(),
                        req.hubType(), "", req.secretId()));
        return GeneralMessage.ok(serialize(saved));
    }

    @PutMapping(value = {"", "/"})
    public ApiResult update(@RequestParam("secret_id") String secretId,
                              @RequestBody @Valid RegistryAuthReq req) {
        requireSysAdmin();
        TeamRegistryAuth saved = service.update(secretId, req);
        return GeneralMessage.ok(serialize(saved));
    }

    @DeleteMapping(value = {"", "/"})
    public ApiResult delete(@RequestParam("secret_id") String secretId) {
        requireSysAdmin();
        service.delete(secretId);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/image", "/image/"})
    public ApiResult image(@RequestParam("secret_id") String secretId,
                             @RequestParam(value = "namespace", required = false) String namespace,
                             @RequestParam(value = "name", required = false) String name) {
        requireUser();
        // 简化版：本 change 不实现 registry HTTP V2 调用（rainbond 端依赖 hub_type 适配 docker/harbor/quay 等），
        // 留给 hardening change。当前返回空列表 + 提示
        TeamRegistryAuth auth = service.requireBySecret(secretId);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("domain", auth.getDomain());
        bean.put("hub_type", auth.getHubType());
        return new cn.kuship.console.common.response.ApiResult(200,
                "registry image listing not implemented yet (placeholder)",
                "镜像列表查询尚未实现",
                Map.of("bean", bean, "list", List.of()));
    }

    private Integer requireUser() {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        return userId;
    }

    private void requireSysAdmin() {
        if (!requestContext.isSysAdmin()) {
            throw new ServiceHandleException(403, "sys_admin required", "您无操作此功能的权限");
        }
    }

    private Map<String, Object> serialize(TeamRegistryAuth a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("secret_id", a.getSecretId());
        m.put("domain", a.getDomain());
        m.put("username", a.getUsername());
        // password 不输出
        m.put("hub_type", a.getHubType());
        m.put("create_time", a.getCreateTime());
        m.put("update_time", a.getUpdateTime());
        return m;
    }
}
