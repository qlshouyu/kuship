package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.EventOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 事件查询：组件级 events / event_log；团队级 events / events/{eventId}/log。 */
@RestController
public class AppEventController {

    private final EventOperations events;
    private final RuntimeContextLoader loader;

    public AppEventController(EventOperations events, RuntimeContextLoader loader) {
        this.events = events;
        this.loader = loader;
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/events",
                          "/console/teams/{team_name}/apps/{service_alias}/events/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult appEvents(@PathVariable("team_name") String teamName,
                                  @PathVariable("service_alias") String alias,
                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                  @RequestParam(value = "page_size", defaultValue = "10") int pageSize) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("service_alias", alias);
        q.put("page", String.valueOf(page));
        q.put("page_size", String.valueOf(pageSize));
        Map<String, Object> resp = events.getTargetEventsList(s.getServiceRegion(), teamName, q);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/event_log",
                          "/console/teams/{team_name}/apps/{service_alias}/event_log/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult eventLog(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String alias,
                                @RequestParam(value = "event_id", required = false) String eventId,
                                @RequestParam(value = "level", required = false) String level) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> body = new LinkedHashMap<>();
        if (eventId != null) body.put("event_id", eventId);
        if (level != null) body.put("level", level);
        Map<String, Object> resp = events.getEventLog(s.getServiceRegion(), teamName, body);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/events", "/console/teams/{team_name}/events/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult teamEvents(@PathVariable("team_name") String teamName,
                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                  @RequestParam(value = "page_size", defaultValue = "10") int pageSize,
                                  @RequestParam(value = "region_name", required = false) String regionName) {
        var team = loader.requireTeam(teamName);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("page", String.valueOf(page));
        q.put("page_size", String.valueOf(pageSize));
        // 团队没有 region 字段；调用方未指定时退化成调一个默认 region；典型场景下前端会带 region_name
        String region = regionName != null && !regionName.isEmpty() ? regionName : "default";
        Map<String, Object> resp = events.getTargetEventsList(region, teamName, q);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/events/{eventId}/log",
                          "/console/teams/{team_name}/events/{eventId}/log/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult teamEventLog(@PathVariable("team_name") String teamName,
                                    @PathVariable("eventId") String eventId,
                                    @RequestParam(value = "region_name", required = false) String regionName) {
        loader.requireTeam(teamName);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event_id", eventId);
        String region = regionName != null && !regionName.isEmpty() ? regionName : "default";
        Map<String, Object> resp = events.getEventLog(region, teamName, body);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }
}
