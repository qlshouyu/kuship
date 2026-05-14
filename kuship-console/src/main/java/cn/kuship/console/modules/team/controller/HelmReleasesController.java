package cn.kuship.console.modules.team.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.team.service.HelmReleaseService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /console/teams/{team_name}/regions/{region_name}/helm/*} —— helm release 实例 CRUD。
 *
 * <p>对齐 rainbond {@code console/views/team_resources.py:210-291} 的 5 个 view：
 * HelmReleasesView / HelmChartPreviewView / HelmReleaseDetailView /
 * HelmReleaseHistoryView / HelmReleaseRollbackView。
 *
 * <p>路径变量名严格保留 snake_case；trailing slash 显式列出。
 * 不加 @RequirePerm（与 rainbond {@code TenantHeaderView} 一致，仅依赖 JWT + tenant 上下文）。
 */
@RestController
@RequestMapping("/console")
public class HelmReleasesController {

    private final HelmReleaseService helmReleaseService;
    private final RequestContext requestContext;

    public HelmReleasesController(HelmReleaseService helmReleaseService, RequestContext requestContext) {
        this.helmReleaseService = helmReleaseService;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {
            "/teams/{team_name}/regions/{region_name}/helm/releases",
            "/teams/{team_name}/regions/{region_name}/helm/releases/"})
    public Map<String, Object> listReleases(@PathVariable("team_name") String teamName,
                                              @PathVariable("region_name") String regionName) {
        return helmReleaseService.listReleases(teamName, regionName);
    }

    @PostMapping(value = {
            "/teams/{team_name}/regions/{region_name}/helm/releases",
            "/teams/{team_name}/regions/{region_name}/helm/releases/"})
    public Map<String, Object> installRelease(@PathVariable("team_name") String teamName,
                                                @PathVariable("region_name") String regionName,
                                                @RequestBody(required = false) Map<String, Object> body) {
        return helmReleaseService.installRelease(teamName, regionName, body, currentOperator());
    }

    @PostMapping(value = {
            "/teams/{team_name}/regions/{region_name}/helm/chart-preview",
            "/teams/{team_name}/regions/{region_name}/helm/chart-preview/"})
    public Map<String, Object> previewChart(@PathVariable("team_name") String teamName,
                                              @PathVariable("region_name") String regionName,
                                              @RequestBody(required = false) Map<String, Object> body) {
        return helmReleaseService.previewChart(teamName, regionName, body);
    }

    @GetMapping(value = {
            "/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}",
            "/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/"})
    public Map<String, Object> getReleaseDetail(@PathVariable("team_name") String teamName,
                                                  @PathVariable("region_name") String regionName,
                                                  @PathVariable("release_name") String releaseName) {
        return helmReleaseService.getDetail(teamName, regionName, releaseName);
    }

    @PutMapping(value = {
            "/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}",
            "/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/"})
    public Map<String, Object> upgradeRelease(@PathVariable("team_name") String teamName,
                                                @PathVariable("region_name") String regionName,
                                                @PathVariable("release_name") String releaseName,
                                                @RequestBody(required = false) Map<String, Object> body) {
        return helmReleaseService.upgradeRelease(teamName, regionName, releaseName, body, currentOperator());
    }

    @DeleteMapping(value = {
            "/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}",
            "/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/"})
    public Map<String, Object> uninstallRelease(@PathVariable("team_name") String teamName,
                                                  @PathVariable("region_name") String regionName,
                                                  @PathVariable("release_name") String releaseName) {
        helmReleaseService.uninstallRelease(teamName, regionName, releaseName);
        return Map.of();
    }

    @GetMapping(value = {
            "/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/history",
            "/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/history/"})
    public Map<String, Object> getReleaseHistory(@PathVariable("team_name") String teamName,
                                                   @PathVariable("region_name") String regionName,
                                                   @PathVariable("release_name") String releaseName) {
        return helmReleaseService.getHistory(teamName, regionName, releaseName);
    }

    @PostMapping(value = {
            "/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/rollback",
            "/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/rollback/"})
    public Map<String, Object> rollbackRelease(@PathVariable("team_name") String teamName,
                                                 @PathVariable("region_name") String regionName,
                                                 @PathVariable("release_name") String releaseName,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        return helmReleaseService.rollbackRelease(teamName, regionName, releaseName, body);
    }

    private String currentOperator() {
        if (requestContext.getUsername() != null && !requestContext.getUsername().isBlank()) {
            return requestContext.getUsername();
        }
        return requestContext.getUserId() == null ? "" : String.valueOf(requestContext.getUserId());
    }
}
