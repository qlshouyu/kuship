package cn.kuship.console.modules.plugin.team.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.plugin.api.PluginOperations;
import cn.kuship.console.modules.plugin.service.PluginContextLoader;
import cn.kuship.console.modules.plugin.team.entity.TenantPlugin;
import cn.kuship.console.modules.plugin.team.repository.TenantPluginRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 团队级插件 CRUD：8 endpoint。 */
@RestController
@RequestMapping("/console/teams/{team_name}")
public class TenantPluginController {

    private final TenantPluginRepository pluginRepo;
    private final PluginContextLoader loader;
    private final PluginOperations pluginOps;
    private final TenantServiceRepository serviceRepo;
    private final RequestContext requestContext;

    public TenantPluginController(TenantPluginRepository pluginRepo,
                                     PluginContextLoader loader,
                                     PluginOperations pluginOps,
                                     TenantServiceRepository serviceRepo,
                                     RequestContext requestContext) {
        this.pluginRepo = pluginRepo;
        this.loader = loader;
        this.pluginOps = pluginOps;
        this.serviceRepo = serviceRepo;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/plugins", "/plugins/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult list(@PathVariable("team_name") String teamName) {
        Tenants team = loader.requireTeam(teamName);
        return GeneralMessage.okList(pluginRepo.findByTenantId(team.getTenantId()).stream()
                .map(TenantPluginController::toBean).toList());
    }

    @GetMapping(value = {"/plugins/all", "/plugins/all/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult listAll(@PathVariable("team_name") String teamName) {
        return list(teamName);
    }

    @PostMapping(value = {"/plugins", "/plugins/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    @Transactional
    public ApiResult create(@PathVariable("team_name") String teamName,
                              @RequestBody Map<String, Object> body) {
        Tenants team = loader.requireTeam(teamName);
        TenantPlugin p = new TenantPlugin();
        p.setPluginId(UuidGenerator.makeUuid());
        p.setTenantId(team.getTenantId());
        p.setRegion(String.valueOf(body.getOrDefault("region", "default")));
        p.setCreateUser(requestContext.getUserId() != null ? requestContext.getUserId() : 0);
        p.setDescribe(String.valueOf(body.getOrDefault("desc", "")));
        p.setPluginName(requireString(body, "plugin_name"));
        p.setPluginAlias(String.valueOf(body.getOrDefault("plugin_alias", body.get("plugin_name"))));
        p.setCategory(String.valueOf(body.getOrDefault("category", "general")));
        p.setBuildSource(String.valueOf(body.getOrDefault("build_source", "image")));
        p.setImage((String) body.get("image"));
        p.setCodeRepo((String) body.get("code_repo"));
        p.setOrigin("local");
        p.setOriginShareId("");
        p.setUsername((String) body.get("username"));
        p.setPassword((String) body.get("password"));
        p.setCreateTime(LocalDateTime.now());
        pluginRepo.save(p);
        try {
            pluginOps.createPlugin(p.getRegion(), teamName, body);
        } catch (Exception ignored) {
            // region 失败不阻塞本地建档；下次手动 build 时仍可触发 region
        }
        return GeneralMessage.ok(toBean(p));
    }

    @PostMapping(value = {"/plugins/default", "/plugins/default/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult createDefault(@PathVariable("team_name") String teamName,
                                       @RequestBody(required = false) Map<String, Object> body) {
        // MVP：占位返回内置默认插件 plugin_id 列表，深度安装留作 hardening
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/plugins/{plugin_id}", "/plugins/{plugin_id}/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult detail(@PathVariable("team_name") String teamName,
                              @PathVariable("plugin_id") String pluginId) {
        TenantPlugin p = loader.requirePlugin(teamName, pluginId);
        return GeneralMessage.ok(toBean(p));
    }

    @PutMapping(value = {"/plugins/{plugin_id}", "/plugins/{plugin_id}/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    @Transactional
    public ApiResult update(@PathVariable("team_name") String teamName,
                              @PathVariable("plugin_id") String pluginId,
                              @RequestBody Map<String, Object> body) {
        TenantPlugin p = loader.requirePlugin(teamName, pluginId);
        if (body.get("plugin_alias") instanceof String a) p.setPluginAlias(a);
        if (body.get("desc") instanceof String d) p.setDescribe(d);
        if (body.get("category") instanceof String c) p.setCategory(c);
        if (body.get("image") instanceof String i) p.setImage(i);
        pluginRepo.save(p);
        return GeneralMessage.ok(toBean(p));
    }

    @DeleteMapping(value = {"/plugins/{plugin_id}", "/plugins/{plugin_id}/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    @Transactional
    public ApiResult delete(@PathVariable("team_name") String teamName,
                              @PathVariable("plugin_id") String pluginId) {
        TenantPlugin p = loader.requirePlugin(teamName, pluginId);
        try {
            pluginOps.deletePlugin(p.getRegion(), teamName, pluginId);
        } catch (Exception ignored) {
            // region 失败不阻塞本地清理
        }
        pluginRepo.deleteByPluginId(pluginId);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/plugins/{plugin_id}/used_services", "/plugins/{plugin_id}/used_services/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult usedServices(@PathVariable("team_name") String teamName,
                                       @PathVariable("plugin_id") String pluginId) {
        loader.requirePlugin(teamName, pluginId);
        // MVP：占位返回空数组；完整 join 留作 hardening
        return GeneralMessage.okList(List.of());
    }

    private static String requireString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new ServiceHandleException(400, "missing " + key, "缺少 " + key);
        }
        return s;
    }

    static Map<String, Object> toBean(TenantPlugin p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plugin_id", p.getPluginId());
        m.put("plugin_name", p.getPluginName());
        m.put("plugin_alias", p.getPluginAlias());
        m.put("category", p.getCategory());
        m.put("build_source", p.getBuildSource());
        m.put("image", p.getImage());
        m.put("code_repo", p.getCodeRepo());
        m.put("desc", p.getDescribe());
        m.put("region", p.getRegion());
        m.put("origin", p.getOrigin());
        m.put("create_time", p.getCreateTime());
        return m;
    }
}
