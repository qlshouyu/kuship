package cn.kuship.console.modules.appmarket.share.export.api;

import java.util.Map;

/** rainbond `export_app` / `get_app_export_status` 2 method 透传。 */
public interface AppExportOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-import-export";

    default Map<String, Object> exportApp(String regionName, String enterpriseId, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> getExportStatus(String regionName, String enterpriseId, String eventId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
