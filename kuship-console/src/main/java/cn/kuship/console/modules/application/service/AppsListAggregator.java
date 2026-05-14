package cn.kuship.console.modules.application.service;

import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.application.api.RegionApiSupport;
import cn.kuship.console.modules.application.entity.RegionApp;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.entity.TenantServiceVolume;
import cn.kuship.console.modules.application.repository.RegionAppRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.application.repository.TenantServiceVolumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用列表聚合：调 region {@code services_status} + {@code appstatuses}，按 rainbond
 * {@code group_service.get_multi_apps_all_info} 行为聚合 status / used_mem / used_cpu /
 * run_service_num / allocate_mem / used_disk，并按 status + used_mem 排序。
 *
 * <p>注：access 信息（{@code accesses}）需 service_domain / service_tcp_domain 表
 * 一并迁入，本类暂返回空列表占位。
 */
@Service
public class AppsListAggregator {

    private static final Logger log = LoggerFactory.getLogger(AppsListAggregator.class);

    private static final String API_TYPE = "apps_list";

    private final ServiceGroupRelationRepository relationRepo;
    private final TenantServiceRepository serviceRepo;
    private final TenantServiceVolumeRepository volumeRepo;
    private final RegionAppRepository regionAppRepo;
    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;
    private final ServiceAccessInfoBuilder accessInfoBuilder;

    public AppsListAggregator(ServiceGroupRelationRepository relationRepo,
                                TenantServiceRepository serviceRepo,
                                TenantServiceVolumeRepository volumeRepo,
                                RegionAppRepository regionAppRepo,
                                RegionClientFactory clientFactory,
                                RegionApiResponseProcessor processor,
                                ServiceAccessInfoBuilder accessInfoBuilder) {
        this.relationRepo = relationRepo;
        this.serviceRepo = serviceRepo;
        this.volumeRepo = volumeRepo;
        this.regionAppRepo = regionAppRepo;
        this.clientFactory = clientFactory;
        this.processor = processor;
        this.accessInfoBuilder = accessInfoBuilder;
    }

