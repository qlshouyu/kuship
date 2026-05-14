package cn.kuship.console.modules.plugin.team.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.appmarket.share.api.ShareOperations;
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
import java.util.List;
import java.util.Map;

/**
 * 插件分享异步流程：5 endpoint + region 接线（migrate-console-app-share）。
 *
 * <p>状态机模板与 ServiceShareController 同构：addEvent 调 region {@code sharePlugin}、
 * eventStatus 调 region {@code getSharePluginResult}、complete 仅校验本地 + 用 shareVersion
 * 末尾 {@code _COMPLETE} 后缀标记完成（rainbond 模式无独立 status 列）。
 */
@RestController
public class PluginShareController {

    private final TenantPluginShareRepository shareRepo;
    private final PluginShareRecordEventRepository eventRepo;
    private final PluginContextLoader loader;
    private final RequestContext requestContext;
    private final ShareOperations shareOps;
    private final TenantsRepository tenantsRepo;

    public PluginShareController(TenantPluginShareRepository shareRepo,
                                    PluginShareRecordEventRepository eventRepo,
                                    PluginContextLoader loader,
                                    RequestContext requestContext,
                                    ShareOperations shareOps,
                                    TenantsRepository tenantsRepo) {
        this.shareRepo = shareRepo;
        this.eventRepo = eventRepo;
        this.loader = loader;
        this.requestContext = requestContext;
        this.shareOps = shareOps;
        this.tenantsRepo = tenantsRepo;
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

    /**
     * step 3：本地落 event 行 + 调 region {@code sharePlugin}（@Transactional 失败回滚）。
     */
    @PostMapping(value = {"/console/teams/{team_name}/plugin-share/{share_id}/events/{event_id}",
                            "/console/teams/{team_name}/plugin-share/{share_id}/events/{event_id}/"})
    @Transactional
    public ApiResult addEvent(@PathVariable("team_name") String teamName,
                                  @PathVariable("share_id") String shareId,
                                  @PathVariable("event_id") String eventId,
                                  @RequestBody Map<String, Object> body) {
        TenantPluginShare s = requireShare(shareId);
        Map<String, Object> b = body == null ? Map.of() : body;
        String regionName = String.valueOf(b.getOrDefault("region_name", ""));
        if (regionName.isBlank()) {
            throw new ServiceHandleException(400, "missing region_name", "缺少 region_name");
        }

        Map<String, Object> regionBody = new LinkedHashMap<>(b);
        regionBody.put("event_id", eventId);
        regionBody.putIfAbsent("plugin_id", s.getOriginPluginId());
        regionBody.putIfAbsent("plugin_version", s.getShareVersion());
        regionBody.putIfAbsent("plugin_key", s.getPluginName());
        Map<String, Object> bean = shareOps.sharePlugin(regionName, teamName, s.getOriginPluginId(), regionBody);

        PluginShareRecordEvent e = new PluginShareRecordEvent();
        e.setRecordId(s.getId());
        e.setRegionShareId(stringOr(bean.get("share_id"), eventId));
        e.setTeamId(s.getTenantId());
        e.setTeamName(teamName);
        e.setPluginId(s.getOriginPluginId());
        e.setPluginName(s.getPluginName());
        e.setEventId(stringOr(bean.get("event_id"), eventId));
        e.setEventStatus("start");
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        eventRepo.save(e);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("event_id", e.getEventId());
        out.put("region_share_id", e.getRegionShareId());
        out.put("event_status", e.getEventStatus());
        out.putAll(bean);
        return GeneralMessage.ok(out);
    }

    /**
     * step 4 轮询：从 region 拉 plugin share status 同步到本地 {@code event_status}。
     */
    @GetMapping(value = {"/console/teams/{team_name}/plugin-share/{share_id}/events/{event_id}/status",
                          "/console/teams/{team_name}/plugin-share/{share_id}/events/{event_id}/status/"})
    @Transactional
    public ApiResult eventStatus(@PathVariable("team_name") String teamName,
                                       @PathVariable("share_id") String shareId,
                                       @PathVariable("event_id") String eventId,
                                       @RequestParam(value = "region_name", required = false) String regionName) {
        TenantPluginShare s = requireShare(shareId);
        PluginShareRecordEvent e = eventRepo.findByRecordIdAndEventId(s.getId(), eventId)
                .orElseThrow(() -> new ServiceHandleException(404, "event not found", "事件不存在"));

        if (e.getRegionShareId() == null || e.getRegionShareId().isBlank()) {
            return GeneralMessage.ok(Map.of("event_status", "pending"));
        }
        if (regionName == null || regionName.isBlank()) {
            // tenant 通常一个 region；缺省 region_name 时尝试从 tenant 上下文回退
            tenantsRepo.findByTenantId(s.getTenantId()).ifPresent(t -> {});
            throw new ServiceHandleException(400, "missing region_name", "缺少 region_name");
        }

        Map<String, Object> bean = shareOps.getSharePluginResult(
                regionName, teamName, s.getOriginPluginId(), e.getRegionShareId());
        Object status = bean.get("status");
        if (status instanceof String st && !st.isBlank()) {
            e.setEventStatus(st);
            e.setUpdateTime(LocalDateTime.now());
            eventRepo.save(e);
        }
        return GeneralMessage.ok(bean);
    }

    /**
     * step 5：校验全部 event_status=success → 在 shareVersion 末尾追加 {@code _COMPLETE}。
     */
    @PostMapping(value = {"/console/teams/{team_name}/plugin-share/{share_id}/complete",
                            "/console/teams/{team_name}/plugin-share/{share_id}/complete/"})
    @Transactional
    public ApiResult complete(@PathVariable("team_name") String teamName,
                                  @PathVariable("share_id") String shareId) {
        TenantPluginShare s = requireShare(shareId);

        List<PluginShareRecordEvent> events = eventRepo.findByRecordId(s.getId());
        for (PluginShareRecordEvent e : events) {
            String st = e.getEventStatus();
            if (st == null || "failure".equalsIgnoreCase(st) || "fail".equalsIgnoreCase(st) || "error".equalsIgnoreCase(st)) {
                throw new ServiceHandleException(409, "share not all success", "存在失败事件，请放弃后重试");
            }
            if (!"success".equalsIgnoreCase(st)) {
                throw new ServiceHandleException(409, "share not finished", "分享尚未完成");
            }
        }

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

    private static String stringOr(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
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
