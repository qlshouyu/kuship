package cn.kuship.console.modules.application.k8sattr.api;

import java.util.List;
import java.util.Map;

/**
 * 组件 k8s 属性 region API（业务自治）。承接 rainbond {@code regionapi.py:2572-2598} 中 4 method。
 *
 * <p>注：rainbond 历史 GET 用了 GET with body 模式，本接口跟随该约定。
 */
public interface K8sAttributeOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-governance-policy";

    /** GET with body 查询单个属性。 */
    default List<Map<String, Object>> getK8sAttribute(String regionName, String tenantName,
                                                       String serviceAlias, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> createK8sAttribute(String regionName, String tenantName,
                                                    String serviceAlias, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> updateK8sAttribute(String regionName, String tenantName,
                                                    String serviceAlias, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> deleteK8sAttribute(String regionName, String tenantName,
                                                    String serviceAlias, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
