package cn.kuship.console.modules.application.governance.api;

import java.util.List;
import java.util.Map;

/**
 * 应用治理模式 region API（业务自治）。承接 rainbond {@code regionapi.py:2319-2353} 中 5 method。
 */
public interface GovernanceModeOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-governance-policy";

    default List<Map<String, Object>> listGovernanceMode(String regionName, String tenantName) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> checkAppGovernanceMode(String regionName, String tenantName,
                                                       String regionAppId, String governanceMode) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> createGovernanceCr(String regionName, String tenantName,
                                                    String regionAppId, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> updateGovernanceCr(String regionName, String tenantName,
                                                    String regionAppId, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    default Map<String, Object> deleteGovernanceCr(String regionName, String tenantName, String regionAppId) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