    public List<Map<String, Object>> aggregate(List<ServiceGroup> pageGroups,
                                                  String tenantId,
                                                  String tenantName,
                                                  String enterpriseId,
                                                  Integer sort) {
        if (pageGroups == null || pageGroups.isEmpty()) return List.of();

        // 1) 取 group → 组件关联
        List<Integer> groupIds = pageGroups.stream().map(ServiceGroup::getId).toList();
        List<ServiceGroupRelation> relations = relationRepo.findByGroupIdIn(groupIds);
        Set<String> serviceIdSet = relations.stream()
                .map(ServiceGroupRelation::getServiceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<TenantService> services = serviceIdSet.isEmpty()
                ? List.of() : serviceRepo.findByServiceIdIn(new ArrayList<>(serviceIdSet));
        Map<String, TenantService> serviceById = services.stream()
                .collect(Collectors.toMap(TenantService::getServiceId, s -> s, (a, b) -> a));
        Map<Integer, List<TenantService>> servicesByGroup = new HashMap<>();
        for (ServiceGroupRelation r : relations) {
            TenantService svc = serviceById.get(r.getServiceId());
            if (svc != null) servicesByGroup.computeIfAbsent(r.getGroupId(), k -> new ArrayList<>()).add(svc);
        }

        // 2) 按 region_name 分组，分别调 region API
        Map<String, List<ServiceGroup>> groupsByRegion = pageGroups.stream()
                .filter(g -> g.getRegionName() != null && !g.getRegionName().isBlank())
                .collect(Collectors.groupingBy(ServiceGroup::getRegionName));

        // service_id -> {status, used_mem, used_cpu, ...}
        Map<String, JsonNode> serviceStatusMap = new HashMap<>();
        // app_id -> {status, memory, cpu}
        Map<Integer, JsonNode> appStatusMap = new HashMap<>();

        for (Map.Entry<String, List<ServiceGroup>> e : groupsByRegion.entrySet()) {
            String regionName = e.getKey();
            List<Integer> regionGroupIds = e.getValue().stream().map(ServiceGroup::getId).toList();
            List<String> regionServiceIds = regionGroupIds.stream()
                    .flatMap(gid -> servicesByGroup.getOrDefault(gid, List.of()).stream())
                    .map(TenantService::getServiceId).distinct().toList();

            // service status（POST）
            if (!regionServiceIds.isEmpty()) {
                JsonNode statusList = callServicesStatus(regionName, tenantName, enterpriseId, regionServiceIds);
                if (statusList != null && statusList.isArray()) {
                    for (JsonNode it : statusList) {
                        String sid = it.path("service_id").asText("");
                        if (!sid.isBlank()) serviceStatusMap.put(sid, it);
                    }
                }
            }

            // appstatuses（GET，通过 region_app 反查 region_app_id）
            List<RegionApp> regionApps = regionAppRepo.findByRegionNameAndAppIdIn(regionName, regionGroupIds);
            Map<String, Integer> regionAppIdToAppId = regionApps.stream()
                    .collect(Collectors.toMap(RegionApp::getRegionAppId, RegionApp::getAppId, (a, b) -> a));
            List<String> regionAppIds = new ArrayList<>(regionAppIdToAppId.keySet());
            if (!regionAppIds.isEmpty()) {
                JsonNode appStatusList = callAppStatuses(regionName, tenantName, regionAppIds);
                if (appStatusList != null && appStatusList.isArray()) {
                    for (JsonNode it : appStatusList) {
                        String regionAppId = it.path("app_id").asText("");
                        Integer appId = regionAppIdToAppId.get(regionAppId);
                        if (appId != null) appStatusMap.put(appId, it);
                    }
                }
            }
        }

        // 3a) 访问入口拼装（HTTP / TCP 域名 + region tcpdomain 替换）
        Map<String, Map<String, Object>> accessInfoByService = accessInfoBuilder.build(tenantId, services);

        // 3) volume 聚合：service_id → 总容量
        Map<String, Long> volumeByService = new HashMap<>();
        if (!serviceIdSet.isEmpty()) {
            List<TenantServiceVolume> vols = volumeRepo.findByServiceIdIn(new ArrayList<>(serviceIdSet));
            for (TenantServiceVolume v : vols) {
                if (v.getVolumeType() != null && v.getVolumeType().equalsIgnoreCase("config-file")) continue;
                long cap = v.getVolumeCapacity() == null || v.getVolumeCapacity() == 0 ? 10L : v.getVolumeCapacity().longValue();
                volumeByService.merge(v.getServiceId(), cap, Long::sum);
            }
        }

        // 4) 拼装每个 app
        List<Map<String, Object>> apps = new ArrayList<>();
        for (ServiceGroup g : pageGroups) {
            List<TenantService> compList = servicesByGroup.getOrDefault(g.getId(), List.of());
            JsonNode appStatus = appStatusMap.get(g.getId());

            // memory / cpu 来自 region appstatus；status 优先用 region appstatus，组件状态聚合作为兜底
            long usedMem = appStatus != null ? appStatus.path("memory").asLong(0) : 0L;
            long usedCpu = appStatus != null ? appStatus.path("cpu").asLong(0) : 0L;
            String regionAppStatus = appStatus != null ? appStatus.path("status").asText("") : "";

            // 组件状态列表（去除 undeploy） + run_service_num + allocate_mem + used_disk
            List<String> componentStatuses = new ArrayList<>();
            int runServiceNum = 0;
            long allocateMem = 0L;
            long usedDisk = 0L;
            for (TenantService svc : compList) {
                JsonNode svcStatus = serviceStatusMap.get(svc.getServiceId());
                String s = svcStatus != null ? svcStatus.path("status").asText("failure") : "failure";
                componentStatuses.add(s);
                if (svc.getMinMemory() != null) allocateMem += svc.getMinMemory();
                if (s.equals("running") || s.equals("upgrade") || s.equals("starting") || s.equals("some_abnormal")) {
                    runServiceNum++;
                }
                Long disk = volumeByService.get(svc.getServiceId());
                if (disk != null) usedDisk += disk;
            }
            if (usedMem > allocateMem) allocateMem = usedMem;

            // 应用状态：rainbond 行为：helm 应用直接用 region 返回的 status；非 helm 应用用组件状态聚合
            String status;
            if (g.getAppType() != null && g.getAppType().equalsIgnoreCase("helm")) {
                status = regionAppStatus.isBlank() ? "UNKNOWN" : regionAppStatus;
            } else {
                if (compList.isEmpty()) {
                    status = !regionAppStatus.isBlank() ? regionAppStatus : "NIL";
                } else {
                    status = aggregateAppStatus(componentStatuses);
                }
            }

            Map<String, Object> app = new LinkedHashMap<>();
            app.put("group_id", g.getId());
            app.put("update_time", g.getUpdateTime());
            app.put("create_time", g.getCreateTime());
            app.put("group_name", g.getGroupName());
            app.put("group_note", g.getNote() == null ? "" : g.getNote());
            app.put("used_mem", usedMem);
            app.put("used_cpu", usedCpu);
            app.put("used_disk", usedDisk);
            app.put("status", status);
            app.put("logo", g.getLogo());
            // accesses: rainbond 端是组件粒度的 access_info 列表，按 service_id 顺序排
            List<Map<String, Object>> accesses = new ArrayList<>();
            for (TenantService svc : compList) {
                Map<String, Object> info = accessInfoByService.get(svc.getServiceId());
                if (info != null) accesses.add(info);
            }
            app.put("accesses", accesses);
            app.put("services_num", compList.size());
            app.put("run_service_num", runServiceNum);
            app.put("allocate_mem", allocateMem);
            apps.add(app);
        }

        // 5) sort != 2 时按 status + used_mem desc 排序
        if (sort == null || sort != 2) {
            apps.sort(Comparator
                    .comparingInt((Map<String, Object> a) -> statusOrder((String) a.get("status")))
                    .thenComparing(a -> -toLong(a.get("used_mem"))));
        }
        return apps;
    }

    /** rainbond {@code topological_service.get_app_status} 状态机。 */
    static String aggregateAppStatus(List<String> componentStatuses) {
        List<String> active = componentStatuses.stream()
                .filter(s -> s != null && !s.equalsIgnoreCase("undeploy")).toList();
        if (active.isEmpty()) return "NIL";
        if (active.stream().allMatch(s -> s.equalsIgnoreCase("closed"))) return "CLOSED";
        // partially abnormal: 含 some_abnormal 直接判 PARTIAL；含 abnormal 但不全 abnormal → PARTIAL
        boolean anySomeAbnormal = active.stream().anyMatch(s -> s.equalsIgnoreCase("some_abnormal"));
        long abnormalCount = active.stream().filter(s -> s.equalsIgnoreCase("abnormal")).count();
        if (anySomeAbnormal || (abnormalCount > 0 && abnormalCount != active.size())) return "PARTIAL_ABNORMAL";
        if (abnormalCount > 0) return "ABNORMAL";
        if (active.stream().anyMatch(s -> s.equalsIgnoreCase("starting") || s.equalsIgnoreCase("waiting"))) return "STARTING";
        // stopping: 至少 1 个 stopping，其余 closed
        boolean anyStopping = false;
        boolean stoppingOk = true;
        for (String s : active) {
            if (s.equalsIgnoreCase("stopping")) { anyStopping = true; continue; }
            if (s.equalsIgnoreCase("closed")) continue;
            stoppingOk = false; break;
        }
        if (anyStopping && stoppingOk) return "STOPPING";
        return "RUNNING";
    }

    private JsonNode callServicesStatus(String regionName, String tenantName, String enterpriseId,
                                          List<String> serviceIds) {
        String url = "/v2/tenants/" + URLEncoder.encode(tenantName, StandardCharsets.UTF_8) + "/services_status";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service_ids", serviceIds);
        if (enterpriseId != null && !enterpriseId.isBlank()) body.put("enterprise_id", enterpriseId);
        try {
            ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "POST",
                    c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                            .exchange((req, r) -> RegionApiSupport.readAsString(r)));
            JsonNode root = processor.checkStatus(resp, API_TYPE, url, "POST");
            return resolveList(root);
        } catch (RuntimeException ex) {
            log.warn("[apps_list] services_status failed: region={} tenant={} err={}", regionName, tenantName, ex.toString());
            return null;
        }
    }

