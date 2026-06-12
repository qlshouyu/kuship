package cn.kuship.console.modules.rbac.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.enterprise.repository.EnterpriseUserPermRepository;
import cn.kuship.console.modules.rbac.entity.RoleInfo;
import cn.kuship.console.modules.rbac.entity.UserRole;
import cn.kuship.console.modules.rbac.repository.RoleInfoRepository;
import cn.kuship.console.modules.rbac.repository.UserRoleRepository;
import cn.kuship.console.modules.team.entity.PermRelTenant;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.PermRelTenantRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 成员角色服务：列表/角色总览（拥有者标记）、单成员角色、角色重建/交集校验/清空。 */
class TeamMemberRoleServiceTest {

    private final PermRelTenantRepository permRel = mock(PermRelTenantRepository.class);
    private final UserInfoRepository userRepo = mock(UserInfoRepository.class);
    private final RoleInfoRepository roleRepo = mock(RoleInfoRepository.class);
    private final UserRoleRepository userRoleRepo = mock(UserRoleRepository.class);
    private final EnterpriseUserPermRepository entPermRepo = mock(EnterpriseUserPermRepository.class);
    private final RbacReadService rbac = mock(RbacReadService.class);
    private final TeamMemberRoleService service =
            new TeamMemberRoleService(permRel, userRepo, roleRepo, userRoleRepo, entPermRepo, rbac);

    private static final String TID = "t-uuid";

    private static Tenants team(int creater) {
        Tenants t = new Tenants();
        t.setId(1);
        t.setTenantId(TID);
        t.setCreater(creater);
        return t;
    }

    private static UserInfo user(int id, String nick) {
        UserInfo u = new UserInfo();
        u.setUserId(id);
        u.setNickName(nick);
        u.setEmail(nick + "@k.cn");
        return u;
    }

    private static PermRelTenant member(int uid) {
        PermRelTenant p = new PermRelTenant();
        p.setUserId(uid);
        p.setTenantId(1);
        return p;
    }

    private static RoleInfo role(int id, String name) {
        RoleInfo r = new RoleInfo();
        r.setId(id);
        r.setName(name);
        r.setKind("team");
        r.setKindId(TID);
        return r;
    }

    private void teamHas(int... userIds) {
        List<PermRelTenant> rels = new java.util.ArrayList<>();
        List<UserInfo> users = new java.util.ArrayList<>();
        for (int id : userIds) {
            rels.add(member(id));
            users.add(user(id, "u" + id));
        }
        when(permRel.findByTenantId(1)).thenReturn(rels);
        when(userRepo.findByUserIdIn(anyList())).thenReturn(users);
        when(roleRepo.findByKindAndKindId("team", TID)).thenReturn(List.of(role(1, "管理员"), role(2, "开发者")));
    }

    @Test
    void users_roles_marks_owner() {
        teamHas(700002);
        when(userRoleRepo.findByUserIdAndRoleIdIn(eq("700002"), anyList())).thenReturn(List.of());
        List<Map<String, Object>> data = service.getUsersRoles(team(700002));
        assertThat(data).hasSize(1);
        List<Map<String, Object>> roles = (List<Map<String, Object>>) data.get(0).get("roles");
        assertThat(roles).anySatisfy(r -> {
            assertThat(r.get("role_id")).isEqualTo(0);
            assertThat(r.get("role_name")).isEqualTo("拥有者");
        });
    }

    @Test
    void get_user_roles_returns_string_role_id() {
        teamHas(700003);
        UserRole ur = new UserRole();
        ur.setUserId("700003");
        ur.setRoleId("2");
        when(userRoleRepo.findByUserIdAndRoleIdIn(eq("700003"), anyList())).thenReturn(List.of(ur));
        Map<String, Object> bean = service.getUserRoles(team(700002), 700003);
        assertThat(bean).containsEntry("user_id", 700003).containsEntry("nick_name", "u700003");
        List<Map<String, Object>> roles = (List<Map<String, Object>>) bean.get("roles");
        assertThat(roles.get(0)).containsEntry("role_id", "2").containsEntry("role_name", "开发者");
    }

    @Test
    void update_user_roles_rebuilds() {
        teamHas(700003);
        when(userRoleRepo.findByUserIdAndRoleIdIn(eq("700003"), anyList())).thenReturn(List.of());
        service.updateUserRoles(team(700002), 700003, List.of(2));
        verify(userRoleRepo).deleteByUserIdAndRoleIdIn(eq("700003"), anyList());
        verify(userRoleRepo).saveAll(anyList());
    }

    @Test
    void update_user_roles_rejects_all_invalid() {
        teamHas(700003);
        assertThatThrownBy(() -> service.updateUserRoles(team(700002), 700003, List.of(9999)))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("no found can update");
    }

    @Test
    void delete_user_roles_clears() {
        teamHas(700003);
        when(userRoleRepo.findByUserIdAndRoleIdIn(eq("700003"), anyList())).thenReturn(List.of());
        service.deleteUserRoles(team(700002), 700003);
        verify(userRoleRepo).deleteByUserIdAndRoleIdIn(eq("700003"), anyList());
    }

    @Test
    void non_member_rejected() {
        teamHas(700002);
        assertThatThrownBy(() -> service.getUserRoles(team(700002), 999999))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("no found user");
    }

    @Test
    @SuppressWarnings("unchecked")
    void user_perms_wraps_tree_with_user_id() {
        teamHas(700003);
        when(rbac.getUserTeamActions(eq(TID), eq(700003), any(Boolean.class), any(Boolean.class)))
                .thenReturn(Map.of("team", Map.of()));
        UserInfo requester = user(700002, "interop");
        requester.setEnterpriseId("e1");
        when(entPermRepo.findFirstByEnterpriseIdAndUserId("e1", 700002)).thenReturn(java.util.Optional.empty());
        Map<String, Object> bean = service.getUserPerms(team(700002), 700003, requester);
        assertThat(bean).containsEntry("user_id", 700003).containsKey("permissions");
    }
}
