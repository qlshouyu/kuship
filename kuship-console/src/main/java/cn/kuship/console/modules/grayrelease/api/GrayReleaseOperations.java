package cn.kuship.console.modules.grayrelease.api;

import java.util.Map;

/**
 * 灰度发布 region API 接口（业务自治接口，归属 grayrelease 模块）。
 *
 * <p>承接 rainbond {@code regionapi.py:2937-2960} 的 3 个灰度发布 region 调用：
 * create / update / operate（rollback）。与 {@code ApisixRouteWeightUpdater} 职责分层 ——
 * 本接口走"命令面"（让 region 灰度对象的 desired_replicas / strategy 同步），
 * {@code ApisixRouteWeightUpdater} 走"数据面"（流量切换）。
 *
 * <p>本 change：{@code migrate-console-grayrelease-finalize}。
 */
public interface GrayReleaseOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-grayrelease-finalize";

    /** {@code POST /v2/tenants/{namespace}/apps/{regionAppId}/gray_release} */
    default Map<String, Object> createAppGrayRelease(String regionName, String tenantName,
                                                       Integer regionAppId, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** {@code PUT /v2/tenants/{namespace}/apps/{regionAppId}/gray_release} */
    default Map<String, Object> updateAppGrayRelease(String regionName, String tenantName,
                                                       Integer regionAppId, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /**
     * {@code PUT /v2/tenants/{namespace}/apps/{regionAppId}/operate_gray_release?namespace=&app_id=&operation_method=}
     * <p>{@code operationMethod} 当前已知值：{@code rollback}（其它待 hardening 扩展）。
     */
    default Map<String, Object> operateAppGrayRelease(String regionName, String tenantName,
                                                        Integer regionAppId, String namespace,
                                                        String operationMethod) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
