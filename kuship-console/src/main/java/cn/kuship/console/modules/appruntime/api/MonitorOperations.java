package cn.kuship.console.modules.appruntime.api;

import java.util.Map;

/** 监控查询域 region API。透传 Prometheus 风格 query / query_range / batch_query / resources。 */
public interface MonitorOperations {

    Map<String, Object> query(String regionName, String tenantName, Map<String, String> queryParams);

    Map<String, Object> queryRange(String regionName, String tenantName, Map<String, String> queryParams);

    Map<String, Object> batchQuery(String regionName, String tenantName, Map<String, String> queryParams);

    Map<String, Object> getServiceResources(String regionName, String tenantName, String serviceAlias);
}
