package cn.kuship.console.modules.application.governance.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.RegionApp;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.governance.api.GovernanceModeOperations;
import cn.kuship.console.modules.application.repository.RegionAppRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /console/teams/{team_name}/groups/{app_id}/governancemode*}：应用治理模式 region 透传。 */
@RestController
@RequestMapping("/console/teams/{team_name}/groups/{app_id}")
public class AppGovernanceModeController {

    private final GovernanceModeOperations governanceOps;
    private final ServiceGroupRepository groupRepo;
    private final RegionAppRepository regionAppRepo;
    private final TenantsRepository tenantsRepo;

    public AppGovernanceModeController(GovernanceModeOperations governanceOps,
                                        ServiceGroupRepository groupRepo,
                                        RegionAppRepository regionAppRepo,
                                        TenantsRepository tenantsRepo) {
        this.governanceOps = governanceOps;
        this.groupRepo = groupRepo;
        this.regionAppRepo = regionAppRepo;
        this.tenantsRepo = tenantsRepo;
    }

    @GetMapping(value = {"/governancemode/available", "/governancemode/available/"})
    public ApiResult listGovernanceMode(@PathVariable("team_name") String teamName,
                                         @PathVariable("app_id") Integer appId) {
        ServiceGroup group = requireGroup(appId);
        List<Map<String, Object>> list = governanceOps.listGovernanceMode(group.getRegionName(), teamName);
        return GeneralMessage.okList(list);
    }

    @PutMapping(value = {"/governancemode/sync", "/governancemode/sync/"})
    public ApiResult updateGovernanceMode(@PathVariable("team_name") String teamName,
                                           @PathVariable("app_id") Integer appId,
                                           @RequestBody Map<String, Object> body) {
        ServiceGroup group = requireGroup(appId);
        Object modeObj = body == null ? null : body.get("governance_mode");
        if (modeObj == null || modeObj.toString().isBlank()) {
            throw new ServiceHandleException(400, "governance_mode is null", "请指定治理模式");
        }
        String mode = modeObj.toString();
        String action = body.get("action") == null ? null : body.get("action").toString();
        String regionAppId = resolveRegionAppId(appId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("governance_mode", mode);

        if ("create".equals(action)) {
            Map<String, Object> cr = governanceOps.createGovernanceCr(group.getRegionName(), teamName, regionAppId, body);
            if (cr != null && !cr.isEmpty()) {
                result.put("governance_cr", cr);
            }
        } else if ("update".equals(action)) {
            Map<String, Object> cr = governanceOps.updateGovernanceCr(group.getRegionName(), teamName, regionAppId, body);
            if (cr != null && !cr.isEmpty()) {
                result.put("governance_cr", cr);
            }
        } else if ("delete".equals(action)) {
            governanceOps.deleteGovernanceCr(group.getRegionName(), teamName, regionAppId);
        }

        group.setGovernanceMode(mode);
        groupRepo.save(group);
        return GeneralMessage.ok(result);
    }

    @PostMapping(value = {"/governancemode-cr", "/governancemode-cr/"})
    public ApiResult createCr(@PathVariable("team_name") String teamName,
                               @PathVariable("app_id") Integer appId,
                               @RequestBody Map<String, Object> body) {
        ServiceGroup group = requireGroup(appId);
        Map<String, Object> cr = body == null ? Map.of() : body;
        Object inner = cr.get("governance_cr");
        Map<String, Object> bean = governanceOps.createGovernanceCr(group.getRegionName(), teamName,
                resolveRegionAppId(appId), inner instanceof Map ? (Map<String, Object>) inner : cr);
        return GeneralMessage.ok(bean == null ? Map.of() : bean);
    }

    @PutMapping(value = {"/governancemode-cr", "/governancemode-cr/"})
    public ApiResult updateCr(@PathVariable("team_name") String teamName,
                               @PathVariable("app_id") Integer appId,
                               @RequestBody Map<String, Object> body) {
        ServiceGroup group = requireGroup(appId);
        Object inner = body == null ? null : body.get("governance_cr");
        Map<String, Object> bean = governanceOps.updateGovernanceCr(group.getRegionName(), teamName,
                resolveRegionAppId(appId), inner instanceof Map ? (Map<String, Object>) inner : body);
        return GeneralMessage.ok(bean == null ? Map.of() : bean);
    }

    @DeleteMapping(value = {"/governancemode-cr", "/governancemode-cr/"})
    public ApiResult deleteCr(@PathVariable("team_name") String teamName,
                               @PathVariable("app_id") Integer appId) {
        ServiceGroup group = requireGroup(appId);
        governanceOps.deleteGovernanceCr(group.getRegionName(), teamName, resolveRegionAppId(appId));
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/governancemode/check", "/governancemode/check/"})
    public ApiResult checkGovernanceMode(@PathVariable("team_name") String teamName,
                                          @PathVariable("app_id") Integer appId,
                                          @RequestParam("governance_mode") String mode) {
        ServiceGroup group = requireGroup(appId);
        Map<String, Object> bean = governanceOps.checkAppGovernanceMode(group.getRegionName(), teamName,
                resolveRegionAppId(appId), mode);
        Map<String, Object> result = bean == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bean);
        result.putIfAbsent("governance_mode", mode);
        return GeneralMessage.ok(result);
    }

    private ServiceGroup requireGroup(Integer appId) {
        return groupRepo.findById(appId)
                .orElseThrow(() -> new ServiceHandleException(404, "app not found", "应用不存在"));
    }

    private String resolveRegionAppId(Integer appId) {
        return regionAppRepo.findFirstByAppId(appId)
                .map(RegionApp::getRegionAppId)
                .orElse(String.valueOf(appId));
    }
}
