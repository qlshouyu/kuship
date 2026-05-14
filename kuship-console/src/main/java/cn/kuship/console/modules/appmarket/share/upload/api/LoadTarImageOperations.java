package cn.kuship.console.modules.appmarket.share.upload.api;

import java.util.Map;

/** rainbond `load_tar_image`：POST /v2/app/load_tar_image。 */
public interface LoadTarImageOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-import-export";

    default Map<String, Object> loadTarImage(String regionName, String tenantName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
