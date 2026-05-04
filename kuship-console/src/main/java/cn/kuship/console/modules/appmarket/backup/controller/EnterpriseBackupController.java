package cn.kuship.console.modules.appmarket.backup.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.appmarket.backup.repository.ServiceGroupBackupRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 企业级备份列表 / 上传 / 下载（占位 + 列表透传）。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}")
public class EnterpriseBackupController {

    private final ServiceGroupBackupRepository repo;

    public EnterpriseBackupController(ServiceGroupBackupRepository repo) {
        this.repo = repo;
    }

    @GetMapping(value = {"/backups", "/backups/"})
    public ApiResult listBackups(@PathVariable("enterprise_id") String enterpriseId) {
        return GeneralMessage.okList(repo.findAll().stream()
                .map(GroupAppBackupController::toBean).toList());
    }

    @GetMapping(value = {"/backups/{backup_name}", "/backups/{backup_name}/"})
    public ApiResult downloadBackup(@PathVariable("enterprise_id") String enterpriseId,
                                       @PathVariable("backup_name") String backupName) {
        return GeneralMessage.ok(Map.of("backup_name", backupName, "download_url", ""));
    }

    @PostMapping(value = {"/upload-backups", "/upload-backups/"})
    public ApiResult uploadBackup(@PathVariable("enterprise_id") String enterpriseId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(Map.of("uploaded", true));
    }
}
