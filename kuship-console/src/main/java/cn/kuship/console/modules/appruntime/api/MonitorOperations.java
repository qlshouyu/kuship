package cn.kuship.console.modules.appruntime.api;

import java.util.Map;

/**
 * 监控查询域 region API。透传 Prometheus 风格 query / query_range / batch_query / resources，
 * 以及由 {@code migrate-console-monitor-extras} 落地的 4 个新 method（监控指标 / 资源中心事件 /
 * 域名访问 / 服务访问）。
 */
public interface MonitorOperations {

    String NOT_IMPLEMENTED_EXTRAS =
            "not yet implemented; will be filled in by migrate-console-monitor-extras";

    Map<String, Object> query(String regionName, String tenantName, Map<String, String> queryParams);

    Map<String, Object> queryRange(String regionName, String tenantName, Map<String, String> queryParams);

    Map<String, Object> batchQuery(String regionName, String tenantName, Map<String, String> queryParams);

    Map<String, Object> getServiceResources(String regionName, String tenantName, String serviceAlias);

    /** {@code GET /v2/monitor/metrics?target=&tenant=&app=&component=}（区域全局监控端点）。 */
    default Map<String, Object> getMonitorMetrics(String regionName, String tenantId,
                                                    String target, String appId, String componentId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_EXTRAS);
    }

    /** {@code GET /v2/tenants/{tenantName}/resource-center/events?<query>}（团队维度资源对象事件）。 */
    default Map<String, Object> getResourceCenterEvents(String regionName, String tenantName,
                                                          Map<String, String> queryParams) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_EXTRAS);
    }

    /** {@code GET /api/v1/query?<query>} —— PromQL 域名访问聚合查询。 */
    default Map<String, Object> queryDomainAccess(String regionName, String tenantName,
                                                    Map<String, String> queryParams) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_EXTRAS);
    }

    /** {@code GET /api/v1/query?<query>} —— PromQL 服务访问聚合查询。 */
    default Map<String, Object> queryServiceAccess(String regionName, String tenantName,
                                                     Map<String, String> queryParams) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_EXTRAS);
    }
}
