package cn.kuship.console.modules.appmarket.share.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
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

/** 服务分享流程：record CRUD + info / events / complete / giveup。 */
@RestController
public class ServiceShareController {

    private final ServiceShareRecordRepository recordRepo;
    private final ServiceShareRecordEventRepository eventRepo;

    public ServiceShareController(ServiceShareRecordRepository recordRepo,
                                     ServiceShareRecordEventRepository eventRepo) {
        this.recordRepo = recordRepo;
        this.eventRepo = eventRepo;
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

    @PostMapping(value = {"/console/teams/{team_name}/share/{share_id}/events/{event_id}",
                            "/console/teams/{team_name}/share/{share_id}/events/{event_id}/"})
    @Transactional
    public ApiResult addEvent(@PathVariable("team_name") String teamName,
                                  @PathVariable("share_id") String shareId,
                                  @PathVariable("event_id") String eventId,
                                  @RequestBody Map<String, Object> body) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        ServiceShareRecordEvent e = new ServiceShareRecordEvent();
        e.setRecordId(r.getId());
        e.setRegionShareId(eventId);
        e.setTeamName(teamName);
        e.setServiceKey(String.valueOf(body.getOrDefault("service_key", "")));
        e.setServiceId(String.valueOf(body.getOrDefault("service_id", "")));
        e.setServiceAlias(String.valueOf(body.getOrDefault("service_alias", "")));
        e.setServiceName(String.valueOf(body.getOrDefault("service_name", "")));
        e.setTeamId(String.valueOf(body.getOrDefault("team_id", "")));
        e.setEventId(eventId);
        e.setEventStatus(String.valueOf(body.getOrDefault("event_status", "running")));
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        eventRepo.save(e);
        return GeneralMessage.ok();
    }

    @PostMapping(value = {"/console/teams/{team_name}/share/{share_id}/events/{event_id}/plugin",
                            "/console/teams/{team_name}/share/{share_id}/events/{event_id}/plugin/"})
    public ApiResult pluginEvent(@PathVariable("team_name") String teamName,
                                      @PathVariable("share_id") String shareId,
                                      @PathVariable("event_id") String eventId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        // plugin 阶段单独占位（rainbond 原版用于插件分享子流程），MVP 仅返回 ok
        return GeneralMessage.ok();
    }

    @PostMapping(value = {"/console/teams/{team_name}/share/{share_id}/complete",
                            "/console/teams/{team_name}/share/{share_id}/complete/"})
    @Transactional
    public ApiResult complete(@PathVariable("team_name") String teamName,
                                  @PathVariable("share_id") String shareId) {
        ServiceShareRecord r = recordRepo.findByGroupShareId(shareId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "分享记录不存在"));
        r.setStatus(1);
        r.setStep(5);
        r.setIsSuccess(true);
        r.setUpdateTime(LocalDateTime.now());
        recordRepo.save(r);
        return GeneralMessage.ok(toBean(r));
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
