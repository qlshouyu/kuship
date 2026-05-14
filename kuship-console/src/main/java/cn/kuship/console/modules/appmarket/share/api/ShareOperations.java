package cn.kuship.console.modules.appmarket.share.api;

import java.util.List;
import java.util.Map;

/**
 * 应用 / 插件分享流程 region API 接口（7 method）。
 *
 * <p>承接 rainbond-console {@code regionapi.py:975-1015 / 1331 / 2389} 的 7 个分享相关
 * region 调用。所有 method 默认抛 {@link UnsupportedOperationException} 占位，
 * 由 {@link ShareOperationsImpl}（{@code @Primary @Service}）覆盖。
 *
 * <p>业务自治接口（非 14 核心 region 骨架），归属 {@code modules/appmarket/share/api/}。
 */
public interface ShareOperations {

    String NOT_IMPLEMENTED = "not yet implemented; will be filled in by migrate-console-app-share";

    default Map<String, Object> shareCloudService(String regionName, String tenantName, Map<String, Object> body) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    default Map<String, Object> shareService(String regionName, String tenantName, String serviceAlias,
                                                Map<String, Object> body) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    default Map<String, Object> getShareServiceResult(String regionName, String tenantName, String serviceAlias,
                                                        String regionShareId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    default Map<String, Object> sharePlugin(String regionName, String tenantName, String pluginId,
                                              Map<String, Object> body) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    default Map<String, Object> getSharePluginResult(String regionName, String tenantName, String pluginId,
                                                       String regionShareId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * URL 中**不含** {@code namespace} 段（rainbond Python 端只调 {@code __get_region_access_info}，
     * 是 7 个 method 中唯一例外）。
     */
    default Map<String, Object> getServicePublishStatus(String regionName, String tenantName,
                                                          String serviceKey, String appVersion) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    default List<Object> listAppReleases(String regionName, String tenantName, String regionAppId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
