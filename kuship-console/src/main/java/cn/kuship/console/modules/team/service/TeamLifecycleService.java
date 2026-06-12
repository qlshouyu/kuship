package cn.kuship.console.modules.team.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.rbac.entity.RoleInfo;
import cn.kuship.console.modules.rbac.entity.RolePerms;
import cn.kuship.console.modules.rbac.entity.UserRole;
import cn.kuship.console.modules.rbac.perms.PermsCatalog;
import cn.kuship.console.modules.rbac.repository.RoleInfoRepository;
import cn.kuship.console.modules.rbac.repository.RolePermsRepository;
import cn.kuship.console.modules.rbac.repository.UserRoleRepository;
import cn.kuship.console.modules.team.entity.PermRelTenant;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.PermRelTenantRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 团队生命周期（对齐 rainbond AddTeamView/create_team 与 TeamDelView/delete_by_tenant_id，无 region 绑定路径）：
 * 创建团队（含 owner 关系、3 默认角色 + role_perms、创建者管理员角色）与删除团队（删 tenant_perms+tenant_info）。
 * useable_regions 非空时的 region provision 本轮不做（无 region 域）。
 */
@Service
public class TeamLifecycleService {

    private static final String KIND_TEAM = "team";
    private static final int GLOBAL_APP_ID = -1;
    private static final String ADMIN_ROLE = "管理员";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** k8s 命名规范：小写字母开头，仅 [a-z0-9-]，字母或数字结尾。 */
    private static final Pattern QUALIFIED_NAME = Pattern.compile("^[a-z]([-a-z0-9]*[a-z0-9])?$");
    private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final TenantsRepository tenantsRepository;
    private final TenantEnterpriseRepository enterpriseRepository;
    private final PermRelTenantRepository permRelTenantRepository;
    private final RoleInfoRepository roleInfoRepository;
    private final RolePermsRepository rolePermsRepository;
    private final UserRoleRepository userRoleRepository;
    private final Random random = new Random();

    public TeamLifecycleService(TenantsRepository tenantsRepository,
                                TenantEnterpriseRepository enterpriseRepository,
                                PermRelTenantRepository permRelTenantRepository,
                                RoleInfoRepository roleInfoRepository,
                                RolePermsRepository rolePermsRepository,
                                UserRoleRepository userRoleRepository) {
        this.tenantsRepository = tenantsRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.permRelTenantRepository = permRelTenantRepository;
        this.roleInfoRepository = roleInfoRepository;
        this.rolePermsRepository = rolePermsRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /** 创建团队（无 region 绑定路径），返回团队完整 bean。 */
    @Transactional
    public Map<String, Object> createTeam(Integer userId, String enterpriseId, String teamAlias,
                                          String namespace, String logo) {
        if (teamAlias == null || teamAlias.isBlank()) {
            throw ServiceHandleException.badRequest("failed", "团队名不能为空");
        }
        if (namespace != null && !namespace.isBlank() && !QUALIFIED_NAME.matcher(namespace).matches()) {
            throw ServiceHandleException.badRequest("invalid namespace name",
                    "命名空间只能由小写字母、数字或-组成，并且必须以字母开始、以数字或字母结尾");
        }
        if (tenantsRepository.findByTenantAliasAndEnterpriseId(teamAlias, enterpriseId).isPresent()) {
            throw ServiceHandleException.badRequest("failed", "该团队名已存在");
        }
        TenantEnterprise enterprise = enterpriseRepository.findByEnterpriseId(enterpriseId)
                .orElseThrow(() -> new ServiceHandleException(500, "user's enterprise is not found", "无企业信息"));

        Tenants team = new Tenants();
        team.setTenantName(randomTenantName());
        team.setTenantId(UUID.randomUUID().toString().replace("-", ""));
        team.setCreater(userId);
        team.setTenantAlias(teamAlias);
        team.setEnterpriseId(enterpriseId);
        team.setLimitMemory(0);
        team.setNamespace(namespace == null ? "" : namespace);
        team.setLogo(logo == null ? "" : logo);
        team.setIsActive(true);
        LocalDateTime now = LocalDateTime.now();
        team.setCreateTime(now);
        team.setUpdateTime(now);
        Tenants saved = tenantsRepository.save(team);

        PermRelTenant owner = new PermRelTenant();
        owner.setUserId(userId);
        owner.setTenantId(saved.getId());
        owner.setIdentity("owner");
        owner.setEnterpriseId(enterprise.getId());
        permRelTenantRepository.save(owner);

        initDefaultRoles(saved.getTenantId(), userId);
        return toDict(saved);
    }

    /** 初始化 3 默认角色 + 各 role_perms，并把管理员角色分配给创建者。 */
    private void initDefaultRoles(String tenantId, Integer creatorUserId) {
        Integer adminRoleId = null;
        for (Map.Entry<String, List<Integer>> e : PermsCatalog.DEFAULT_TEAM_ROLE_PERMS.entrySet()) {
            RoleInfo role = new RoleInfo();
            role.setKind(KIND_TEAM);
            role.setKindId(tenantId);
            role.setName(e.getKey());
            RoleInfo savedRole = roleInfoRepository.save(role);
            if (ADMIN_ROLE.equals(e.getKey())) {
                adminRoleId = savedRole.getId();
            }
            List<RolePerms> rows = new ArrayList<>();
            for (Integer code : e.getValue()) {
                RolePerms rp = new RolePerms();
                rp.setRoleId(savedRole.getId());
                rp.setPermCode(code);
                rp.setAppId(GLOBAL_APP_ID);
                rows.add(rp);
            }
            rolePermsRepository.saveAll(rows);
        }
        if (adminRoleId != null) {
            UserRole ur = new UserRole();
            ur.setUserId(String.valueOf(creatorUserId));
            ur.setRoleId(String.valueOf(adminRoleId));
            userRoleRepository.save(ur);
        }
    }

    /** 删除团队（无 region 路径）：删 tenant_perms + tenant_info（不级联 role_info/user_role，对齐 rainbond）。 */
    @Transactional
    public void deleteTeam(String teamName, String enterpriseId) {
        Tenants team = tenantsRepository.findByTenantNameAndEnterpriseId(teamName, enterpriseId)
                .orElseThrow(() -> new ServiceHandleException(404, "tenant not exist", teamName + "团队不存在"));
        permRelTenantRepository.deleteByTenantId(team.getId());
        tenantsRepository.delete(team);
    }

    private String randomTenantName() {
        String name;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(NAME_CHARS.charAt(random.nextInt(NAME_CHARS.length())));
            }
            name = sb.toString();
        } while (tenantsRepository.existsByTenantName(name));
        return name;
    }

    private Map<String, Object> toDict(Tenants t) {
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
        return m;
    }
}
