package cn.kuship.console.modules.region.resource.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import cn.kuship.console.modules.region.resource.service.HelmReleaseSourceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 资源中心 WebSocket 信息端点。
 *
 * <p>对应 rainbond {@code views/team_resources.py ResourceCenterWSInfoView}。
 * 返回 event WebSocket URL、namespace、tenant_name，供前端建立 event log WS 连接。
 */
@RestController
@RequestMapping("/console/teams/{team_name}/regions/{region_name}/resource-center")
public class ResourceCenterWsInfoController {

    private final TenantsRepository tenantsRepo;
    private final RegionInfoEntityRepository regionRepo;

    public ResourceCenterWsInfoController(TenantsRepository tenantsRepo,
                                           RegionInfoEntityRepository regionRepo) {
        this.tenantsRepo = tenantsRepo;
        this.regionRepo = regionRepo;
    }

    /** GET /ws-info — 返回 event WebSocket URL + namespace + tenant_name。 */
    @GetMapping(value = {"/ws-info", "/ws-info/"})
    public ApiResult getWsInfo(@PathVariable("team_name") String teamName,
                                @PathVariable("region_name") String regionName,
                                HttpServletRequest request) {
        // 解析 namespace（优先 tenant.namespace，否则 team_name）
        String namespace = HelmReleaseSourceService.resolveNamespace(teamName, tenantsRepo);

        // 构建 event_websocket_url：对齐 rainbond ws_service.get_event_log_ws
        String eventWsUrl = buildEventWsUrl(regionName, request);

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("event_websocket_url", eventWsUrl);
        bean.put("namespace", namespace);
        bean.put("tenant_name", teamName);
        return GeneralMessage.ok(bean);
    }

    /**
     * 对齐 rainbond {@code ws_service.__event_ws(request, region, "event_log")}：
     * - region.wsurl 非空且非 "auto" → {@code region.wsurl + "/event_log"}
     * - 否则 → {@code ws://<HOST>:6060/event_log}
     */
    private String buildEventWsUrl(String regionName, HttpServletRequest request) {
        Optional<RegionInfo> regionOpt = regionRepo.findByRegionName(regionName);
        if (regionOpt.isPresent()) {
            RegionInfo r = regionOpt.get();
            String wsUrl = r.getWsurl();
            if (wsUrl != null && !wsUrl.isBlank() && !"auto".equalsIgnoreCase(wsUrl)) {
                return wsUrl.endsWith("/") ? wsUrl + "event_log" : wsUrl + "/event_log";
            }
        }
        // fallback：auto 或找不到 region
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName();
        }
        String serverHost = host.split(":")[0];
        return "ws://" + serverHost + ":6060/event_log";
    }
}
