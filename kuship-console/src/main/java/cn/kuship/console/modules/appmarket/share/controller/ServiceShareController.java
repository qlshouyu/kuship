package cn.kuship.console.modules.appmarket.share.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.appmarket.share.api.ShareOperations;
import cn.kuship.console.modules.appmarket.share.entity.ServiceShareRecord;
import cn.kuship.console.modules.appmarket.share.entity.ServiceShareRecordEvent;
import cn.kuship.console.modules.appmarket.share.repository.ServiceShareRecordEventRepository;
import cn.kuship.console.modules.appmarket.share.repository.ServiceShareRecordRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务分享流程：record CRUD + info / events / complete / giveup + region 接线（migrate-console-app-share）。
 *
 * <p>6-step 状态机各步对应 region 调用注入点：step3=share、step4=getResult 轮询；
 * step5 complete 仅校验本地 event_status=success，不调 region（与 rainbond Python fire-and-forget 模式一致）。
 */
@RestController
public class ServiceShareController {

    private final ServiceShareRecordRepository recordRepo;
    private final ServiceShareRecordEventRepository eventRepo;
    private final ShareOperations shareOps;
    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public ServiceShareController(ServiceShareRecordRepository recordRepo,
                                     ServiceShareRecordEventRepository eventRepo,
                                     ShareOperations shareOps,
                                     TenantsRepository tenantsRepo,
                                     TenantServiceRepository serviceRepo) {
        this.recordRepo = recordRepo;
        this.eventRepo = eventRepo;
        this.shareOps = shareOps;
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping(value = {"/console/teams/{team_name}/groups/{group_id}/share/record/{record_id}",
                          "/console/teams/{team_name}/groups/{group_id}/share/record/{record_id}/"})
    public ApiResult getRecord(@PathVariable("team_name") String teamName,
                                  @PathVariable("group_id") String groupId,
                                  @PathVariable("record_id") String recordId) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(recordId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        return GeneralMessage.ok(toBean(r));
    }

    @PostMapping(value = {"/console/teams/{team_name}/groups/{group_id}/share/record",
                            "/console/teams/{team_name}/groups/{group_id}/share/record/"})
    @Transactional
    public ApiResult startShare(@PathVariable("team_name") String teamName,
                                   @PathVariable("group_id") String groupId,
                                   @RequestBody Map<String, Object> body) {
        ServiceShareRecord r = new ServiceShareRecord();
        r.setGroupShareId(UuidGenerator.makeUuid());
        r.setGroupId(groupId);
        r.setTeamName(teamName);
        r.setShareVersion((String) body.getOrDefault("share_version", "1.0"));
        r.setShareVersionAlias((String) body.getOrDefault("share_version_alias", ""));
        r.setShareAppVersionInfo((String) body.getOrDefault("share_app_version_info", ""));
        r.setShareAppMarketName((String) body.get("share_app_market_name"));
        r.setShareStoreName((String) body.get("share_store_name"));
        r.setShareAppModelName((String) body.get("share_app_model_name"));
        r.setIsSuccess(false);
        r.setStep(0);
        r.setStatus(0);
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        recordRepo.save(r);
        return GeneralMessage.ok(toBean(r));
    }

    @DeleteMapping(value = {"/console/teams/{team_name}/groups/{group_id}/share/record/{record_id}",
                              "/console/teams/{team_name}/groups/{group_id}/share/record/{record_id}/"})
    @Transactional
    public ApiResult cancel(@PathVariable("team_name") String teamName,
                              @PathVariable("group_id") String groupId,
                              @PathVariable("record_id") String recordId) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(recordId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        eventRepo.deleteByRecordId(r.getId());
        recordRepo.delete(r);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/console/teams/{team_name}/groups/{group_id}/share/record/version",
                          "/console/teams/{team_name}/groups/{group_id}/share/record/version/"})
    public ApiResult listVersions(@PathVariable("team_name") String teamName,
                                       @PathVariable("group_id") String groupId) {
        List<ServiceShareRecord> all = recordRepo.findByGroupId(groupId);
        return GeneralMessage.okList(all.stream().map(ServiceShareController::toBean).toList());
    }