    private JsonNode callAppStatuses(String regionName, String tenantName, List<String> regionAppIds) {
        String url = "/v2/tenants/" + URLEncoder.encode(tenantName, StandardCharsets.UTF_8) + "/appstatuses";
        Map<String, Object> body = Map.of("app_ids", regionAppIds);
        try {
            ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "GET",
                    c -> c.method(HttpMethod.GET).uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                            .exchange((req, r) -> RegionApiSupport.readAsString(r)));
            JsonNode root = processor.checkStatus(resp, API_TYPE, url, "GET");
            return resolveList(root);
        } catch (RuntimeException ex) {
            log.warn("[apps_list] appstatuses failed: region={} tenant={} err={}", regionName, tenantName, ex.toString());
            return null;
        }
    }

    /** region 响应的 list 节点：先看顶层 list，再退到 data.list。 */
    private static JsonNode resolveList(JsonNode root) {
        if (root == null) return null;
        JsonNode listNode = root.path("list");
        if (listNode.isArray()) return listNode;
        listNode = root.path("data").path("list");
        return listNode.isArray() ? listNode : null;
    }

    private static int statusOrder(String status) {
        if (status == null) return 4;
        return switch (status) {
            case "RUNNING" -> 1;
            case "ABNORMAL", "PARTIAL_ABNORMAL" -> 2;
            case "STARTING" -> 3;
            case "CLOSED" -> 5;
            default -> 4;
        };
    }

    private static long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0; }
    }
}
