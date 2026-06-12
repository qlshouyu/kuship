package cn.kuship.console.modules.account.service;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.enterprise.entity.EnterpriseUserPerm;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.EnterpriseUserPermRepository;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import cn.kuship.console.modules.team.entity.PermRelTenant;
import cn.kuship.console.modules.team.entity.TenantRegionInfo;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.PermRelTenantRepository;
import cn.kuship.console.modules.team.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 当前用户详情（GET /console/users/details）。字段对照 docs/p1a-7070-reference.md。
 * <p>RBAC 派生字段（permissions、每团队 tenant_actions/role_name_list）本轮返回空/默认，
 * owner 短路给 is_team_owner / enterprise admin 的 roles，完整 RBAC 留 P1-b。
 */
@Service
public class AccountProfileService {

    private final RequestContext requestContext;
    private final TenantEnterpriseRepository enterpriseRepository;
    private final EnterpriseUserPermRepository enterpriseUserPermRepository;
    private final TenantsRepository tenantsRepository;
    private final TenantRegionInfoRepository tenantRegionRepository;
    private final PermRelTenantRepository permRelTenantRepository;
    private final RegionConfigRepository regionConfigRepository;

    public AccountProfileService(RequestContext requestContext,
                                 TenantEnterpriseRepository enterpriseRepository,
                                 EnterpriseUserPermRepository enterpriseUserPermRepository,
                                 TenantsRepository tenantsRepository,
                                 TenantRegionInfoRepository tenantRegionRepository,
                                 PermRelTenantRepository permRelTenantRepository,
                                 RegionConfigRepository regionConfigRepository) {
        this.requestContext = requestContext;
        this.enterpriseRepository = enterpriseRepository;
        this.enterpriseUserPermRepository = enterpriseUserPermRepository;
        this.tenantsRepository = tenantsRepository;
        this.tenantRegionRepository = tenantRegionRepository;
        this.permRelTenantRepository = permRelTenantRepository;
        this.regionConfigRepository = regionConfigRepository;
    }

    public Map<String, Object> currentUserDetails() {
        UserInfo user = requestContext.getCurrentUser();
        String eid = user.getEnterpriseId();
        TenantEnterprise enterprise = enterpriseRepository.findByEnterpriseId(eid).orElse(null);

        EnterpriseUserPerm perm = enterpriseUserPermRepository
                .findFirstByEnterpriseIdAndUserId(eid, user.getUserId()).orElse(null);
        boolean isEntAdmin = perm != null
                && ("admin".equals(perm.getIdentity()) || Boolean.TRUE.equals(perm.getIsInitialEnterpriseAdmin()));
        boolean isInitialAdmin = perm != null && Boolean.TRUE.equals(perm.getIsInitialEnterpriseAdmin());

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("user_id", user.getUserId());
        bean.put("user_name", user.getNickName());
        bean.put("real_name", user.getRealName());
        bean.put("logo", user.getLogo());
        bean.put("email", user.getEmail());
        bean.put("enterprise_id", eid);
        bean.put("phone", user.getPhone());
        bean.put("is_sys_admin", Boolean.TRUE.equals(user.getSysAdmin()));
        bean.put("is_enterprise_active", enterprise == null ? 0 : enterprise.getIsActive());
        bean.put("is_enterprise_admin", isEntAdmin);
        bean.put("is_initial_enterprise_admin", isInitialAdmin);
        bean.put("roles", isEntAdmin ? List.of("admin") : new ArrayList<>());
        bean.put("permissions", new ArrayList<>()); // 企业权限码 → P1-b
        bean.put("teams", currentUserTeams(eid, user.getUserId()));
        bean.put("oauth_services", new ArrayList<>());
        return bean;
    }

    /** 当前用户的团队（成员 ∪ 自建），仅保留有 region 的团队（对齐 rainbond 过滤）。 */
    private List<Map<String, Object>> currentUserTeams(String enterpriseId, Integer userId) {
        List<Integer> pks = permRelTenantRepository.findByUserId(userId).stream()
                .map(PermRelTenant::getTenantId).collect(Collectors.toList());
        List<Tenants> teams = pks.isEmpty() ? new ArrayList<>() : tenantsRepository.findByIdIn(pks);
        for (Tenants t : tenantsRepository.findByEnterpriseId(enterpriseId)) {
            if (userId.equals(t.getCreater()) && teams.stream().noneMatch(x -> x.getId().equals(t.getId()))) {
                teams.add(t);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tenants t : teams) {
            List<Map<String, Object>> regions = richRegions(t.getTenantId());
            if (regions.isEmpty()) {
                continue; // 与 rainbond 一致：无 region 的团队不出现在 users/details
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("team_id", t.getId());
            m.put("team_name", t.getTenantName());
            m.put("team_alias", t.getTenantAlias());
            m.put("limit_memory", t.getLimitMemory());
            m.put("region", regions);
            m.put("creater", t.getCreater());
            m.put("create_time", t.getCreateTime());
            m.put("namespace", t.getNamespace());
            m.put("role_name_list", new ArrayList<>()); // RBAC 角色名 → P1-b
            m.put("tenant_actions", new LinkedHashMap<>()); // RBAC 权限树 → P1-b
            m.put("is_team_owner", userId.equals(t.getCreater()));
            out.add(m);
        }
        return out;
    }

    /** users/details 中每团队的富 region 对象（含 ws/tcp/region_id 等，源自 tenant_region + region_info）。 */
    private List<Map<String, Object>> richRegions(String tenantId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TenantRegionInfo tr : tenantRegionRepository.findByTenantId(tenantId)) {
            RegionConfig rc = regionConfigRepository.findByRegionName(tr.getRegionName()).orElse(null);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("service_status", tr.getServiceStatus());
            r.put("is_active", tr.getIsActive());
            r.put("region_status", rc == null ? null : rc.getStatus());
            r.put("team_region_alias", rc == null ? null : rc.getRegionAlias());
            r.put("region_tenant_id", tr.getRegionTenantId());
            r.put("team_region_name", tr.getRegionName());
            r.put("region_scope", tr.getRegionScope());
            r.put("region_create_time", rc == null ? null : rc.getCreateTime());
            r.put("websocket_uri", rc == null ? null : rc.getWsurl());
            r.put("tcpdomain", rc == null ? null : rc.getTcpdomain());
            r.put("region_id", rc == null ? null : rc.getRegionId());
            out.add(r);
        }
        return out;
    }
}
