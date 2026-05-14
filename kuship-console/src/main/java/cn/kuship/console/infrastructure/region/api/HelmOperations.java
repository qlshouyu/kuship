package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Helm chart / app / release 域。
 *
 * <p>chart 信息接口（chart-information / yaml / upload-chart-*）由
 * {@code migrate-console-app-market} 落地；release 实例接口（list / install /
 * preview / detail / upgrade / uninstall / history / rollback）由
 * {@code migrate-console-helm-release} 落地。两者共享同一组 region URL 前缀
 * {@code /v2/tenants/{tenant_name}/helm/*}，故合并在同一接口。
 */
public interface HelmOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-helm-release";

    // chart 信息（migrate-console-app-market）

    default Map<String, Object> getChartInformation(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> checkHelmApp(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getYamlByChart(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getUploadChartInformation(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getUploadChartValue(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> importUploadChartResource(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    // helm release 实例（migrate-console-helm-release）

    /** GET {@code /v2/tenants/{tenant_name}/helm/releases?namespace={ns}}。 */
    default Map<String, Object> getTenantHelmReleases(String regionName, String tenantName, String namespace) { return unsupported(IMPLEMENTING_CHANGE); }

    /** POST {@code /v2/tenants/{tenant_name}/helm/releases}（含 namespace）。 */
    default Map<String, Object> installTenantHelmRelease(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    /** POST {@code /v2/tenants/{tenant_name}/helm/chart-preview}。 */
    default Map<String, Object> previewTenantHelmChart(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    /** GET {@code /v2/tenants/{tenant_name}/helm/releases/{release_name}?namespace={ns}}。 */
    default Map<String, Object> getTenantHelmReleaseDetail(String regionName, String tenantName, String releaseName, String namespace) { return unsupported(IMPLEMENTING_CHANGE); }

    /** PUT {@code /v2/tenants/{tenant_name}/helm/releases/{release_name}}。 */
    default Map<String, Object> upgradeTenantHelmRelease(String regionName, String tenantName, String releaseName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    /** DELETE {@code /v2/tenants/{tenant_name}/helm/releases/{release_name}?namespace={ns}}。 */
    default void uninstallTenantHelmRelease(String regionName, String tenantName, String releaseName, String namespace) { unsupported(IMPLEMENTING_CHANGE); }

    /** GET {@code /v2/tenants/{tenant_name}/helm/releases/{release_name}/history?namespace={ns}}。 */
    default Map<String, Object> getTenantHelmReleaseHistory(String regionName, String tenantName, String releaseName, String namespace) { return unsupported(IMPLEMENTING_CHANGE); }

    /** POST {@code /v2/tenants/{tenant_name}/helm/releases/{release_name}/rollback}（含 namespace）。 */
    default Map<String, Object> rollbackTenantHelmRelease(String regionName, String tenantName, String releaseName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }
}
