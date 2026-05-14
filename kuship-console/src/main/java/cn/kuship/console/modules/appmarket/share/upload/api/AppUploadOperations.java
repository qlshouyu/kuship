package cn.kuship.console.modules.appmarket.share.upload.api;

import java.util.Map;

/** rainbond {@code regionapi.py:1638-1666}：4 个 upload event method。 */
public interface AppUploadOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-import-export";

    /** POST /v2/app/upload/events/{event_id} */
    default Map<String, Object> createUploadDir(String regionName, String tenantName, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** GET /v2/app/upload/events/{event_id} */
    default Map<String, Object> getUploadDir(String regionName, String tenantName, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** DELETE /v2/app/upload/events/{event_id} */
    default Map<String, Object> deleteUploadDir(String regionName, String tenantName, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** PUT /v2/app/upload/events/{event_id}/component_id/{component_id} */
    default Map<String, Object> updateUploadDir(String regionName, String tenantName, String eventId, String componentId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
