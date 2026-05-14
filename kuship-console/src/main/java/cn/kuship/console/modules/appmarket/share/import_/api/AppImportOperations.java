package cn.kuship.console.modules.appmarket.share.import_.api;

import java.util.Map;

/** rainbond {@code regionapi.py:1554-1632} —— 10 个 import 透传 method。 */
public interface AppImportOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-import-export";

    /** POST /v2/app/import （enterprise scope）*/
    default Map<String, Object> importApp2Enterprise(String regionName, String enterpriseId, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** POST /v2/app/import （tenant scope）*/
    default Map<String, Object> importApp(String regionName, String tenantName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** GET /v2/app/import/{event_id} */
    default Map<String, Object> getImportStatus(String regionName, String tenantName, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** GET /v2/app/import/ids/{event_id} */
    default Map<String, Object> getEnterpriseImportStatus(String regionName, String enterpriseId, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** GET /v2/app/import/ids/{event_id}（with body）*/
    default Map<String, Object> getEnterpriseImportFileDir(String regionName, String enterpriseId, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** GET /v2/app/import/{event_id}（with body）*/
    default Map<String, Object> getImportFileDir(String regionName, String tenantName, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** DELETE /v2/app/import/ids/{event_id}（删除事件）*/
    default Map<String, Object> deleteEnterpriseImport(String regionName, String enterpriseId, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** DELETE /v2/app/import/{event_id}（删除事件）*/
    default Map<String, Object> deleteImport(String regionName, String tenantName, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** POST /v2/app/import/{event_id}（创建文件目录）*/
    default Map<String, Object> createImportFileDir(String regionName, String tenantName, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** DELETE /v2/app/import/ids/{event_id}（删除文件目录）/ DELETE /v2/app/import/{event_id} */
    default Map<String, Object> deleteImportFileDir(String regionName, String scopeName, String eventId, boolean enterprise) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
