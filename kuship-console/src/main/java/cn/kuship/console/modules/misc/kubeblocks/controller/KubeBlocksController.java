package cn.kuship.console.modules.misc.kubeblocks.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.misc.kubeblocks.api.KubeBlocksOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * KubeBlocks 数据库托管 12 endpoint，全部 region API 透传。
 *
 * <p>对齐 rainbond {@code urls.py:1116-1130} 的 7 个 view 共 12 个 HTTP method
 * （restore endpoint 保留 stub，等待 {@code add-kubeblocks-restore} hardening）。
 *
 * <p>路径段 {@code supported_databases / storage_classes / backup_repos}（snake_case）
 * 与 {@code backup-config / restore}（kebab-case）拼写不一致是 rainbond 历史遗留，本 change
 * 严格保留 URL 不修复。
 */
@RestController
public class KubeBlocksController {

    private static final Logger log = LoggerFactory.getLogger(KubeBlocksController.class);

    private final KubeBlocksOperations kubeblocksOps;
    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public KubeBlocksController(KubeBlocksOperations kubeblocksOps,
                                 TenantsRepository tenantsRepo,
                                 TenantServiceRepository serviceRepo) {
        this.kubeblocksOps = kubeblocksOps;
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    // ─── 3 个 region-level GET（不需解析 service） ────────────────────────────

    @GetMapping(value = {"/console/teams/{team_name}/regions/{region_name}/kubeblocks/supported_databases",
                          "/console/teams/{team_name}/regions/{region_name}/kubeblocks/supported_databases/"})
    @RequirePerm(PermCode.TEAM_REGION_DESCRIBE)
    public ApiResult supportedDatabases(@PathVariable("region_name") String region) {
        return GeneralMessage.ok(kubeblocksOps.listSupportedDatabases(region));
    }

    @GetMapping(value = {"/console/teams/{team_name}/regions/{region_name}/kubeblocks/storage_classes",
                          "/console/teams/{team_name}/regions/{region_name}/kubeblocks/storage_classes/"})
    @RequirePerm(PermCode.TEAM_REGION_DESCRIBE)
    public ApiResult storageClasses(@PathVariable("region_name") String region) {
        return GeneralMessage.ok(kubeblocksOps.listStorageClasses(region));
    }

    @GetMapping(value = {"/console/teams/{team_name}/regions/{region_name}/kubeblocks/backup_repos",
                          "/console/teams/{team_name}/regions/{region_name}/kubeblocks/backup_repos/"})
    @RequirePerm(PermCode.TEAM_REGION_DESCRIBE)
    public ApiResult backupRepos(@PathVariable("region_name") String region) {
        return GeneralMessage.ok(kubeblocksOps.listBackupRepos(region));
    }

    // ─── /apps/{service_alias}/kubeblocks/detail GET + PUT ────────────────────

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/detail",
                          "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/detail/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult detail(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String alias) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(kubeblocksOps.getClusterDetail(svc.getServiceRegion(), svc.getServiceId()));
    }

    @PutMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/detail",
                          "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/detail/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult expansion(@PathVariable("team_name") String teamName,
                                 @PathVariable("service_alias") String alias,
                                 @RequestBody(required = false) Map<String, Object> body) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(kubeblocksOps.expansionCluster(svc.getServiceRegion(), svc.getServiceId(), body));
    }

    // ─── /apps/{service_alias}/kubeblocks/backup-config PUT ───────────────────
    // GET 已按决策 6 删除：UI 端改用 detail bean 的 backup_config 字段

    @PutMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backup-config",
                          "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backup-config/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult updateBackupConfig(@PathVariable("team_name") String teamName,
                                          @PathVariable("service_alias") String alias,
                                          @RequestBody(required = false) Map<String, Object> body) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(kubeblocksOps.updateBackupConfig(svc.getServiceRegion(), svc.getServiceId(), body));
    }

    // ─── /apps/{service_alias}/kubeblocks/backups GET + POST + DELETE ────────

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups",
                          "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult listBackups(@PathVariable("team_name") String teamName,
                                   @PathVariable("service_alias") String alias,
                                   @RequestParam(required = false, defaultValue = "1") int page,
                                   @RequestParam(name = "page_size", required = false, defaultValue = "10") int pageSize) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(kubeblocksOps.listClusterBackups(
                svc.getServiceRegion(), svc.getServiceId(), page, pageSize));
    }

    @PostMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups",
                            "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult manualBackup(@PathVariable("team_name") String teamName,
                                    @PathVariable("service_alias") String alias) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(kubeblocksOps.createManualBackup(svc.getServiceRegion(), svc.getServiceId()));
    }

    @DeleteMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups",
                              "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult deleteBackups(@PathVariable("team_name") String teamName,
                                     @PathVariable("service_alias") String alias,
                                     @RequestBody(required = false) Map<String, Object> body) {
        TenantService svc = resolveService(teamName, alias);
        @SuppressWarnings("unchecked")
        List<String> backups = body == null ? List.of()
                : (List<String>) body.getOrDefault("backups", List.of());
        return GeneralMessage.ok(kubeblocksOps.deleteClusterBackups(
                svc.getServiceRegion(), svc.getServiceId(), backups));
    }

    // ─── /apps/{service_alias}/kubeblocks/parameters GET + POST ──────────────

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/parameters",
                          "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/parameters/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult listParameters(@PathVariable("team_name") String teamName,
                                      @PathVariable("service_alias") String alias,
                                      @RequestParam(required = false, defaultValue = "1") int page,
                                      @RequestParam(name = "page_size", required = false, defaultValue = "6") int pageSize,
                                      @RequestParam(required = false) String keyword) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(kubeblocksOps.listClusterParameters(
                svc.getServiceRegion(), svc.getServiceId(), page, pageSize, keyword));
    }

    @PostMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/parameters",
                            "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/parameters/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult updateParameters(@PathVariable("team_name") String teamName,
                                        @PathVariable("service_alias") String alias,
                                        @RequestBody(required = false) Map<String, Object> body) {
        TenantService svc = resolveService(teamName, alias);
        return GeneralMessage.ok(kubeblocksOps.updateClusterParameters(
                svc.getServiceRegion(), svc.getServiceId(), body));
    }

    // ─── /apps/{service_alias}/kubeblocks/restore POST（stub，待 add-kubeblocks-restore） ─

    @PostMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/kubeblocks/restore",
                            "/console/teams/{team_name}/apps/{service_alias}/kubeblocks/restore/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult restore(@PathVariable("service_alias") String alias,
                              @RequestBody(required = false) Map<String, Object> body) {
        // TODO(add-kubeblocks-restore): 接 ServiceOperations.createService + KubeBlocksOperations.restoreFromBackup
        log.info("[KubeBlocks][stub] restore endpoint hit; full restore flow pending add-kubeblocks-restore");
        return GeneralMessage.ok(Map.of("restore_started", true));
    }

    // ─── helper ──────────────────────────────────────────────────────────────

    private TenantService resolveService(String teamName, String serviceAlias) {
        Tenants tenant = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        return serviceRepo.findByTenantIdAndServiceAlias(tenant.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }
}