    @PostMapping(value = {"/console/teams/{team_name}/groups/{group_id}/share/step",
                            "/console/teams/{team_name}/groups/{group_id}/share/step/"})
    @Transactional
    public ApiResult updateStep(@PathVariable("team_name") String teamName,
                                    @PathVariable("group_id") String groupId,
                                    @RequestBody Map<String, Object> body) {
        String groupShareId = (String) body.get("group_share_id");
        if (groupShareId == null) throw new ServiceHandleException(400, "missing group_share_id", "缺少 group_share_id");
        ServiceShareRecord r = recordRepo.findByGroupShareId(groupShareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        if (body.get("step") instanceof Number n) r.setStep(n.intValue());
        if (body.get("status") instanceof Number n) r.setStatus(n.intValue());
        r.setUpdateTime(LocalDateTime.now());
        recordRepo.save(r);
        return GeneralMessage.ok(toBean(r));
    }

    @PutMapping(value = {"/console/teams/{team_name}/groups/{group_id}/share/step",
                          "/console/teams/{team_name}/groups/{group_id}/share/step/"})
    public ApiResult updateStepPut(@PathVariable("team_name") String teamName,
                                       @PathVariable("group_id") String groupId,
                                       @RequestBody Map<String, Object> body) {
        return updateStep(teamName, groupId, body);
    }

    @GetMapping(value = {"/console/teams/{team_name}/share/{share_id}/info",
                          "/console/teams/{team_name}/share/{share_id}/info/"})
    public ApiResult getInfo(@PathVariable("team_name") String teamName,
                                @PathVariable("share_id") String shareId) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        return GeneralMessage.ok(toBean(r));
    }

