package cn.kuship.console.infrastructure.region.api;

import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * 事件查询域。<b>实现 change：{@code migrate-console-app-runtime}</b>。
 * 对应 Python {@code get_event_log}/{@code get_target_events_list}/{@code get_myteams_events_list}。
 */
public interface EventOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-runtime";

    default Map<String, Object> getEventLog(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getTargetEventsList(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getMyteamsEventsList(String regionName, String tenantName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }
}
