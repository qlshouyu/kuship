package cn.kuship.console.modules.appmarket.backup.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.appmarket.backup.api.BackupOperations;
import cn.kuship.console.modules.appmarket.backup.entity.ServiceGroupBackup;
import cn.kuship.console.modules.appmarket.backup.repository.ServiceGroupBackupRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 整组应用备份：触发 / 状态 / 导出 / 导入。 */
@RestController
public class GroupAppBackupController {

    private final ServiceGroupBackupRepository backupRepo;
    private final BackupOperations backupOps;
    private final ServiceGroupRepository groupRepo;
    private final TenantsRepository tenantsRepo;
    private final RequestContext requestContext;

    public GroupAppBackupController(ServiceGroupBackupRepository backupRepo,
                                       BackupOperations backupOps,
                                       ServiceGroupRepository groupRepo,
                                       TenantsRepository tenantsRepo,
                                       RequestContext requestContext) {
        this.backupRepo = backupRepo;
        this.backupOps = backupOps;
        this.groupRepo = groupRepo;
        this.tenantsRepo = tenantsRepo;
        this.requestContext = requestContext;
    }

    @PostMapping(value = {"/console/teams/{team_name}/groupapp/{group_id}/backup",
                            "/console/teams/{team_name}/groupapp/{group_id}/backup/"})
    @Transactional
    public ApiResult startBackup(@PathVariable("team_name") String teamName,
                                    @PathVariable("group_id") Integer groupId,
                                    @RequestBody(required = false) Map<String, Object> body) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        ServiceGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ServiceHandleException(404, "group not found", "应用不存在"));
        Map<String, Object> req = body != null ? body : Map.of();

        ServiceGroupBackup b = new ServiceGroupBackup();
        b.setGroupId(groupId);
        b.setBackupId(java.util.UUID.randomUUID().toString());
        b.setGroupUuid(UuidGenerator.makeUuid());
        b.setTeamId(team.getTenantId());
        b.setUser(requestContext.getUsername() != null ? requestContext.getUsername() : "system");
        b.setRegion(group.getRegionName());
        b.setStatus("starting");
        b.setNote((String) req.getOrDefault("note", ""));
        b.setMode((String) req.getOrDefault("mode", "full"));
        b.setBackupSize(0L);
        b.setTotalMemory(0);
        b.setSourceType((String) req.getOrDefault("source_type", "local"));
        b.setCreateTime(LocalDateTime.now());
        backupRepo.save(b);

        try {
            Map<String, Object> resp = backupOps.backup(group.getRegionName(), teamName, String.valueOf(groupId), req);
            if (resp != null && resp.get("event_id") != null) {
                b.setEventId(resp.get("event_id").toString());
                backupRepo.save(b);
            }
        } catch (Exception e) {
            b.setStatus("failed");
            backupRepo.save(b);
            throw new ServiceHandleException(500, "region backup failed: " + e.getMessage(), "备份触发失败");
        }
        return GeneralMessage.ok(toBean(b));
    }

    @GetMapping(value = {"/console/teams/{team_name}/groupapp/{group_id}/backup/all_status",
                          "/console/teams/{team_name}/groupapp/{group_id}/backup/all_status/"})
    public ApiResult listStatus(@PathVariable("team_name") String teamName,
                                    @PathVariable("group_id") Integer groupId) {
        return GeneralMessage.okList(backupRepo.findByGroupIdOrderByCreateTimeDesc(groupId).stream()
                .map(GroupAppBackupController::toBean).toList());
    }

    @GetMapping(value = {"/console/teams/{team_name}/groupapp/{group_id}/backup/export",
                          "/console/teams/{team_name}/groupapp/{group_id}/backup/export/"})
    public ApiResult export(@PathVariable("team_name") String teamName,
                                @PathVariable("group_id") Integer groupId,
                                @RequestParam("backup_id") String backupId) {
        ServiceGroupBackup b = backupRepo.findByBackupId(backupId)
                .orElseThrow(() -> new ServiceHandleException(404, "backup not found", "备份不存在"));
        Map<String, Object> resp = backupOps.export(b.getRegion(), teamName, backupId);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @PostMapping(value = {"/console/teams/{team_name}/groupapp/{group_id}/backup/import",
                            "/console/teams/{team_name}/groupapp/{group_id}/backup/import/"})
    public ApiResult importBackup(@PathVariable("team_name") String teamName,
                                       @PathVariable("group_id") Integer groupId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        ServiceGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ServiceHandleException(404, "group not found", "应用不存在"));
        Map<String, Object> resp = backupOps.restore(group.getRegionName(), teamName,
                body != null ? body : Map.of());
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/groupapp/backup",
                          "/console/teams/{team_name}/groupapp/backup/"})
    public ApiResult listTeamBackups(@PathVariable("team_name") String teamName) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        List<ServiceGroupBackup> all = backupRepo.findByTeamId(team.getTenantId());
        return GeneralMessage.okList(all.stream().map(GroupAppBackupController::toBean).toList());
    }

    @GetMapping(value = {"/console/teams/{team_name}/all/groupapp/backup",
                          "/console/teams/{team_name}/all/groupapp/backup/"})
    public ApiResult listAllEnterpriseBackups(@PathVariable("team_name") String teamName) {
        return GeneralMessage.okList(backupRepo.findAll().stream()
                .map(GroupAppBackupController::toBean).toList());
    }

    static Map<String, Object> toBean(ServiceGroupBackup b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("backup_id", b.getBackupId());
        m.put("group_id", b.getGroupId());
        m.put("event_id", b.getEventId());
        m.put("status", b.getStatus());
        m.put("note", b.getNote());
        m.put("mode", b.getMode());
        m.put("region", b.getRegion());
        m.put("backup_size", b.getBackupSize());
        m.put("create_time", b.getCreateTime());
        return m;
    }
}
