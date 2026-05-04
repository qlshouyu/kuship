package cn.kuship.console.modules.grayrelease.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.grayrelease.service.GrayReleaseService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内部 console 端点：rainbond-ui 前端用于"判断某 service 是否参与灰度"以决定按钮态。
 * 走 JWT 默认认证（与 appruntime 模块一致）。
 */
@RestController
@RequestMapping("/console/teams/{team_name}/regions/{region_name}/apps/{app_id}")
public class GrayReleaseInfoController {

    private final TenantsRepository tenantsRepo;
    private final GrayReleaseService grayReleaseService;

    public GrayReleaseInfoController(TenantsRepository tenantsRepo,
                                        GrayReleaseService grayReleaseService) {
        this.tenantsRepo = tenantsRepo;
        this.grayReleaseService = grayReleaseService;
    }

    @PostMapping(value = {"/gray-release-info", "/gray-release-info/"})
    public Map<String, Object> grayReleaseInfo(@PathVariable("team_name") String teamName,
                                                    @PathVariable("region_name") String regionName,
                                                    @PathVariable("app_id") Integer appId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        if (body == null) body = Map.of();
        Object svc = body.get("service_id");
        Object grp = body.get("upgrade_group_id");
        if (svc instanceof String s && !s.isBlank()) {
            return grayReleaseService.getInfoByService(team.getTenantId(), s);
        }
        if (grp != null) {
            Integer gid = grp instanceof Number n ? n.intValue() : Integer.parseInt(grp.toString());
            return grayReleaseService.getInfoByUpgradeGroupId(team.getTenantId(), appId, gid);
        }
        throw new ServiceHandleException(400, "missing service_id or upgrade_group_id",
                "缺少 service_id 或 upgrade_group_id");
    }

    @GetMapping(value = {"/gray-releases", "/gray-releases/"})
    public Object listAppGrayReleases(@PathVariable("team_name") String teamName,
                                          @PathVariable("region_name") String regionName,
                                          @PathVariable("app_id") Integer appId) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        return grayReleaseService.listByApp(appId).stream()
                .filter(r -> team.getTenantId().equals(r.getTenantId()))
                .map(cn.kuship.console.modules.grayrelease.dto.GrayReleaseRecordDto::from)
                .toList();
    }
}