    @PostMapping(value = {"/console/teams/{team_name}/share/{share_id}/info",
                            "/console/teams/{team_name}/share/{share_id}/info/"})
    @Transactional
    public ApiResult pushInfo(@PathVariable("team_name") String teamName,
                                  @PathVariable("share_id") String shareId,
                                  @RequestBody Map<String, Object> body) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        if (body.get("event_id") instanceof String eid) r.setEventId(eid);
        if (body.get("app_id") instanceof String aid) r.setAppId(aid);
        r.setStep(2);
        r.setUpdateTime(LocalDateTime.now());
        recordRepo.save(r);
        return GeneralMessage.ok(toBean(r));
    }

    @PostMapping(value = {"/console/teams/{team_name}/share/{share_id}/giveup",
                            "/console/teams/{team_name}/share/{share_id}/giveup/"})
    @Transactional
    public ApiResult giveup(@PathVariable("team_name") String teamName,
                                @PathVariable("share_id") String shareId) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        r.setStatus(2);
        r.setUpdateTime(LocalDateTime.now());
        recordRepo.save(r);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/console/teams/{team_name}/share/{share_id}/events",
                          "/console/teams/{team_name}/share/{share_id}/events/"})
    public ApiResult listEvents(@PathVariable("team_name") String teamName,
                                    @PathVariable("share_id") String shareId) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        return GeneralMessage.okList(eventRepo.findByRecordId(r.getId()).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("event_id", e.getEventId());
            m.put("event_status", e.getEventStatus());
            m.put("service_alias", e.getServiceAlias());
            m.put("service_name", e.getServiceName());
            m.put("update_time", e.getUpdateTime());
            return m;
        }).toList());
    }

    /**
     * step 3 服务级事件：本地落 event 行 + 调 region {@code shareService}（@Transactional 失败回滚）。
     */
    @PostMapping(value = {"/console/teams/{team_name}/share/{share_id}/events/{event_id}",
                            "/console/teams/{team_name}/share/{share_id}/events/{event_id}/"})
    @Transactional
    public ApiResult addEvent(@PathVariable("team_name") String teamName,
                                  @PathVariable("share_id") String shareId,
                                  @PathVariable("event_id") String eventId,
                                  @RequestBody Map<String, Object> body) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));

        String serviceAlias = String.valueOf(body.getOrDefault("service_alias", ""));
        if (serviceAlias.isBlank()) {
            throw new ServiceHandleException(400, "missing service_alias", "缺少 service_alias");
        }
        TenantService svc = resolveService(teamName, serviceAlias);

        Map<String, Object> regionBody = new LinkedHashMap<>(body);
        regionBody.put("event_id", eventId);
        Map<String, Object> bean = shareOps.shareService(svc.getServiceRegion(), teamName, serviceAlias, regionBody);

        ServiceShareRecordEvent e = new ServiceShareRecordEvent();
        e.setRecordId(r.getId());
        e.setRegionShareId(stringOr(bean.get("share_id"), eventId));
        e.setTeamName(teamName);
        e.setServiceKey(String.valueOf(body.getOrDefault("service_key", "")));
        e.setServiceId(String.valueOf(body.getOrDefault("service_id", svc.getServiceId())));
        e.setServiceAlias(serviceAlias);
        e.setServiceName(String.valueOf(body.getOrDefault("service_name", "")));
        e.setTeamId(String.valueOf(body.getOrDefault("team_id", svc.getTenantId())));
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
     * step 3 插件级事件（在 service share record 流程内）：调 region {@code sharePlugin}。
     */
    @PostMapping(value = {"/console/teams/{team_name}/share/{share_id}/events/{event_id}/plugin",
                            "/console/teams/{team_name}/share/{share_id}/events/{event_id}/plugin/"})
    @Transactional
    public ApiResult pluginEvent(@PathVariable("team_name") String teamName,
                                      @PathVariable("share_id") String shareId,
                                      @PathVariable("event_id") String eventId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        Map<String, Object> b = body == null ? Map.of() : body;
        String pluginId = String.valueOf(b.getOrDefault("plugin_id", ""));
        if (pluginId.isBlank()) {
            throw new ServiceHandleException(400, "missing plugin_id", "缺少 plugin_id");
        }

        Tenants tenant = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        String regionName = String.valueOf(b.getOrDefault("region_name",
                r.getTeamName() != null ? r.getTeamName() : ""));
        if (regionName.isBlank() && b.get("service_alias") instanceof String alias && !alias.isBlank()) {
            TenantService svc = serviceRepo.findByTenantIdAndServiceAlias(tenant.getTenantId(), alias)
                    .orElse(null);
            if (svc != null) regionName = svc.getServiceRegion();
        }

        Map<String, Object> regionBody = new LinkedHashMap<>(b);
        regionBody.put("event_id", eventId);
        Map<String, Object> bean = shareOps.sharePlugin(regionName, teamName, pluginId, regionBody);

        ServiceShareRecordEvent e = new ServiceShareRecordEvent();
        e.setRecordId(r.getId());
        e.setRegionShareId(stringOr(bean.get("share_id"), eventId));
        e.setTeamName(teamName);
        e.setServiceKey(String.valueOf(b.getOrDefault("plugin_key", "")));
        e.setServiceId(pluginId);
        e.setServiceAlias(String.valueOf(b.getOrDefault("plugin_alias", pluginId)));
        e.setServiceName(String.valueOf(b.getOrDefault("plugin_name", "")));
        e.setTeamId(tenant.getTenantId());
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
     * step 4 轮询：从 region 拉 status 同步到本地 {@code event_status}。
     */
    @GetMapping(value = {"/console/teams/{team_name}/share/{share_id}/events/{event_id}/status",
                          "/console/teams/{team_name}/share/{share_id}/events/{event_id}/status/"})
    @Transactional
    public ApiResult eventStatus(@PathVariable("team_name") String teamName,
                                       @PathVariable("share_id") String shareId,
                                       @PathVariable("event_id") String eventId) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        ServiceShareRecordEvent e = eventRepo.findByRecordIdAndEventId(r.getId(), eventId)
                .orElseThrow(() -> new ServiceHandleException(404, "event not found", "事件不存在"));

        String regionShareId = e.getRegionShareId();
        if (regionShareId == null || regionShareId.isBlank()) {
            return GeneralMessage.ok(Map.of("event_status", "pending"));
        }

        TenantService svc = resolveService(teamName, e.getServiceAlias());
        Map<String, Object> bean = shareOps.getShareServiceResult(
                svc.getServiceRegion(), teamName, e.getServiceAlias(), regionShareId);
        Object status = bean.get("status");
        if (status instanceof String s && !s.isBlank()) {
            e.setEventStatus(s);
            e.setUpdateTime(LocalDateTime.now());
            eventRepo.save(e);
        }
        return GeneralMessage.ok(bean);
    }

    /**
     * step 5：校验全部 event_status=success 翻 status=1；不调 region。
     */
    @PostMapping(value = {"/console/teams/{team_name}/share/{share_id}/complete",
                            "/console/teams/{team_name}/share/{share_id}/complete/"})
    @Transactional
    public ApiResult complete(@PathVariable("team_name") String teamName,
                                  @PathVariable("share_id") String shareId) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));

        List<ServiceShareRecordEvent> events = eventRepo.findByRecordId(r.getId());
        for (ServiceShareRecordEvent e : events) {
            String s = e.getEventStatus();
            if (s == null || "failure".equalsIgnoreCase(s) || "fail".equalsIgnoreCase(s) || "error".equalsIgnoreCase(s)) {
                throw new ServiceHandleException(409, "share not all success", "存在失败事件，请放弃后重试");
            }
            if (!"success".equalsIgnoreCase(s)) {
                throw new ServiceHandleException(409, "share not finished", "分享尚未完成");
            }
        }

        r.setStatus(1);
        r.setStep(5);
        r.setIsSuccess(true);
        r.setUpdateTime(LocalDateTime.now());
        recordRepo.save(r);
        return GeneralMessage.ok(toBean(r));
    }

    private TenantService resolveService(String teamName, String serviceAlias) {
        Tenants tenant = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        return serviceRepo.findByTenantIdAndServiceAlias(tenant.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    private static String stringOr(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }

    static Map<String, Object> toBean(ServiceShareRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group_share_id", r.getGroupShareId());
        m.put("group_id", r.getGroupId());
        m.put("event_id", r.getEventId());
        m.put("share_version", r.getShareVersion());
        m.put("step", r.getStep());
        m.put("status", r.getStatus());
        m.put("is_success", r.getIsSuccess());
        m.put("share_app_market_name", r.getShareAppMarketName());
        m.put("share_app_model_name", r.getShareAppModelName());
        m.put("create_time", r.getCreateTime());
        m.put("update_time", r.getUpdateTime());
        return m;
    }
}
