package cn.kuship.console.modules.application.api;

import java.util.List;
import java.util.Map;

/**
 * 组件 node label 与 region 端可用 label 列表 API（业务自治接口，归属 application 模块）。
 *
 * <p>承接 rainbond {@code regionapi.py:337-388} 中 4 个 region method（{@code get_region_labels} /
 * {@code addServiceNodeLabel} / {@code deleteServiceNodeLabel} / {@code update_service_state_label}）。
 */
public interface ServiceLabelOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-service-labels";

    default List<Map<String, Object>> listRegionLabels(String regionName, String tenantName) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> addServiceNodeLabel(String regionName, String tenantName,
                                                     String serviceAlias, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> deleteServiceNodeLabel(String regionName, String tenantName,
                                                       String serviceAlias, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> updateServiceStateLabel(String regionName, String tenantName,
                                                        String serviceAlias, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
