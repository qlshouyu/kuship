package cn.kuship.console.modules.plugin.team.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.plugin.api.PluginOperations;
import cn.kuship.console.modules.plugin.service.PluginContextLoader;
import cn.kuship.console.modules.plugin.team.entity.PluginBuildVersion;
import cn.kuship.console.modules.plugin.team.entity.TenantPlugin;
import cn.kuship.console.modules.plugin.team.repository.PluginBuildVersionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 插件版本构建：build-history / new-version / version detail / build / status / event-log。 */
@RestController
@RequestMapping("/console/teams/{team_name}/plugins/{plugin_id}")
public class PluginBuildController {

    private final PluginBuildVersionRepository versionRepo;
    private final PluginContextLoader loader;
    private final PluginOperations pluginOps;
    private final RequestContext requestContext;

    public PluginBuildController(PluginBuildVersionRepository versionRepo,
                                    PluginContextLoader loader,
                                    PluginOperations pluginOps,
                                    RequestContext requestContext) {
        this.versionRepo = versionRepo;
        this.loader = loader;
        this.pluginOps = pluginOps;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/build-history", "/build-history/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult history(@PathVariable("team_name") String teamName,
                                @PathVariable("plugin_id") String pluginId) {
        loader.requirePlugin(teamName, pluginId);
        return GeneralMessage.okList(versionRepo.findByPluginIdOrderByBuildTimeDesc(pluginId).stream()
                .map(PluginBuildController::toBean).toList());
    }

    @PostMapping(value = {"/new-version", "/new-version/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    @Transactional
    public ApiResult newVersion(@PathVariable("team_name") String teamName,
                                   @PathVariable("plugin_id") String pluginId,
                                   @RequestBody Map<String, Object> body) {
        TenantPlugin p = loader.requirePlugin(teamName, pluginId);
        String buildVersion = String.valueOf(body.getOrDefault("build_version", "1.0.0"));
        if (versionRepo.findByPluginIdAndBuildVersion(pluginId, buildVersion).isPresent()) {
            throw new ServiceHandleException(400, "version exists", "版本号已存在");
        }
        PluginBuildVersion v = new PluginBuildVersion();
        v.setPluginId(pluginId);
        v.setTenantId(p.getTenantId());
        v.setRegion(p.getRegion());
        v.setUserId(requestContext.getUserId() != null ? requestContext.getUserId() : 0);
        v.setUpdateInfo(String.valueOf(body.getOrDefault("update_info", "")));
        v.setBuildVersion(buildVersion);
        v.setBuildStatus("unbuilding");
        v.setPluginVersionStatus("unbuild");
        v.setMinMemory(body.get("min_memory") instanceof Number n ? n.intValue() : 64);
        v.setMinCpu(body.get("min_cpu") instanceof Number n ? n.intValue() : 50);
        v.setBuildCmd((String) body.get("build_cmd"));
        v.setImageTag((String) body.get("image_tag"));
        v.setCodeVersion((String) body.get("code_version"));
        v.setBuildTime(LocalDateTime.now());
        versionRepo.save(v);
        return GeneralMessage.ok(toBean(v));
    }

    @GetMapping(value = {"/version/{build_version}", "/version/{build_version}/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult versionDetail(@PathVariable("plugin_id") String pluginId,
                                       @PathVariable("build_version") String buildVersion) {
        PluginBuildVersion v = versionRepo.findByPluginIdAndBuildVersion(pluginId, buildVersion)
                .orElseThrow(() -> new ServiceHandleException(404, "version not found", "版本不存在"));
        return GeneralMessage.ok(toBean(v));
    }

    @PutMapping(value = {"/version/{build_version}", "/version/{build_version}/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    @Transactional
    public ApiResult versionUpdate(@PathVariable("plugin_id") String pluginId,
                                       @PathVariable("build_version") String buildVersion,
                                       @RequestBody Map<String, Object> body) {
        PluginBuildVersion v = versionRepo.findByPluginIdAndBuildVersion(pluginId, buildVersion)
                .orElseThrow(() -> new ServiceHandleException(404, "version not found", "版本不存在"));
        if (body.get("update_info") instanceof String u) v.setUpdateInfo(u);
        if (body.get("min_memory") instanceof Number n) v.setMinMemory(n.intValue());
        if (body.get("min_cpu") instanceof Number n) v.setMinCpu(n.intValue());
        if (body.get("build_cmd") instanceof String bc) v.setBuildCmd(bc);
        versionRepo.save(v);
        return GeneralMessage.ok(toBean(v));
    }

    @PostMapping(value = {"/version/{build_version}/build", "/version/{build_version}/build/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    @Transactional
    public ApiResult build(@PathVariable("team_name") String teamName,
                              @PathVariable("plugin_id") String pluginId,
                              @PathVariable("build_version") String buildVersion) {
        PluginBuildVersion v = versionRepo.findByPluginIdAndBuildVersion(pluginId, buildVersion)
                .orElseThrow(() -> new ServiceHandleException(404, "version not found", "版本不存在"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("build_version", buildVersion);
        body.put("plugin_id", pluginId);
        Map<String, Object> resp = pluginOps.buildPlugin(v.getRegion(), teamName, pluginId, body);
        if (resp != null && resp.get("event_id") != null) {
            v.setEventId(resp.get("event_id").toString());
        }
        v.setBuildStatus("building");
        v.setPluginVersionStatus("building");
        versionRepo.save(v);
        return GeneralMessage.ok(toBean(v));
    }

    @GetMapping(value = {"/version/{build_version}/status", "/version/{build_version}/status/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult status(@PathVariable("team_name") String teamName,
                                @PathVariable("plugin_id") String pluginId,
                                @PathVariable("build_version") String buildVersion) {
        PluginBuildVersion v = versionRepo.findByPluginIdAndBuildVersion(pluginId, buildVersion)
                .orElseThrow(() -> new ServiceHandleException(404, "version not found", "版本不存在"));
        try {
            Map<String, Object> resp = pluginOps.getPluginBuildStatus(v.getRegion(), teamName, pluginId, buildVersion);
            if (resp != null && resp.get("status") instanceof String s) {
                v.setBuildStatus(s);
                v.setPluginVersionStatus(s);
                versionRepo.save(v);
            }
        } catch (Exception ignored) {
            // region 不可达时返回本地状态
        }
        return GeneralMessage.ok(toBean(v));
    }

    @GetMapping(value = {"/version/{build_version}/event-log", "/version/{build_version}/event-log/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult eventLog(@PathVariable("plugin_id") String pluginId,
                                  @PathVariable("build_version") String buildVersion) {
        // MVP：透传需走 region；占位返回空 list
        return GeneralMessage.okList(java.util.List.of());
    }

    static Map<String, Object> toBean(PluginBuildVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("build_version", v.getBuildVersion());
        m.put("update_info", v.getUpdateInfo());
        m.put("build_status", v.getBuildStatus());
        m.put("plugin_version_status", v.getPluginVersionStatus());
        m.put("event_id", v.getEventId());
        m.put("min_memory", v.getMinMemory());
        m.put("min_cpu", v.getMinCpu());
        m.put("image_tag", v.getImageTag());
        m.put("build_time", v.getBuildTime());
        return m;
    }
}
