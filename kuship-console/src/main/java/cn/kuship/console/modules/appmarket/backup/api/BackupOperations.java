package cn.kuship.console.modules.appmarket.backup.api;

import java.util.Map;

/** 整组应用备份 region API（非 14 接口骨架，本 change 新增）。 */
public interface BackupOperations {

    Map<String, Object> backup(String regionName, String tenantName, String groupId, Map<String, Object> body);

    Map<String, Object> backupStatus(String regionName, String tenantName, String backupId);

    Map<String, Object> restore(String regionName, String tenantName, Map<String, Object> body);

    Map<String, Object> export(String regionName, String tenantName, String backupId);
}
