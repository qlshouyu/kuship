package cn.kuship.console.modules.application.api;

import java.util.Map;

/**
 * 批量组件操作 region API（业务自治接口，归属 application 模块）。
 *
 * <p>承接 rainbond {@code regionapi.py:batchoperation_service} 单 method 透传，
 * 使用 {@code Resource-Validation: true} header 与 region 端约定。
 */
public interface BatchServiceOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-build-versions";

    default Map<String, Object> batchOperationService(String regionName, String tenantName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
