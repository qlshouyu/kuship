package cn.kuship.console.modules.appruntime.api;

import java.util.Map;

/** 弹性伸缩规则 region API：xparules CRUD + xparecords 历史。 */
public interface AutoscalerOperations {

    Map<String, Object> createRule(String regionName, String tenantName, String serviceAlias, Map<String, Object> body);

    Map<String, Object> updateRule(String regionName, String tenantName, String serviceAlias, String ruleId, Map<String, Object> body);

    void deleteRule(String regionName, String tenantName, String serviceAlias, String ruleId);

    Map<String, Object> listScalingRecords(String regionName, String tenantName, String serviceAlias, Map<String, String> queryParams);
}
