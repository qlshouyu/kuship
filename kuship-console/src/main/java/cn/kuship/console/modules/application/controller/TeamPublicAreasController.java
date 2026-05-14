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
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.service.AppsListAggregator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;
    private final AppsListAggregator appsListAggregator;

    public TeamPublicAreasController(TenantsRepository tenantsRepo,
                                       ServiceGroupRepository groupRepo,
                                       RegionClientFactory clientFactory,
                                       RegionApiResponseProcessor processor,
                                       AppsListAggregator appsListAggregator) {
        this.tenantsRepo = tenantsRepo;
        this.groupRepo = groupRepo;
        this.clientFactory = clientFactory;
        this.processor = processor;
        this.appsListAggregator = appsListAggregator;
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
     * （{@code public_areas.py:538}）+ {@code group_service.get_multi_apps_all_info}。
     *
     * <p>聚合 region {@code services_status} + {@code appstatuses} 拿组件 / 应用状态，
     * 计算 used_mem / used_cpu / run_service_num / allocate_mem / used_disk。
     * accesses 待 service_domain / service_tcp_domain entity 迁移后补齐，当前返回空列表。
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
        List<ServiceGroup> pageGroups = new ArrayList<>(groups.subList(start, end));

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("total", total);

        if (pageGroups.isEmpty()) {
            return GeneralMessage.okWithExtras(bean, List.of(), null);
        }

        List<Map<String, Object>> apps = appsListAggregator.aggregate(
                pageGroups, tenant.getTenantId(), tenant.getTenantName(), tenant.getEnterpriseId(), sort);
        return GeneralMessage.okWithExtras(bean, apps, null);
    }
}
