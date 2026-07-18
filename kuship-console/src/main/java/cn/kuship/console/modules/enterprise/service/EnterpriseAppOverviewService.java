package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import cn.kuship.console.modules.app.entity.ServiceGroup;
import cn.kuship.console.modules.app.entity.ServiceGroupRelation;
import cn.kuship.console.modules.app.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 企业应用/组件运行概览（对齐 rainbond EnterpriseAppOverView + get_enterprise_runing_service）。
 * 应用/组件总数取自 DB（service_group / service_group_relation）；运行数取自各集群 running-services。
 * 注：本环境 region 不可达，运行数降级为 0；total 取 DB 实数。
 */
@Service
public class EnterpriseAppOverviewService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseAppOverviewService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RegionConfigRepository regionConfigRepository;
    private final TenantsRepository tenantsRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final ServiceGroupRelationRepository serviceGroupRelationRepository;
    private final RegionClient regionClient;

    public EnterpriseAppOverviewService(RegionConfigRepository regionConfigRepository,
                                        TenantsRepository tenantsRepository,
                                        ServiceGroupRepository serviceGroupRepository,
                                        ServiceGroupRelationRepository serviceGroupRelationRepository,
                                        RegionClient regionClient) {
        this.regionConfigRepository = regionConfigRepository;
        this.tenantsRepository = tenantsRepository;
        this.serviceGroupRepository = serviceGroupRepository;
        this.serviceGroupRelationRepository = serviceGroupRelationRepository;
        this.regionClient = regionClient;
    }

    /** 应用/组件运行统计 bean；无可用集群返回 null（controller 走 404/no found regions 分支）。 */
    public Map<String, Object> overview(String enterpriseId) {
        List<RegionConfig> regions = regionConfigRepository
                .findByEnterpriseIdAndStatusOrderById(enterpriseId, "1");
        if (regions.isEmpty()) {
            return null;
        }

        List<Tenants> teams = tenantsRepository.findByEnterpriseId(enterpriseId);
        if (teams.isEmpty()) {
            return buildData(0, 0, 0, 0);
        }
        List<String> teamIds = teams.stream().map(Tenants::getTenantId).toList();
        List<String> regionNames = regions.stream().map(RegionConfig::getRegionName).toList();

        List<ServiceGroup> apps = serviceGroupRepository.findByTenantIdInAndRegionNameIn(teamIds, regionNames);
        int appTotal = apps.size();
        List<Integer> appIds = apps.stream().map(ServiceGroup::getId).toList();

        List<ServiceGroupRelation> relations = appIds.isEmpty()
                ? List.of()
                : serviceGroupRelationRepository.findByGroupIdIn(appIds);
        int componentTotal = relations.size();

        // component_id -> app(group) id
        Map<String, Integer> componentAndApp = new LinkedHashMap<>();
        for (ServiceGroupRelation r : relations) {
            componentAndApp.put(r.getServiceId(), r.getGroupId());
        }

        // 各集群运行中组件 id（region 不可达则跳过）
        List<String> runningComponentIds = new ArrayList<>();
        for (RegionConfig region : regions) {
            try {
                String body = regionClient.exchange(region.getRegionName(), "GET",
                        "/v2/enterprise/" + enterpriseId + "/running-services", null, TimeoutTier.NORMAL, null);
                Object ids = MAPPER.readValue(body, Map.class).get("service_ids");
                if (ids instanceof List<?> il) {
                    for (Object o : il) {
                        runningComponentIds.add(String.valueOf(o));
                    }
                }
            } catch (Exception e) {
                log.debug("get running services {}: {}", region.getRegionName(), e.getMessage());
            }
        }

        int componentRunning = 0;
        Set<Integer> runningApps = new HashSet<>();
        for (String cid : runningComponentIds) {
            Integer appId = componentAndApp.get(cid);
            if (appId != null) {
                componentRunning++;
                runningApps.add(appId);
            }
        }
        return buildData(appTotal, runningApps.size(), componentTotal, componentRunning);
    }

    private Map<String, Object> buildData(int appTotal, int appRunning, int compTotal, int compRunning) {
        Map<String, Object> serviceGroups = new LinkedHashMap<>();
        serviceGroups.put("total", appTotal);
        serviceGroups.put("running", appRunning);
        serviceGroups.put("closed", appTotal - appRunning);
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("total", compTotal);
        components.put("running", compRunning);
        components.put("closed", compTotal - compRunning);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service_groups", serviceGroups);
        data.put("components", components);
        return data;
    }
}
