package cn.kuship.console.modules.misc.config.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.ConsoleConfig;
import cn.kuship.console.modules.account.repository.ConsoleConfigRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 企业全局配置：复用 ConsoleConfig 表，key 命名 {eid}.{name}。 */
@RestController
public class EnterpriseConfigController {

    private final ConsoleConfigRepository repo;
    private final RequestContext requestContext;

    public EnterpriseConfigController(ConsoleConfigRepository repo, RequestContext requestContext) {
        this.repo = repo;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/console/enterprises/{enterprise_id}/configs",
                          "/console/enterprises/{enterprise_id}/configs/"})
    public ApiResult list(@PathVariable("enterprise_id") String enterpriseId) {
        List<ConsoleConfig> rows = repo.findByKeyStartingWith(enterpriseId + ".%");
        return GeneralMessage.okList(rows.stream().map(EnterpriseConfigController::toBean).toList());
    }

    @PutMapping(value = {"/console/enterprises/{enterprise_id}/configs/{key}",
                          "/console/enterprises/{enterprise_id}/configs/{key}/"})
    @Transactional
    public ApiResult upsert(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("key") String key,
                              @RequestBody Map<String, Object> body) {
        String fullKey = enterpriseId + "." + key;
        ConsoleConfig c = repo.findByKey(fullKey).orElseGet(ConsoleConfig::new);
        c.setKey(fullKey);
        c.setValue(String.valueOf(body.getOrDefault("value", "")));
        Object desc = body.get("description");
        if (desc instanceof String d) c.setDescription(d);
        c.setUpdateTime(LocalDateTime.now());
        c.setUserNickName(requestContext.getUsername() != null ? requestContext.getUsername() : "system");
        repo.save(c);
        return GeneralMessage.ok(toBean(c));
    }

    @DeleteMapping(value = {"/console/enterprises/{enterprise_id}/configs/{key}",
                              "/console/enterprises/{enterprise_id}/configs/{key}/"})
    @Transactional
    public ApiResult delete(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("key") String key) {
        repo.deleteByKey(enterpriseId + "." + key);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/console/enterprise/object_storage", "/console/enterprise/object_storage/"})
    public ApiResult getObjectStorage() {
        return shortcutGet("OBJECT_STORAGE");
    }

    @PutMapping(value = {"/console/enterprise/object_storage", "/console/enterprise/object_storage/"})
    public ApiResult putObjectStorage(@RequestBody Map<String, Object> body) {
        return shortcutPut("OBJECT_STORAGE", body);
    }

    @GetMapping(value = {"/console/enterprise/appstore_image_hub", "/console/enterprise/appstore_image_hub/"})
    public ApiResult getAppstore() {
        return shortcutGet("APPSTORE_IMAGE_HUB");
    }

    @PutMapping(value = {"/console/enterprise/appstore_image_hub", "/console/enterprise/appstore_image_hub/"})
    public ApiResult putAppstore(@RequestBody Map<String, Object> body) {
        return shortcutPut("APPSTORE_IMAGE_HUB", body);
    }

    @GetMapping(value = {"/console/enterprise/{enterprise_id}/visualmonitor",
                          "/console/enterprise/{enterprise_id}/visualmonitor/"})
    public ApiResult getVisualMonitor(@PathVariable("enterprise_id") String enterpriseId) {
        return repo.findByKey(enterpriseId + ".VISUAL_MONITOR")
                .map(c -> GeneralMessage.ok(toBean(c)))
                .orElse(GeneralMessage.ok(Map.of("enabled", false)));
    }

    @GetMapping(value = {"/console/enterprise/{enterprise_id}/alerts",
                          "/console/enterprise/{enterprise_id}/alerts/"})
    public ApiResult getAlerts(@PathVariable("enterprise_id") String enterpriseId) {
        return repo.findByKey(enterpriseId + ".ALERTS")
                .map(c -> GeneralMessage.ok(toBean(c)))
                .orElse(GeneralMessage.okList(java.util.List.of()));
    }

    private ApiResult shortcutGet(String key) {
        return repo.findByKey("global." + key)
                .map(c -> GeneralMessage.ok(toBean(c)))
                .orElse(GeneralMessage.ok(Map.of("value", "", "exists", false)));
    }

    @Transactional
    private ApiResult shortcutPut(String key, Map<String, Object> body) {
        String fullKey = "global." + key;
        ConsoleConfig c = repo.findByKey(fullKey).orElseGet(ConsoleConfig::new);
        c.setKey(fullKey);
        c.setValue(String.valueOf(body.getOrDefault("value", "")));
        c.setUpdateTime(LocalDateTime.now());
        c.setUserNickName(requestContext.getUsername() != null ? requestContext.getUsername() : "system");
        repo.save(c);
        return GeneralMessage.ok(toBean(c));
    }

    static Map<String, Object> toBean(ConsoleConfig c) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("key", c.getKey());
        b.put("value", c.getValue());
        b.put("description", c.getDescription());
        b.put("update_time", c.getUpdateTime());
        b.put("user_nick_name", c.getUserNickName());
        return b;
    }
}
