package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Service 生命周期管理（启停/重启/构建/升级/回滚/扩缩容）域。
 * <b>实现 change：{@code migrate-console-app-runtime}</b>。
 *
 * <p>对应 Python {@code start_service}/{@code stop_service}/{@code restart_service}/
 * {@code rollback}/{@code upgrade_service}/{@code horizontal_upgrade}/{@code vertical_upgrade}/
 * {@code change_memory}/{@code pause_service}/{@code un_pause_service} 等。
 */
public interface ServiceLifecycleOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-runtime";

    default Map<String, Object> startService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> stopService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> restartService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> upgradeService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> rollback(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> horizontalUpgrade(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> verticalUpgrade(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> changeMemory(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> pauseService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> unpauseService(String regionName, String tenantName, String serviceAlias, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }
}
