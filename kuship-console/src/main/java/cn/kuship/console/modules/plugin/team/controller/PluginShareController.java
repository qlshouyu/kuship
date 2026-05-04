package cn.kuship.console.modules.plugin.team.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.plugin.service.PluginContextLoader;
import cn.kuship.console.modules.plugin.team.entity.PluginShareRecordEvent;
import cn.kuship.console.modules.plugin.team.entity.TenantPlugin;
import cn.kuship.console.modules.plugin.team.entity.TenantPluginShare;
import cn.kuship.console.modules.plugin.team.repository.PluginShareRecordEventRepository;
import cn.kuship.console.modules.plugin.team.repository.TenantPluginShareRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 插件分享异步流程：5 endpoint。状态机模板与 ServiceShareController 同构。 */
@RestController
public class PluginShareController {

    private final TenantPluginShareRepository shareRepo;
    private final PluginShareRecordEventRepository eventRepo;
    private final PluginContextLoader loader;
    private final RequestContext requestContext;

    public PluginShareController(TenantPluginShareRepository shareRepo,
                                    PluginShareRecordEventRepository eventRepo,
                                    PluginContextLoader loader,
                                    RequestContext requestContext) {
        this.shareRepo = shareRepo;
        this.eventRepo = eventRepo;
        this.loader = loader;
        this.requestContext = requestContext;
    }

    @PostMapping(value = {"/console/teams/{team_name}/plugins/{plugin_id}/share/record",
                            "/console/teams/{team_name}/plugins/{plugin_id}/share/record/"})
    @Transactional
    public ApiResult start(@PathVariable("team_name") String teamName,
                              @PathVariable("plugin_id") String pluginId,
                              @RequestBody Map<String, Object> body) {
        TenantPlugin p = loader.requirePlugin(teamName, pluginId);
        TenantPluginShare s = new TenantPluginShare();
        s.setShareId(UuidGenerator.makeUuid());
        s.setShareVersion(String.valueOf(body.getOrDefault("share_version", "1.0")));
        s.setOriginPluginId(pluginId);
        s.setTenantId(p.getTenantId());
        s.setUserId(requestContext.getUserId() != null ? requestContext.getUserId() : 0);
        s.setDescribe(String.valueOf(body.getOrDefault("desc", p.getDescribe())));
        s.setPluginName(p.getPluginName());
        s.setPluginAlias(p.getPluginAlias());
        s.setCategory(p.getCategory());
        s.setImage(p.getImage());
        s.setUpdateInfo(String.valueOf(body.getOrDefault("update_info", "")));
        s.setMinMemory(body.get("min_memory") instanceof Number n ? n.intValue() : 64);
        s.setMinCpu(body.get("min_cpu") instanceof Number n ? n.intValue() : 50);
        s.setBuildCmd((String) body.get("build_cmd"));
        s.setConfig(String.valueOf(body.getOrDefault("config", "")));
        s.setCreateTime(LocalDateTime.now());
        shareRepo.save(s);
        return GeneralMessage.ok(toBean(s));
    }

    @GetMapping(value = {"/console/teams/{team_name}/plugin-share/{share_id}",
                          "/console/teams/{team_name}/plugin-share/{share_id}/"})
    public ApiResult info(@PathVariable("team_name") String teamName,
                            @PathVariable("share_id") String shareId) {
        TenantPluginShare s = requireShare(shareId);
        return GeneralMessage.ok(toBean(s));
    }

    @GetMapping(value = {"/console/teams/{team_name}/plugin-share/{share_id}/events",
                          "/console/teams/{team_name}/plugin-share/{share_id}/events/"})
    public ApiResult listEvents(@PathVariable("share_id") String shareId) {
        TenantPluginShare s = requireShare(shareId);
        return GeneralMessage.okList(eventRepo.findByRecordId(s.getId()).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("event_id", e.getEventId());
            m.put("event_status", e.getEventStatus());
            m.put("plugin_name", e.getPluginName());
            m.put("update_time", e.getUpdateTime());
            return m;
        }).toList());
    }

    @PostMapping(value = {"/console/teams/{team_name}/plugin-share/{share_id}/events/{event_id}",
                            "/console/teams/{team_name}/plugin-share/{share_id}/events/{event_id}/"})
    @Transactional
    public ApiResult addEvent(@PathVariable("team_name") String teamName,
                                  @PathVariable("share_id") String shareId,
                                  @PathVariable("event_id") String eventId,
                                  @RequestBody Map<String, Object> body) {
        TenantPluginShare s = requireShare(shareId);
        PluginShareRecordEvent e = new PluginShareRecordEvent();
        e.setRecordId(s.getId());
        e.setRegionShareId(eventId);
        e.setTeamId(s.getTenantId());
        e.setTeamName(teamName);
        e.setPluginId(s.getOriginPluginId());
        e.setPluginName(s.getPluginName());
        e.setEventId(eventId);
        e.setEventStatus(String.valueOf(body.getOrDefault("event_status", "running")));
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        eventRepo.save(e);
        return GeneralMessage.ok();
    }

    @PostMapping(value = {"/console/teams/{team_name}/plugin-share/{share_id}/complete",
                            "/console/teams/{team_name}/plugin-share/{share_id}/complete/"})
    @Transactional
    public ApiResult complete(@PathVariable("team_name") String teamName,
                                  @PathVariable("share_id") String shareId) {
        TenantPluginShare s = requireShare(shareId);
        // 用 shareVersion 字段以 _COMPLETE 后缀标记完成（rainbond 模式无独立 status 列；保持简化）
        if (!s.getShareVersion().endsWith("_COMPLETE")) {
            s.setShareVersion(s.getShareVersion() + "_COMPLETE");
            shareRepo.save(s);
        }
        return GeneralMessage.ok(toBean(s));
    }

    private TenantPluginShare requireShare(String shareId) {
        return shareRepo.findByShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "share not found", "分享记录不存在"));
    }

    static Map<String, Object> toBean(TenantPluginShare s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("share_id", s.getShareId());
        m.put("origin_plugin_id", s.getOriginPluginId());
        m.put("share_version", s.getShareVersion());
        m.put("plugin_name", s.getPluginName());
        m.put("plugin_alias", s.getPluginAlias());
        m.put("category", s.getCategory());
        m.put("desc", s.getDescribe());
        m.put("update_info", s.getUpdateInfo());
        m.put("create_time", s.getCreateTime());
        return m;
    }
}
