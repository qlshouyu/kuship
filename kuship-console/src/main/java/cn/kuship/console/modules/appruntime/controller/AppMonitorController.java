package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.appruntime.api.MonitorOperations;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 监控端点：query / query_range / batch_query / resource。 */
@RestController
public class AppMonitorController {

    private final MonitorOperations monitor;
    private final RuntimeContextLoader loader;
    private final ServiceGroupRepository groupRepo;
    private final ServiceGroupRelationRepository relationRepo;
    private final TenantServiceRepository serviceRepo;

    public AppMonitorController(MonitorOperations monitor,
                                  RuntimeContextLoader loader,
                                  ServiceGroupRepository groupRepo,
                                  ServiceGroupRelationRepository relationRepo,
                                  TenantServiceRepository serviceRepo) {
        this.monitor = monitor;
        this.loader = loader;
        this.groupRepo = groupRepo;
        this.relationRepo = relationRepo;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/monitor/query",
                          "/console/teams/{team_name}/apps/{service_alias}/monitor/query/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult query(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String alias,
                              @RequestParam Map<String, String> queryParams) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = monitor.query(s.getServiceRegion(), teamName, queryParams);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/monitor/query_range",
                          "/console/teams/{team_name}/apps/{service_alias}/monitor/query_range/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult queryRange(@PathVariable("team_name") String teamName,
                                   @PathVariable("service_alias") String alias,
                                   @RequestParam Map<String, String> queryParams) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = monitor.queryRange(s.getServiceRegion(), teamName, queryParams);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/groups/{group_id}/monitor/batch_query",
                          "/console/teams/{team_name}/groups/{group_id}/monitor/batch_query/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult batchQuery(@PathVariable("team_name") String teamName,
                                   @PathVariable("group_id") Integer groupId,
                                   @RequestParam Map<String, String> queryParams) {
        loader.requireTeam(teamName);
        ServiceGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ServiceHandleException(404, "group not found", "应用不存在"));
        List<ServiceGroupRelation> rels = relationRepo.findByGroupId(groupId);
        List<String> serviceIds = rels.stream().map(ServiceGroupRelation::getServiceId).toList();
        String region = group.getRegionName();
        if (region == null && !serviceIds.isEmpty()) {
            region = serviceRepo.findByServiceIdIn(serviceIds).get(0).getServiceRegion();
        }
        Map<String, String> q = new LinkedHashMap<>(queryParams);
        q.put("service_ids", String.join(",", serviceIds));
        Map<String, Object> resp = monitor.batchQuery(region != null ? region : "default", teamName, q);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/resource",
                          "/console/teams/{team_name}/apps/{service_alias}/resource/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult resource(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String alias) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = monitor.getServiceResources(s.getServiceRegion(), teamName, alias);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    // ─── migrate-console-monitor-extras：4 个新 endpoint ──────────────────────

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/metrics",
                          "/console/teams/{team_name}/apps/{service_alias}/metrics/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult metrics(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String alias) {
        Tenants tenant = loader.requireTeam(teamName);
        TenantService svc = loader.requireService(teamName, alias);
        Map<String, Object> resp = monitor.getMonitorMetrics(
                svc.getServiceRegion(), tenant.getTenantId(), "component", "", svc.getServiceId());
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    // resourceCenterEvents endpoint 已由既有 ResourceCenterController（migrate-console-region-resource-center）
    // 在 /console/teams/{team_name}/regions/{region_name}/resource-center/events 上落地，本 change 不再
    // 暴露 controller 端点。MonitorOperations.getResourceCenterEvents 接口仍保留供后续 hardening 复用。

    @GetMapping(value = {"/console/teams/{team_name}/region/{region_name}/sort_domain/query",
                          "/console/teams/{team_name}/region/{region_name}/sort_domain/query/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult sortDomainQuery(@PathVariable("team_name") String teamName,
                                          @PathVariable("region_name") String regionName,
                                          @RequestParam(required = false, defaultValue = "1") Integer page,
                                          @RequestParam(name = "page_size", required = false, defaultValue = "10") Integer pageSize,
                                          @RequestParam(required = false) String repo) {
        Tenants tenant = loader.requireTeam(teamName);
        String promql = "sort_desc(sum(ceil(increase(gateway_requests{namespace=\""
                + tenant.getTenantId() + "\"}[1h]))) by (host))";
        Map<String, String> q = new LinkedHashMap<>();
        q.put("query", promql);
        if (repo != null && !repo.isBlank()) q.put("repo", repo);
        Map<String, Object> resp = monitor.queryDomainAccess(regionName, teamName, q);

        List<Map<String, Object>> result = extractPromResult(resp);
        long totalTraffic = 0;
        for (Map<String, Object> entry : result) {
            Object value = entry.get("value");
            if (value instanceof List<?> tuple && tuple.size() >= 2) {
                try {
                    totalTraffic += (long) Double.parseDouble(String.valueOf(tuple.get(1)));
                } catch (NumberFormatException ignore) {}
            }
        }
        int total = result.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(page * pageSize, total);
        List<Map<String, Object>> paged = result.subList(from, to);

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("total", total);
        bean.put("total_traffic", totalTraffic);

        return GeneralMessage.okWithExtras(bean, paged, null);
    }

    @GetMapping(value = {"/console/teams/{team_name}/region/{region_name}/sort_service/query",
                          "/console/teams/{team_name}/region/{region_name}/sort_service/query/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult sortServiceQuery(@PathVariable("team_name") String teamName,
                                           @PathVariable("region_name") String regionName) {
        Tenants tenant = loader.requireTeam(teamName);

        String outerPromql = "topk(10, sort_desc(sum(ceil(increase(gateway_requests{namespace=\""
                + tenant.getTenantId() + "\"}[1h]))) by (service)))";
        Map<String, String> outerQ = Map.of("query", outerPromql);
        Map<String, Object> outer = monitor.queryServiceAccess(regionName, teamName, new LinkedHashMap<>(outerQ));

        String innerPromql = "topk(10, sort_desc(sum(ceil(increase(app_request{tenant_id=\""
                + tenant.getTenantId() + "\"}[1h]))) by (service_id)))";
        Map<String, String> innerQ = Map.of("query", innerPromql);
        Map<String, Object> inner = monitor.queryServiceAccess(regionName, teamName, new LinkedHashMap<>(innerQ));

        List<Map<String, Object>> merged = new ArrayList<>();
        merged.addAll(extractPromResult(outer));
        merged.addAll(extractPromResult(inner));
        // 简单去重：以 metric.service 或 metric.service_id 为 key
        Map<String, Map<String, Object>> dedup = new LinkedHashMap<>();
        for (Map<String, Object> entry : merged) {
            Object metric = entry.get("metric");
            String key = "";
            if (metric instanceof Map<?, ?> m) {
                if (m.get("service") instanceof String s) key = s;
                else if (m.get("service_id") instanceof String s) key = s;
            }
            dedup.putIfAbsent(key, entry);
        }
        List<Map<String, Object>> result = new ArrayList<>(dedup.values());
        if (result.size() > 10) result = result.subList(0, 10);

        return GeneralMessage.okList(result);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractPromResult(Map<String, Object> resp) {
        if (resp == null) return List.of();
        Object data = resp.get("data");
        Object list = null;
        if (data instanceof Map<?, ?> dm) {
            list = dm.get("result");
        }
        if (list == null) {
            list = resp.get("result");
        }
        if (list instanceof List<?> raw) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return List.of();
    }
}
