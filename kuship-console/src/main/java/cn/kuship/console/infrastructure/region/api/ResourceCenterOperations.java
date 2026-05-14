package cn.kuship.console.infrastructure.region.api;

import org.springframework.http.ResponseEntity;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * 资源中心域：NS 资源管理 + Helm Release 生命周期 + 资源中心工作负载/Pod/事件/日志。
 *
 * <p>实现 change：{@code migrate-console-resource-center}。
 * 对应 Python {@code apiclient/regionapi.py} 3670-3847 行。
 */
public interface ResourceCenterOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-resource-center";

    // ---- NS 资源（namespace-scoped K8s 资源）----

    /** 获取 namespace-scoped 资源类型列表。对应 {@code get_tenant_ns_resource_types}。 */
    default Map<String, Object> getNsResourceTypes(String regionName, String tenantName) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 获取 namespace-scoped 资源列表（带 query params）。对应 {@code get_tenant_ns_resources}。 */
    default Map<String, Object> getNsResources(String regionName, String tenantName, Map<String, String> params) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 获取单个 namespace-scoped 资源详情。对应 {@code get_tenant_ns_resource}。 */
    default Map<String, Object> getNsResource(String regionName, String tenantName, String name,
                                               Map<String, String> params) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * 创建 namespace-scoped 资源（raw body + Content-Type 透传）。
     * 对应 {@code post_tenant_ns_resource}。返回 region 原始响应体（含状态码）。
     */
    default ResponseEntity<byte[]> postNsResource(String regionName, String tenantName, byte[] body,
                                                   Map<String, String> params, String contentType) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * 更新 namespace-scoped 资源（raw body + Content-Type 透传）。
     * 对应 {@code put_tenant_ns_resource}。
     */
    default Map<String, Object> putNsResource(String regionName, String tenantName, String name,
                                               byte[] body, Map<String, String> params, String contentType) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 删除 namespace-scoped 资源。对应 {@code delete_tenant_ns_resource}。 */
    default void deleteNsResource(String regionName, String tenantName, String name,
                                   Map<String, String> params) {
        unsupported(IMPLEMENTING_CHANGE);
    }

    // ---- Helm Release 生命周期 ----

    /** 获取 Helm release 列表。对应 {@code get_tenant_helm_releases}。 */
    default Map<String, Object> getHelmReleases(String regionName, String tenantName, String namespace) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 安装 Helm release。对应 {@code install_tenant_helm_release}。 */
    default Map<String, Object> installHelmRelease(String regionName, String tenantName,
                                                    Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 预览 Helm chart。对应 {@code preview_tenant_helm_chart}。 */
    default Map<String, Object> previewHelmChart(String regionName, String tenantName,
                                                  Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 获取 Helm release 历史版本。对应 {@code get_tenant_helm_release_history}。 */
    default Map<String, Object> getHelmReleaseHistory(String regionName, String tenantName,
                                                       String releaseName, String namespace) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 获取 Helm release 详情。对应 {@code get_tenant_helm_release_detail}。 */
    default Map<String, Object> getHelmReleaseDetail(String regionName, String tenantName,
                                                      String releaseName, String namespace) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 升级 Helm release。对应 {@code upgrade_tenant_helm_release}。 */
    default Map<String, Object> upgradeHelmRelease(String regionName, String tenantName,
                                                    String releaseName, Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 回滚 Helm release。对应 {@code rollback_tenant_helm_release}。 */
    default Map<String, Object> rollbackHelmRelease(String regionName, String tenantName,
                                                     String releaseName, Map<String, Object> body) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * 卸载 Helm release。对应 {@code uninstall_tenant_helm_release}。
     * DELETE 可能不带 body；namespace 作为 query 参数传入。
     */
    default void uninstallHelmRelease(String regionName, String tenantName,
                                       String releaseName, String namespace) {
        unsupported(IMPLEMENTING_CHANGE);
    }

    // ---- 资源中心（Resource Center）工作负载 / Pod / 事件 / 日志 ----

    /** 获取资源中心工作负载详情。对应 {@code get_resource_center_workload_detail}。 */
    default Map<String, Object> getWorkloadDetail(String regionName, String tenantName,
                                                   String resource, String name,
                                                   Map<String, String> params) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 获取资源中心容器组详情。对应 {@code get_resource_center_pod_detail}。 */
    default Map<String, Object> getPodDetail(String regionName, String tenantName, String podName) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /** 获取资源中心对象事件。对应 {@code get_resource_center_events}。 */
    default Map<String, Object> getEvents(String regionName, String tenantName,
                                          Map<String, String> params) {
        return unsupported(IMPLEMENTING_CHANGE);
    }

    /**
     * 获取资源中心 Pod 日志流（SSE 透传）。对应 {@code get_resource_center_pod_log}。
     * 返回 region 的原始响应流，由 controller 以 StreamingResponseBody 方式写出。
     */
    default java.io.InputStream getPodLogStream(String regionName, String tenantName,
                                                  String podName, Map<String, String> params) {
        return unsupported(IMPLEMENTING_CHANGE);
    }
}
