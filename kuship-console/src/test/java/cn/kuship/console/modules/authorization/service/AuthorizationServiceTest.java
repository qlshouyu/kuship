package cn.kuship.console.modules.authorization.service;

import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.rbac.perms.PermsCatalog;
import cn.kuship.console.modules.rbac.service.RbacReadService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 鉴权码计算：企业码展开、团队码（owner 短路 / admin 经企业码 / 成员按码）、hasPerms 交集语义。
 */
class AuthorizationServiceTest {

    private final RbacReadService rbac = mock(RbacReadService.class);
    private final AuthorizationService service = new AuthorizationService(rbac);

    private static UserInfo user(int id, String eid) {
        UserInfo u = new UserInfo();
        u.setUserId(id);
        u.setEnterpriseId(eid);
        return u;
    }

    @Test
    void enterprise_admin_codes_contain_all_team_codes() {
        UserInfo u = user(1, "e1");
        when(rbac.listRoles("e1", 1)).thenReturn(List.of("admin"));
        Set<Integer> codes = service.enterprisePermCodes(u);
        assertThat(codes).containsAll(PermsCatalog.allTeamPermCodes());
        assertThat(codes).contains(200001);
    }

    @Test
    void team_owner_gets_all_team_codes_plus_100001() {
        UserInfo u = user(2, "e1");
        when(rbac.listRoles("e1", 2)).thenReturn(List.of()); // 非企业 admin
        Set<Integer> codes = service.teamPermCodes("t1", u, true);
        assertThat(codes).containsAll(PermsCatalog.allTeamPermCodes());
        assertThat(codes).contains(100001, 200001);
    }

    @Test
    void enterprise_admin_passes_team_via_enterprise_codes_without_owner() {
        UserInfo u = user(3, "e1");
        when(rbac.listRoles("e1", 3)).thenReturn(List.of("admin"));
        // 非 owner，也不查成员码（admin 企业码已覆盖团队码）
        Set<Integer> codes = service.teamPermCodes("t1", u, false);
        assertThat(codes).contains(200001);
        assertThat(service.hasPerms(codes, List.of(200001))).isTrue();
    }

    @Test
    void member_gets_only_role_codes() {
        UserInfo u = user(4, "e1");
        when(rbac.listRoles("e1", 4)).thenReturn(List.of());           // 无企业角色 → 仅 common 码
        when(rbac.teamMemberGlobalPermCodes("t1", 4)).thenReturn(Set.of(200002)); // 角色只给 app_list
        Set<Integer> codes = service.teamPermCodes("t1", u, false);
        assertThat(codes).contains(200002);
        assertThat(codes).doesNotContain(200001); // 未授予 describe
        assertThat(service.hasPerms(codes, List.of(200001))).isFalse();
    }

    @Test
    void has_perms_empty_required_allows() {
        assertThat(service.hasPerms(Set.of(), List.of())).isTrue();
        assertThat(service.hasPerms(Set.of(), null)).isTrue();
    }
}
