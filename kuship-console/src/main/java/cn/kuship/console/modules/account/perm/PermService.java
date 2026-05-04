package cn.kuship.console.modules.account.perm;

import cn.kuship.console.modules.account.entity.EnterpriseUserPerm;
import cn.kuship.console.modules.account.entity.PermRelTenant;
import cn.kuship.console.modules.account.entity.PermsInfo;
import cn.kuship.console.modules.account.entity.RolePerms;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.entity.UserRole;
import cn.kuship.console.modules.account.repository.EnterpriseUserPermRepository;
import cn.kuship.console.modules.account.repository.PermRelTenantRepository;
import cn.kuship.console.modules.account.repository.PermsInfoRepository;
import cn.kuship.console.modules.account.repository.RolePermsRepository;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.account.repository.UserRoleRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 团队权限/企业管理员校验。带 60s 缓存（key=userId+tenantName 或 userId+enterpriseId）。
 *
 * <p>缓存策略：修改用户角色 / 角色权限的端点必须显式 {@link CacheEvict}。
 */
@Service
public class PermService {

    private final UserRoleRepository userRoleRepository;
    private final RolePermsRepository rolePermsRepository;
    private final PermsInfoRepository permsInfoRepository;
    private final PermRelTenantRepository permRelTenantRepository;
    private final TenantsRepository tenantsRepository;
    private final EnterpriseUserPermRepository enterpriseUserPermRepository;

    public PermService(UserRoleRepository userRoleRepository,
                        RolePermsRepository rolePermsRepository,
                        PermsInfoRepository permsInfoRepository,
                        PermRelTenantRepository permRelTenantRepository,
                        TenantsRepository tenantsRepository,
                        EnterpriseUserPermRepository enterpriseUserPermRepository) {
        this.userRoleRepository = userRoleRepository;
        this.rolePermsRepository = rolePermsRepository;
        this.permsInfoRepository = permsInfoRepository;
        this.permRelTenantRepository = permRelTenantRepository;
        this.tenantsRepository = tenantsRepository;
        this.enterpriseUserPermRepository = enterpriseUserPermRepository;
    }

    /**
     * 当前用户在指定 team 中拥有的所有权限码（按 perm name，与 rainbond 一致）。
     */
    @Cacheable(value = "user-team-perms", key = "#userId + ':' + #tenantName")
    public Set<String> userPermCodesForTeam(Integer userId, String tenantName) {
        if (userId == null || tenantName == null || tenantName.isBlank()) {
            return Set.of();
        }
        Optional<Tenants> tenantOpt = tenantsRepository.findByTenantName(tenantName);
        if (tenantOpt.isEmpty()) {
            return Set.of();
        }
        // user_role 用 String 类型 user_id（rainbond 该字段是 char(32)）
        List<UserRole> userRoles = userRoleRepository.findByUserId(String.valueOf(userId));
        if (userRoles.isEmpty()) {
            return Set.of();
        }
        List<Integer> roleIds = userRoles.stream()
                .map(ur -> {
                    try {
                        return Integer.parseInt(ur.getRoleId());
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        List<RolePerms> perms = rolePermsRepository.findByRoleIdIn(roleIds);
        if (perms.isEmpty()) {
            return Set.of();
        }
        Set<Integer> permCodes = perms.stream().map(RolePerms::getPermCode).collect(Collectors.toSet());
        Set<String> codes = new HashSet<>();
        for (PermsInfo info : permsInfoRepository.findAll()) {
            if (info.getCode() != null && permCodes.contains(info.getCode())) {
                codes.add(info.getName());
            }
        }
        return codes;
    }

    public boolean userHasPerm(Integer userId, String tenantName, String permCode) {
        return userPermCodesForTeam(userId, tenantName).contains(permCode);
    }

    public boolean userHasAnyPerm(Integer userId, String tenantName, String[] permCodes) {
        Set<String> owned = userPermCodesForTeam(userId, tenantName);
        for (String p : permCodes) {
            if (owned.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前用户是否团队 owner（{@code tenant_info.creater = userId}）。
     */
    public boolean isTeamOwner(Integer userId, String tenantName) {
        return tenantsRepository.findByTenantName(tenantName)
                .map(t -> userId != null && userId.equals(t.getCreater()))
                .orElse(false);
    }

    /**
     * 是否在 enterprise 内有 admin 身份。
     */
    @Cacheable(value = "user-enterprise-admin", key = "#userId + ':' + #enterpriseId")
    public boolean isEnterpriseAdmin(Integer userId, String enterpriseId) {
        if (userId == null || enterpriseId == null) {
            return false;
        }
        Optional<EnterpriseUserPerm> perm = enterpriseUserPermRepository
                .findByUserIdAndEnterpriseId(userId, enterpriseId);
        return perm.map(p -> "admin".equals(p.getIdentity())).orElse(false);
    }

    /**
     * 用户所在 team（通过 tenant_perms 关联）。
     */
    public List<PermRelTenant> userTeams(Integer userId) {
        return permRelTenantRepository.findByUserId(userId);
    }

    @CacheEvict(value = "user-team-perms", key = "#userId + ':' + #tenantName")
    public void evictUserTeamPerms(Integer userId, String tenantName) {
        // no-op; spring cache annotation 完成 evict
    }

    @CacheEvict(value = "user-enterprise-admin", key = "#userId + ':' + #enterpriseId")
    public void evictEnterpriseAdmin(Integer userId, String enterpriseId) {
        // no-op
    }
}
