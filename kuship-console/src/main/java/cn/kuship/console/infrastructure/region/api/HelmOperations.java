package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Helm chart / app 域。<b>实现 change：{@code migrate-console-app-market}</b>。
 * 对应 Python {@code get_chart_information}/{@code check_helm_app}/{@code get_yaml_by_chart}/
 * {@code check_upload_chart}/{@code get_upload_chart_*}/{@code import_upload_chart_resource}。
 */
public interface HelmOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-market";

    default Map<String, Object> getChartInformation(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> checkHelmApp(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getYamlByChart(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getUploadChartInformation(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getUploadChartValue(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> importUploadChartResource(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }
}
