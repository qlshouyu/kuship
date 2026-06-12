package cn.kuship.console.modules.authorization.service;

import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.rbac.perms.PermsCatalog;
import cn.kuship.console.modules.rbac.service.RbacReadService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户整型权限码计算（鉴权用），对齐 rainbond-console {@code base.py} 的两类 {@code get_perms}：
 * <ul>
 *   <li>企业级（{@code JWTAuthApiView.get_perms}）：企业角色 → 整型企业权限码；</li>
 *   <li>团队级（{@code TenantHeaderView.get_perms}）：企业码 ∪ 团队码（仅 owner 短路全团队码+100001，
 *       否则成员 role_perms 全局码并集）。</li>
 * </ul>
 * 企业管理员能过团队鉴权是因其 admin 角色经整型展开已含全部团队码（走企业码），非 owner 短路。
 */
@Service
public class AuthorizationService {

    /** 团队所有者额外拥有的"团队相关操作"码（对齐 rainbond get_perms 的 append(100001)）。 */
    private static final int TEAM_OWNER_EXTRA_CODE = 100001;

    private final RbacReadService rbacReadService;

    public AuthorizationService(RbacReadService rbacReadService) {
        this.rbacReadService = rbacReadService;
    }

    /** 企业级权限码：企业角色（identity）→ 整型企业权限码集合。 */
    public Set<Integer> enterprisePermCodes(UserInfo user) {
        List<String> roles = rbacReadService.listRoles(user.getEnterpriseId(), user.getUserId());
        return new HashSet<>(PermsCatalog.listEnterprisePermCodesByRoles(roles));
    }

    /**
     * 团队级权限码：企业码 ∪ 团队码。{@code isOwner}（user==team creater）→ 全部团队码 + 100001；
     * 否则取成员 role_perms 全局码并集。企业管理员不单独短路（见类注释）。
     */
    public Set<Integer> teamPermCodes(String tenantId, UserInfo user, boolean isOwner) {
        Set<Integer> codes = enterprisePermCodes(user);
        if (isOwner) {
            codes.addAll(PermsCatalog.allTeamPermCodes());
            codes.add(TEAM_OWNER_EXTRA_CODE);
        } else {
            codes.addAll(rbacReadService.teamMemberGlobalPermCodes(tenantId, user.getUserId()));
        }
        return codes;
    }

    /** 所需码是否被用户码完全覆盖（空所需码视为放行）。 */
    public boolean hasPerms(Set<Integer> userCodes, List<Integer> requiredCodes) {
        if (requiredCodes == null || requiredCodes.isEmpty()) {
            return true;
        }
        return userCodes.containsAll(requiredCodes);
    }
}
