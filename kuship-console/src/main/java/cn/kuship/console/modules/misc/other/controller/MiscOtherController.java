package cn.kuship.console.modules.misc.other.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 杂项收尾 controller：platform_settings / task_guidance / errlog / team_overview / team_resources / k8s_attribute / k8s_resource。 */
@RestController
public class MiscOtherController {

    private static final Logger log = LoggerFactory.getLogger(MiscOtherController.class);

    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public MiscOtherController(TenantsRepository tenantsRepo, TenantServiceRepository serviceRepo) {
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping(value = {"/console/platform-settings", "/console/platform-settings/"})
    public ApiResult platformSettings() {
        return GeneralMessage.ok(Map.of(
                "type", "community",
                "version", "0.1.0-SNAPSHOT",
                "commit_id", "unknown"));
    }

    @GetMapping(value = {"/console/task-guidance", "/console/task-guidance/"})
    public ApiResult taskGuidance() {
        return GeneralMessage.okList(List.of());
    }

    @PostMapping(value = {"/console/errlog", "/console/errlog/"})
    public ApiResult errLog(@RequestBody(required = false) Map<String, Object> body) {
        if (body != null) {
            log.error("[errlog from frontend] msg={} stack={}",
                    body.get("msg"), body.get("stack"));
        }
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/console/teams/{team_name}/overview", "/console/teams/{team_name}/overview/"})
    public ApiResult teamOverview(@PathVariable("team_name") String teamName) {
        Tenants team = tenantsRepo.findByTenantName(teamName).orElse(null);
        if (team == null) {
            return GeneralMessage.ok(Map.of("exists", false));
        }
        long componentCount = serviceRepo.findByTenantId(team.getTenantId()).size();
        return GeneralMessage.ok(Map.of(
                "team_name", teamName,
                "tenant_id", team.getTenantId(),
                "limit_memory", team.getLimitMemory() == null ? 0 : team.getLimitMemory(),
                "component_count", componentCount));
    }

    @GetMapping(value = {"/console/teams/{team_name}/resources", "/console/teams/{team_name}/resources/"})
    public ApiResult teamResources(@PathVariable("team_name") String teamName) {
        Tenants team = tenantsRepo.findByTenantName(teamName).orElse(null);
        if (team == null) {
            return GeneralMessage.ok(Map.of("exists", false));
        }
        var services = serviceRepo.findByTenantId(team.getTenantId());
        int usedMemory = services.stream()
                .mapToInt(s -> s.getMinMemory() == null ? 0 : s.getMinMemory()
                        * (s.getMinNode() == null ? 1 : s.getMinNode()))
                .sum();
        return GeneralMessage.ok(Map.of(
                "team_name", teamName,
                "limit_memory", team.getLimitMemory() == null ? 0 : team.getLimitMemory(),
                "used_memory", usedMemory,
                "component_count", services.size()));
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/k8s_attributes",
                          "/console/teams/{team_name}/apps/{service_alias}/k8s_attributes/"})
    public ApiResult k8sAttributes(@PathVariable("team_name") String teamName,
                                       @PathVariable("service_alias") String alias) {
        return GeneralMessage.okList(List.of());
    }

    @PostMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/k8s_attributes",
                            "/console/teams/{team_name}/apps/{service_alias}/k8s_attributes/"})
    public ApiResult addK8sAttribute(@PathVariable("team_name") String teamName,
                                          @PathVariable("service_alias") String alias,
                                          @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(Map.of("added", true));
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/k8s_resources",
                          "/console/teams/{team_name}/apps/{service_alias}/k8s_resources/"})
    public ApiResult k8sResources(@PathVariable("team_name") String teamName,
                                       @PathVariable("service_alias") String alias) {
        return GeneralMessage.okList(List.of());
    }
}
