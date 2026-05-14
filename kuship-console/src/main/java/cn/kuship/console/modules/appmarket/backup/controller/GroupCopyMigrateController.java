package cn.kuship.console.modules.appmarket.backup.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.appmarket.backup.api.BackupOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 整组应用复制 / 迁移（接 region：copyBackupData / startMigrate / getMigrateStatus）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/groupapp/{group_id}")
public class GroupCopyMigrateController {

    private final BackupOperations backupOps;
    private final ServiceGroupRepository groupRepo;
    private final TenantsRepository tenantsRepo;

    public GroupCopyMigrateController(BackupOperations backupOps,
                                      ServiceGroupRepository groupRepo,
                                      TenantsRepository tenantsRepo) {
        this.backupOps = backupOps;
        this.groupRepo = groupRepo;
        this.tenantsRepo = tenantsRepo;
    }

    @PostMapping(value = {"/copy", "/copy/"})
    public ApiResult copy(@PathVariable("team_name") String teamName,
                          @PathVariable("group_id") Integer groupId,
                          @RequestBody Map<String, Object> body) {
        ServiceGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ServiceHandleException(404, "group not found", "应用不存在"));
        Map<String, Object> safe = body == null ? Map.of() : body;
        Map<String, Object> resp = backupOps.copyBackupData(group.getRegionName(), teamName, safe);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @PostMapping(value = {"/migrate", "/migrate/"})
    public ApiResult migrate(@PathVariable("team_name") String teamName,
                              @PathVariable("group_id") Integer groupId,
                              @RequestBody Map<String, Object> body) {
        Map<String, Object> safe = body == null ? new HashMap<>() : new HashMap<>(body);
        String backupId = (String) safe.get("backup_id");
        String migrateRegion = (String) safe.get("region");
        String targetTeam = (String) safe.get("team");
        if (backupId == null || backupId.isBlank()) {
            throw new ServiceHandleException(400, "backup_id is null", "请指定备份 id");
        }
        if (targetTeam == null || targetTeam.isBlank()) {
            throw new ServiceHandleException(400, "team is null", "请指明要迁移的团队");
        }
        Tenants migrateTeam = tenantsRepo.findByTenantName(targetTeam)
                .orElseThrow(() -> new ServiceHandleException(404, "team is not found",
                        "需要迁移的团队" + targetTeam + "不存在"));

        if (!safe.containsKey("event_id")) {
            safe.put("event_id", UuidGenerator.makeUuid());
        }
        if (!safe.containsKey("group_id")) {
            safe.put("group_id", UuidGenerator.makeUuid());
        }
        if (!safe.containsKey("migrate_type")) {
            safe.put("migrate_type", "migrate");
        }

        ServiceGroup group = groupRepo.findById(groupId).orElse(null);
        String regionName = group != null ? group.getRegionName()
                : (migrateRegion != null ? migrateRegion : "");
        Map<String, Object> resp = backupOps.startMigrate(regionName, teamName, backupId, safe);
        Map<String, Object> bean = resp == null ? new HashMap<>() : new HashMap<>(resp);
        bean.put("event_id", safe.get("event_id"));
        bean.put("target_team_id", migrateTeam.getTenantId());
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/migrate/record", "/migrate/record/"})
    public ApiResult migrateRecord(@PathVariable("team_name") String teamName,
                                    @PathVariable("group_id") Integer groupId,
                                    @RequestParam(value = "backup_id", required = false) String backupId,
                                    @RequestParam(value = "restore_id", required = false) String restoreId) {
        if (restoreId == null || restoreId.isBlank()) {
            throw new ServiceHandleException(400, "restore id is null", "请指明查询的备份ID");
        }
        if (backupId == null || backupId.isBlank()) {
            throw new ServiceHandleException(400, "backup_id is null", "请指明 backup_id");
        }
        ServiceGroup group = groupRepo.findById(groupId).orElse(null);
        String regionName = group != null ? group.getRegionName() : "";
        Map<String, Object> resp = backupOps.getMigrateStatus(regionName, teamName, backupId, restoreId);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }
}
