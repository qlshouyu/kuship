package cn.kuship.console.modules.misc.other.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.PermRelTenantRepository;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 杂项收尾 controller：platform_settings / task_guidance / errlog / team_overview / team_resources / k8s_attribute / k8s_resource。 */
@RestController
public class MiscOtherController {

    private static final Logger log = LoggerFactory.getLogger(MiscOtherController.class);

    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;
    private final RegionInfoEntityRepository regionRepo;
    private final PermRelTenantRepository permRelTenantRepo;

    public MiscOtherController(TenantsRepository tenantsRepo,
                                TenantServiceRepository serviceRepo,
                                RegionInfoEntityRepository regionRepo,
                                PermRelTenantRepository permRelTenantRepo) {
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
        this.regionRepo = regionRepo;
        this.permRelTenantRepo = permRelTenantRepo;
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

    /**
     * 复刻 rainbond {@code views/public_areas.py::TeamOverView.get} 输出契约。
     * UI {@code TeamDashboard/TeamBasicInfo/index.js:573} 通过 {@code overviewInfo.region_health}
     * 判断集群是否可达；缺这个字段会显示 "集群端失去响应" warning。
     *
     * <p>资源使用类字段（cpu_usage / memory_usage / total_disk 等）当前占位 0，
     * 真实值需调 region API {@code /v2/tenants/{tenant_name}/resources}（已在 14 接口骨架的
     * {@code TenantOperations}），后续 follow-up 接通后填充。
     */
    @GetMapping(value = {"/console/teams/{team_name}/overview", "/console/teams/{team_name}/overview/"})
    public ApiResult teamOverview(@PathVariable("team_name") String teamName,
                                   @RequestParam(value = "region_name", required = false) String regionName) {
        Tenants team = tenantsRepo.findByTenantName(teamName).orElse(null);
        if (team == null) {
            return GeneralMessage.ok(Map.of("exists", false));
        }
        long componentCount = serviceRepo.findByTenantId(team.getTenantId()).size();
        long userNums = permRelTenantRepo.findByTenantId(team.getId()).size();

        // region_health：region_info 表中存在该 region 即视为 healthy（resource 接口接通后再加真实探测）
        RegionInfo region = (regionName == null || regionName.isBlank())
                ? null
                : regionRepo.findByRegionName(regionName).orElse(null);
        boolean regionHealth = region != null;

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("user_nums", userNums);
        bean.put("logo", team.getLogo() == null ? "" : team.getLogo());
        bean.put("team_app_num", 0);
        bean.put("team_service_num", componentCount);
        bean.put("eid", team.getEnterpriseId());
        bean.put("team_id", team.getTenantId());
        bean.put("team_service_memory_count", 0);
        bean.put("team_service_total_disk", 0);
        bean.put("team_service_total_cpu", 0);
        bean.put("team_service_total_memory", 0);
        bean.put("team_service_use_cpu", 0);
        bean.put("cpu_usage", 0);
        bean.put("memory_usage", 0);
        bean.put("running_app_num", 0);
        bean.put("running_component_num", 0);
        bean.put("team_alias", team.getTenantAlias());
        bean.put("region_id", region != null ? region.getRegionId() : null);
        bean.put("disk_usage", 0);
        bean.put("region_health", regionHealth);
        // 兼容字段（不影响 dashboard 渲染，留作前端旧分支兜底）
        bean.put("team_name", teamName);
        bean.put("tenant_id", team.getTenantId());
        bean.put("limit_memory", team.getLimitMemory() == null ? 0 : team.getLimitMemory());
        bean.put("component_count", componentCount);
        return GeneralMessage.ok(bean);
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
