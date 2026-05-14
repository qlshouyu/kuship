package cn.kuship.console.modules.appmarket.helm.api;

import java.util.Map;

/** rainbond `import_upload_chart_resource`：POST /v2/helm/import_upload_chart_resource。 */
public interface HelmChartImportOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-import-export";

    default Map<String, Object> importUploadChartResource(String regionName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
