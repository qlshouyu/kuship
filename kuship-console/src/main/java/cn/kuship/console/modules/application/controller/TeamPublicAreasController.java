package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.api.RegionApiSupport;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对齐 rainbond {@code views/public_areas.py}：{@code TeamArchView} + {@code TeamAppSortViewView}。
 *
 * <p>路径基址特意保留 {@code /console/teams/{team_name}}，与 {@code GroupController}
 *  ({@code /groups})、{@code TeamOverviewController}（{@code /overview}）共存。
 */
@RestController
@RequestMapping("/console/teams/{team_name}")
public class TeamPublicAreasController {

    private static final String API_TYPE = "cluster_arch";

    private final TenantsRepository tenantsRepo;
    private final ServiceGroupRepository groupRepo;
    private final TenantServiceRepository serviceRepo;
    private final ServiceGroupRelationRepository relationRepo;
    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public TeamPublicAreasController(TenantsRepository tenantsRepo,
                                       ServiceGroupRepository groupRepo,
                                       TenantServiceRepository serviceRepo,
                                       ServiceGroupRelationRepository relationRepo,
                                       RegionClientFactory clientFactory,
                                       RegionApiResponseProcessor processor) {
        this.tenantsRepo = tenantsRepo;
        this.groupRepo = groupRepo;
        this.serviceRepo = serviceRepo;
        this.relationRepo = relationRepo;
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    /**
     * 集群节点架构列表（去重）。对齐 rainbond {@code TeamArchView}（{@code public_areas.py:65}）。
     * Region API 路径：{@code /v2/cluster/nodes/arch}（{@code regionapi.py:2670}），响应顶层 {@code list}。
     */
    @GetMapping(value = {"/arch", "/arch/"})
    public ApiResult arch(@PathVariable("team_name") String teamName,
                            @RequestParam(name = "region_name", required = false) String regionName) {
        if (regionName == null || regionName.isBlank()) {
            throw new ServiceHandleException(400, "region_name is required", "缺少 region_name");
        }
        String url = "/v2/cluster/nodes/arch";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        JsonNode body = processor.checkStatus(resp, API_TYPE, url, "GET");
        JsonNode listNode = body.path("list");
        if (listNode.isMissingNode() || listNode.isNull() || !listNode.isArray()) {
            listNode = body.path("data").path("list");
        }
        Set<String> archs = new HashSet<>();
        if (listNode.isArray()) {
            for (JsonNode n : listNode) {
                String v = n.asText("");
                if (!v.isBlank()) archs.add(v);
            }
        }
        return GeneralMessage.okList(new ArrayList<>(archs));
    }

    /**
     * 团队应用列表（带分页 / sort / query）。对齐 rainbond {@code TeamAppSortViewView}
     * （{@code public_areas.py:538}）。当前实现仅返回 group 基础字段 + service_list；
     * region 状态 / 资源聚合（rainbond {@code group_service.get_multi_apps_all_info}）留待后续 hardening。
     */
    @GetMapping(value = {"/apps", "/apps/"})
    public ApiResult apps(@PathVariable("team_name") String teamName,
                            @RequestParam(name = "region_name", required = false) String regionName,
                            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
                            @RequestParam(name = "page_size", required = false, defaultValue = "10") Integer pageSize,
                            @RequestParam(name = "query", required = false) String query,
                            @RequestParam(name = "sort", required = false, defaultValue = "1") Integer sort) {
        Tenants tenant = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));

        List<ServiceGroup> groups = (regionName == null || regionName.isBlank())
                ? groupRepo.findByTenantId(tenant.getTenantId())
                : groupRepo.findByTenantIdAndRegionName(tenant.getTenantId(), regionName);
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase();
            groups = groups.stream()
                    .filter(g -> g.getGroupName() != null && g.getGroupName().toLowerCase().contains(q))
                    .toList();
        }
        int total = groups.size();
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 200);
        int start = Math.min((p - 1) * ps, total);
        int end = Math.min(start + ps, total);
        List<ServiceGroup> pageGroups = groups.subList(start, end);

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("total", total);

        if (pageGroups.isEmpty()) {
            return GeneralMessage.okWithExtras(bean, List.of(), null);
        }

        List<Integer> groupIds = pageGroups.stream().map(ServiceGroup::getId).toList();
        List<ServiceGroupRelation> relations = relationRepo.findByGroupIdIn(groupIds);
        Set<String> serviceIds = relations.stream()
                .map(ServiceGroupRelation::getServiceId).collect(Collectors.toSet());
        List<TenantService> services = serviceIds.isEmpty()
                ? List.of()
                : serviceRepo.findByServiceIdIn(new ArrayList<>(serviceIds));
        Map<String, TenantService> svcById = services.stream()
                .collect(Collectors.toMap(TenantService::getServiceId, s -> s, (a, b) -> a));

        Map<Integer, List<Map<String, Object>>> groupServiceMap = new HashMap<>();
        for (ServiceGroupRelation r : relations) {
            TenantService s = svcById.get(r.getServiceId());
            if (s == null) continue;
            Map<String, Object> svc = new LinkedHashMap<>();
            svc.put("service_id", s.getServiceId());
            svc.put("service_cname", s.getServiceCname());
            svc.put("service_alias", s.getServiceAlias());
            groupServiceMap.computeIfAbsent(r.getGroupId(), k -> new ArrayList<>()).add(svc);
        }

        List<Map<String, Object>> apps = new ArrayList<>();
        for (ServiceGroup g : pageGroups) {
            Map<String, Object> app = new LinkedHashMap<>();
            app.put("group_id", g.getId());
            app.put("ID", g.getId());
            app.put("group_name", g.getGroupName());
            app.put("region_name", g.getRegionName());
            app.put("note", g.getNote());
            app.put("username", g.getUsername());
            app.put("create_time", g.getCreateTime());
            app.put("update_time", g.getUpdateTime());
            app.put("app_type", g.getAppType());
            app.put("governance_mode", g.getGovernanceMode());
            app.put("services", groupServiceMap.getOrDefault(g.getId(), List.of()));
            // 状态 / 资源占位（待 group_service.get_multi_apps_all_info 迁移完整后填充）
            app.put("run_service_num", 0);
            app.put("services_num", groupServiceMap.getOrDefault(g.getId(), List.of()).size());
            app.put("used_mem", 0);
            app.put("used_cpu", 0);
            apps.add(app);
        }
        return GeneralMessage.okWithExtras(bean, apps, null);
    }
}
