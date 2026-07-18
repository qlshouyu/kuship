package cn.kuship.console.modules.team.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.rbac.service.TeamMemberRoleService;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import cn.kuship.console.modules.team.entity.PermRelTenant;
import cn.kuship.console.modules.team.entity.TenantRegionInfo;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.PermRelTenantRepository;
import cn.kuship.console.modules.team.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 团队读路径业务：企业团队列表、用户加入的团队。字段对照 docs/p1a-7070-reference.md。 */
@Service
public class TeamReadService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TenantsRepository tenantsRepository;
    private final TenantRegionInfoRepository tenantRegionRepository;
    private final PermRelTenantRepository permRelTenantRepository;
    private final RegionConfigRepository regionConfigRepository;
    private final UserInfoRepository userInfoRepository;

    private final TeamMemberRoleService teamMemberRoleService;
    private final cn.kuship.console.modules.app.repository.ServiceGroupRepository serviceGroupRepository;
    private final cn.kuship.console.modules.app.repository.ServiceGroupRelationRepository serviceGroupRelationRepository;

    public TeamReadService(TenantsRepository tenantsRepository,
                           TenantRegionInfoRepository tenantRegionRepository,
                           PermRelTenantRepository permRelTenantRepository,
                           RegionConfigRepository regionConfigRepository,
                           UserInfoRepository userInfoRepository,
                           TeamMemberRoleService teamMemberRoleService,
                           cn.kuship.console.modules.app.repository.ServiceGroupRepository serviceGroupRepository,
                           cn.kuship.console.modules.app.repository.ServiceGroupRelationRepository serviceGroupRelationRepository) {
        this.tenantsRepository = tenantsRepository;
        this.tenantRegionRepository = tenantRegionRepository;
        this.permRelTenantRepository = permRelTenantRepository;
        this.regionConfigRepository = regionConfigRepository;
        this.userInfoRepository = userInfoRepository;
        this.teamMemberRoleService = teamMemberRoleService;
        this.serviceGroupRepository = serviceGroupRepository;
        this.serviceGroupRelationRepository = serviceGroupRelationRepository;
    }

    /** 企业下团队列表（bean = {total_count,page,page_size,list}）。 */
    public Map<String, Object> listEnterpriseTeams(String enterpriseId, int page, int pageSize) {
        List<Tenants> teams = tenantsRepository.findByEnterpriseId(enterpriseId);
        List<Map<String, Object>> list = teams.stream().map(this::toEnterpriseTeamMap).collect(Collectors.toList());
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("total_count", list.size());
        bean.put("page", page);
        bean.put("page_size", pageSize);
        bean.put("list", list);
        return bean;
    }

    /** 用户加入的团队（成员关系 tenant_perms ∪ 自己创建的团队）。 */
    public List<Map<String, Object>> listUserTeams(String enterpriseId, Integer userId) {
        // 对齐 rainbond：user_id 不存在 → 404 user not found / 用户不存在
        if (userInfoRepository.findById(userId).isEmpty()) {
            throw ServiceHandleException.notFound("user not found", "用户不存在");
        }
        List<Integer> tenantPks = permRelTenantRepository.findByUserId(userId).stream()
                .map(PermRelTenant::getTenantId).collect(Collectors.toList());
        List<Tenants> teams = tenantPks.isEmpty() ? new ArrayList<>() : tenantsRepository.findByIdIn(tenantPks);
        // 并入自己创建但未在成员表中的团队（owner 兜底）
        for (Tenants t : tenantsRepository.findByEnterpriseId(enterpriseId)) {
            if (userId.equals(t.getCreater()) && teams.stream().noneMatch(x -> x.getId().equals(t.getId()))) {
                teams.add(t);
            }
        }
        return teams.stream().map(t -> toUserTeamMap(t, userId)).collect(Collectors.toList());
    }

    private Map<String, Object> toEnterpriseTeamMap(Tenants t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", t.getId());
        m.put("tenant_id", t.getTenantId());
        m.put("tenant_name", t.getTenantName());
        m.put("is_active", t.getIsActive());
        m.put("create_time", t.getCreateTime() == null ? null : t.getCreateTime().format(TS));
        m.put("creater", t.getCreater());
        m.put("limit_memory", t.getLimitMemory());
        m.put("update_time", t.getUpdateTime() == null ? null : t.getUpdateTime().format(TS));
        m.put("tenant_alias", t.getTenantAlias());
        m.put("enterprise_id", t.getEnterpriseId());
        m.put("namespace", t.getNamespace());
        m.put("logo", t.getLogo());
        List<Map<String, Object>> regionList = regionListOf(t.getTenantId(), true);
        m.put("region", regionList.isEmpty() ? "" : regionList.get(0).get("region_name"));
        m.put("region_list", regionList);
        m.put("team_alias", t.getTenantAlias());
        m.put("team_name", t.getTenantName());
        m.put("user_number", permRelTenantRepository.countByTenantId(t.getId()));
        m.put("owner_name", ownerName(t.getCreater()));
        // 资源配额/用量来自 region-api，P1-a 暂 0（重聚合留后续）
        m.put("set_limit_memory", 0);
        m.put("set_limit_cpu", 0);
        m.put("set_limit_storage", 0);
        m.put("running_apps", 0);
        m.put("memory_request", 0);
        m.put("cpu_request", 0);
        m.put("storage_request", 0);
        return m;
    }

    private Map<String, Object> toUserTeamMap(Tenants t, Integer userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("team_name", t.getTenantName());
        m.put("team_alias", t.getTenantAlias());
        m.put("team_id", t.getTenantId());
        m.put("create_time", t.getCreateTime());
        m.put("enterprise_id", t.getEnterpriseId());
        m.put("owner", t.getCreater());
        m.put("owner_name", ownerName(t.getCreater()));
        m.put("logo", t.getLogo());
        // 角色（对齐 get_user_roles）：先用户在团队的角色名，creater 再追加 "owner"
        List<String> roles = new ArrayList<>(teamMemberRoleService.userTeamRoleNames(t.getTenantId(), userId));
        if (userId.equals(t.getCreater())) {
            roles.add("owner");
        }
        m.put("roles", roles);
        List<Map<String, Object>> regionList = regionListOf(t.getTenantId(), false);
        m.put("region", regionList.isEmpty() ? "" : regionList.get(0).get("region_name"));
        m.put("region_list", regionList);
        m.put("app_count", 0);
        m.put("service_count", 0);
        return m;
    }

    /** 团队的 region 列表（withId=true 含 region_id，对齐 enterprise/teams；false 仅 name+alias，对齐 user/teams）。 */
    private List<Map<String, Object>> regionListOf(String tenantId, boolean withId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TenantRegionInfo tr : tenantRegionRepository.findByTenantId(tenantId)) {
            RegionConfig rc = regionConfigRepository.findByRegionName(tr.getRegionName()).orElse(null);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("region_name", tr.getRegionName());
            r.put("region_alias", rc == null ? null : rc.getRegionAlias());
            if (withId) {
                r.put("region_id", rc == null ? null : rc.getRegionId());
            }
            out.add(r);
        }
        return out;
    }

    /** 团队概览（region 作用域）。资源统计来自 region-api，P1-a 暂 0。 */
    public Map<String, Object> teamOverview(Tenants team, String regionName) {
        RegionConfig rc = regionConfigRepository.findByRegionName(regionName).orElse(null);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("user_nums", permRelTenantRepository.countByTenantId(team.getId()));
        bean.put("logo", team.getLogo());
        // team_app_num = service_group(tenant+region) 行数（对齐 get_tenant_region_groups）；
        // team_service_num = service_group_relation(tenant+region) 行数（对齐 get_team_service_num_by_team_id）
        bean.put("team_app_num", serviceGroupRepository
                .findByTenantIdAndRegionNameOrderByUpdateTimeDescOrderIndexDesc(team.getTenantId(), regionName).size());
        bean.put("team_service_num", serviceGroupRelationRepository
                .countByTenantIdAndRegionName(team.getTenantId(), regionName));
        bean.put("eid", team.getEnterpriseId());
        bean.put("team_id", team.getTenantId());
        bean.put("team_service_memory_count", 0);
        bean.put("team_service_total_disk", 0);
        bean.put("team_service_total_cpu", 0);
        bean.put("team_service_total_memory", 0);
        bean.put("team_service_use_cpu", 0);
        bean.put("cpu_usage", 0);
        bean.put("memory_usage", 0);
        bean.put("running_app_num", 0);
        bean.put("running_component_num", 0);
        bean.put("team_alias", team.getTenantAlias());
        bean.put("region_id", rc == null ? null : rc.getRegionId());
        bean.put("disk_usage", 0);
        bean.put("region_health", rc != null && "1".equals(rc.getStatus()));
        return bean;
    }

    private String ownerName(Integer creater) {
        if (creater == null) {
            return "";
        }
        return userInfoRepository.findById(creater).map(UserInfo::getNickName).orElse("");
    }
}
