package cn.kuship.console.modules.misc.kubeblocks.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** KubeBlocks 数据库 8 endpoint，全部 region 透传。MVP 占位返回。 */
@RestController
public class KubeBlocksController {

    @GetMapping(value = {"/console/teams/{team_name}/regions/{region_name}/kubeblocks/supported_databases",
                          "/console/teams/{team_name}/regions/{region_name}/kubeblocks/supported_databases/"})
    public ApiResult supportedDatabases(@PathVariable("region_name") String region) {
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/console/teams/{team_name}/regions/{region_name}/kubeblocks/storage_classes",
                          "/console/teams/{team_name}/regions/{region_name}/kubeblocks/storage_classes/"})
    public ApiResult storageClasses(@PathVariable("region_name") String region) {
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/console/teams/{team_name}/regions/{region_name}/kubeblocks/backup_repos",
                          "/console/teams/{team_name}/regions/{region_name}/kubeblocks/backup_repos/"})
    public ApiResult backupRepos(@PathVariable("region_name") String region) {
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/detail",
                          "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/detail/"})
    public ApiResult detail(@PathVariable("service_alias") String alias) {
        return GeneralMessage.ok(Map.of("service_alias", alias, "kubeblocks", false));
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backup-config",
                          "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backup-config/"})
    public ApiResult backupConfig(@PathVariable("service_alias") String alias) {
        return GeneralMessage.ok(Map.of("backup_enabled", false));
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups",
                          "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups/"})
    public ApiResult backups(@PathVariable("service_alias") String alias) {
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/parameters",
                          "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/parameters/"})
    public ApiResult parameters(@PathVariable("service_alias") String alias) {
        return GeneralMessage.okList(List.of());
    }

    @PostMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/restore",
                            "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/restore/"})
    public ApiResult restore(@PathVariable("service_alias") String alias,
                                @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(Map.of("restore_started", true));
    }
}
