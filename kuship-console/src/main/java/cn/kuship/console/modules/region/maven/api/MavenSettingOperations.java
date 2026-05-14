package cn.kuship.console.modules.region.maven.api;

import java.util.List;
import java.util.Map;

/**
 * 企业级 maven 仓库配置 region API（业务自治；归属 region/maven 子域）。
 *
 * <p>承接 rainbond {@code regionapi.py:2123-2168} 中 5 method（list / add / get / update / delete）。
 * URL 路径不含 enterprise_id 段（rainbond 真相），enterprise_id 仅在 console 层做权限校验。
 */
public interface MavenSettingOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-maven-setting";

    /**
     * @param onlyName true 时返回 [{name, is_default}]；false 返回完整内容含 xml content
     */
    default List<Map<String, Object>> listMavenSettings(String enterpriseId, String regionName, boolean onlyName) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> addMavenSetting(String enterpriseId, String regionName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> getMavenSetting(String enterpriseId, String regionName, String name) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> updateMavenSetting(String enterpriseId, String regionName, String name,
                                                   Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> deleteMavenSetting(String enterpriseId, String regionName, String name) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
