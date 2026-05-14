package cn.kuship.console.modules.application.api;

import java.util.Map;

/**
 * 多语言版本管理 region API（业务自治接口，归属 application 模块）。
 *
 * <p>承接 rainbond {@code regionapi.py} 中 {@code lang-version} / {@code cnb/frameworks} 5 个
 * region 调用。method 入参带 {@code enterpriseId} 但 URL 不输出（与既有 Operations 接口约定一致）。
 */
public interface LangVersionOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-build-versions";

    default Map<String, Object> getLangVersion(String enterpriseId, String regionName, String lang,
                                                 String show, String buildStrategy) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> createLangVersion(String enterpriseId, String regionName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> updateLangVersion(String enterpriseId, String regionName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> deleteLangVersion(String enterpriseId, String regionName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> getCnbFrameworks(String enterpriseId, String regionName, String lang) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
