package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.rbac.service.RbacReadService;
import cn.kuship.console.modules.team.entity.PermRelTenant;
import cn.kuship.console.modules.team.entity.TenantRegionInfo;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.PermRelTenantRepository;
import cn.kuship.console.modules.team.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 企业团队概览（对齐 rainbond EnterpriseTeamOverView.get）：active_teams + new_join_team + request_join_team。
 * join/request 段（Applicants 加入申请域）本轮 defer 为空；active_teams 非-creater 角色（tenant_user_role 遗留）defer。
 */
@Service
public class EnterpriseTeamOverviewService {

    private final TenantEnterpriseRepository enterpriseRepository;
    private final PermRelTenantRepository permRelTenantRepository;
    private final TenantsRepository tenantsRepository;
    private final TenantRegionInfoRepository tenantRegionInfoRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final UserInfoRepository userInfoRepository;
    private final RbacReadService rbacReadService;

    public EnterpriseTeamOverviewService(TenantEnterpriseRepository enterpriseRepository,
                                         PermRelTenantRepository permRelTenantRepository,
                                         TenantsRepository tenantsRepository,
                                         TenantRegionInfoRepository tenantRegionInfoRepository,
                                         ServiceGroupRepository serviceGroupRepository,
                                         UserInfoRepository userInfoRepository,
                                         RbacReadService rbacReadService) {
        this.enterpriseRepository = enterpriseRepository;
        this.permRelTenantRepository = permRelTenantRepository;
        this.tenantsRepository = tenantsRepository;
        this.tenantRegionInfoRepository = tenantRegionInfoRepository;
        this.serviceGroupRepository = serviceGroupRepository;
        this.userInfoRepository = userInfoRepository;
        this.rbacReadService = rbacReadService;
    }

    public Map<String, Object> overviewTeam(String enterpriseId, Integer userId) {
        List<Tenants> tenants = userTeams(enterpriseId, userId);

        // active_teams：仅有 region 者，num=ServiceGroup 数，num 降序 [:3]
        List<Map<String, Object>> active = new ArrayList<>();
        for (Tenants t : tenants) {
            List<String> regionNames = regionNames(t.getTenantId());
            if (regionNames.isEmpty()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tenant_id", t.getTenantId());
            item.put("team_alias", t.getTenantAlias());
            item.put("owner", t.getCreater());
            item.put("owner_name", ownerName(t.getCreater()));
            item.put("enterprise_id", t.getEnterpriseId());
            item.put("create_time", t.getCreateTime());
            item.put("team_name", t.getTenantName());
            item.put("region", regionNames.get(0));
            item.put("region_list", regionNames);
            item.put("num", serviceGroupRepository.findByTenantId(t.getTenantId()).size());
            item.put("role", userId.equals(t.getCreater()) ? "owner" : null);
            active.add(item);
        }
        active.sort(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("num")).reversed());
        List<Map<String, Object>> activeTop3 = active.size() > 3 ? new ArrayList<>(active.subList(0, 3)) : active;

        // new_join_team：用户团队前 3（is_pass:true）
        List<Map<String, Object>> newJoin = new ArrayList<>();
        for (Tenants t : tenants.size() > 3 ? tenants.subList(0, 3) : tenants) {
            List<String> regionNames = regionNames(t.getTenantId());
            if (regionNames.isEmpty()) {
                continue;
            }
            List<String> roles = rbacReadService.getUserTeamRoles(t.getTenantId(), userId).stream()
                    .map(r -> (String) r.get("role_name")).collect(Collectors.toList());
            if (userId.equals(t.getCreater())) {
                roles.add("owner");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("team_name", t.getTenantName());
            item.put("team_alias", t.getTenantAlias());
            item.put("team_id", t.getTenantId());
            item.put("create_time", t.getCreateTime());
            item.put("region", regionNames.get(0));
            item.put("region_list", regionNames);
            item.put("enterprise_id", t.getEnterpriseId());
            item.put("owner", t.getCreater());
            item.put("owner_name", ownerName(t.getCreater()));
            item.put("roles", roles);
            item.put("is_pass", true);
            newJoin.add(item);
        }

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("active_teams", activeTop3);
        bean.put("new_join_team", newJoin);
        bean.put("request_join_team", new ArrayList<>()); // Applicants 域 defer
        return bean;
    }

    /** 用户在企业的团队（PermRelTenant by 企业 PK + user，按关联 -ID 去重）。 */
    private List<Tenants> userTeams(String enterpriseId, Integer userId) {
        Integer entPk = enterpriseRepository.findByEnterpriseId(enterpriseId).map(e -> e.getId()).orElse(null);
        if (entPk == null) {
            return List.of();
        }
        List<Integer> tenantPks = permRelTenantRepository.findByUserId(userId).stream()
                .filter(p -> entPk.equals(p.getEnterpriseId()))
                .sorted(Comparator.comparingInt(PermRelTenant::getId).reversed())
                .map(PermRelTenant::getTenantId)
                .distinct()
                .collect(Collectors.toList());
        if (tenantPks.isEmpty()) {
            return List.of();
        }
        Map<Integer, Tenants> byId = tenantsRepository.findByIdIn(tenantPks).stream()
                .collect(Collectors.toMap(Tenants::getId, t -> t));
        List<Tenants> out = new ArrayList<>();
        for (Integer pk : tenantPks) {
            Tenants t = byId.get(pk);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    private List<String> regionNames(String tenantId) {
        return tenantRegionInfoRepository.findByTenantId(tenantId).stream()
                .map(TenantRegionInfo::getRegionName).collect(Collectors.toList());
    }

    private String ownerName(Integer creater) {
        if (creater == null) {
            return "";
        }
        UserInfo u = userInfoRepository.findById(creater).orElse(null);
        if (u == null) {
            return "";
        }
        // 对齐 User.get_name()：real_name 非空则用之，否则 nick_name
        return (u.getRealName() != null && !u.getRealName().isEmpty()) ? u.getRealName() : u.getNickName();
    }
}
