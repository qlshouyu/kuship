package cn.kuship.console.modules.appmarket.backup.api;

import java.util.List;
import java.util.Map;

/**
 * 整组应用备份 region API（业务自治非 14 骨架）。
 *
 * <p>覆盖 rainbond {@code regionapi.py:1685-1750} 中 7 个 backup 相关 region method：
 * <ul>
 *   <li>{@link #backup} ←→ {@code backup_group_apps}</li>
 *   <li>{@link #backupStatus} ←→ {@code get_backup_status_by_backup_id}</li>
 *   <li>{@link #deleteBackup} ←→ {@code delete_backup_by_backup_id}（migrate-console-backup-extras 新增）</li>
 *   <li>{@link #listBackupsByGroupUuid} ←→ {@code get_backup_status_by_group_id}（migrate-console-backup-extras 新增）</li>
 *   <li>{@link #startMigrate} ←→ {@code star_apps_migrate_task}（migrate-console-backup-extras 新增）</li>
 *   <li>{@link #getMigrateStatus} ←→ {@code get_apps_migrate_status}（migrate-console-backup-extras 新增）</li>
 *   <li>{@link #copyBackupData} ←→ {@code copy_backup_data}（migrate-console-backup-extras 新增）</li>
 * </ul>
 */
public interface BackupOperations {

    /** POST /v2/tenants/{tn}/groupapp/backups（group_id 入 body）。 */
    Map<String, Object> backup(String regionName, String tenantName, String groupId, Map<String, Object> body);

    /** GET /v2/tenants/{tn}/groupapp/backups/{backup_id}。 */
    Map<String, Object> backupStatus(String regionName, String tenantName, String backupId);

    /**
     * @deprecated 由 {@link #startMigrate} 取代；rainbond region 不存在 {@code /groupapp/backup/restore} 路径
     */
    @Deprecated
    Map<String, Object> restore(String regionName, String tenantName, Map<String, Object> body);

    /**
     * @deprecated console 内部 json 导出逻辑，应由本地 {@code ServiceGroupBackup} 序列化；rainbond region 不存在此 URL
     */
    @Deprecated
    Map<String, Object> export(String regionName, String tenantName, String backupId);

    /** DELETE /v2/tenants/{tn}/groupapp/backups/{backup_id}。 */
    Map<String, Object> deleteBackup(String regionName, String tenantName, String backupId);

    /** GET /v2/tenants/{tn}/groupapp/backups?group_id={group_uuid}。 */
    List<Map<String, Object>> listBackupsByGroupUuid(String regionName, String tenantName, String groupUuid);

    /** POST /v2/tenants/{tn}/groupapp/backups/{backup_id}/restore。 */
    Map<String, Object> startMigrate(String regionName, String tenantName, String backupId, Map<String, Object> body);

    /** GET /v2/tenants/{tn}/groupapp/backups/{backup_id}/restore/{restore_id}（404 兼容返回 not_found）。 */
    Map<String, Object> getMigrateStatus(String regionName, String tenantName, String backupId, String restoreId);

    /** POST /v2/tenants/{tn}/groupapp/backupcopy。 */
    Map<String, Object> copyBackupData(String regionName, String tenantName, Map<String, Object> body);
}
