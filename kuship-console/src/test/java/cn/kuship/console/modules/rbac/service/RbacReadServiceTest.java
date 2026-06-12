package cn.kuship.console.modules.rbac.service;

import cn.kuship.console.modules.enterprise.entity.EnterpriseUserPerm;
import cn.kuship.console.modules.enterprise.repository.EnterpriseUserPermRepository;
import cn.kuship.console.modules.rbac.entity.RoleInfo;
import cn.kuship.console.modules.rbac.entity.RolePerms;
import cn.kuship.console.modules.rbac.entity.UserRole;
import cn.kuship.console.modules.rbac.repository.RoleInfoRepository;
import cn.kuship.console.modules.rbac.repository.RolePermsRepository;
import cn.kuship.console.modules.rbac.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RbacReadService 解析语义：企业角色/权限、团队 role_name_list、tenant_actions 树
 * （owner/企业管理员全 true、普通成员按码、非成员空）。
 */
class RbacReadServiceTest {

    private final EnterpriseUserPermRepository entPermRepo = mock(EnterpriseUserPermRepository.class);
    private final RoleInfoRepository roleInfoRepo = mock(RoleInfoRepository.class);
    private final UserRoleRepository userRoleRepo = mock(UserRoleRepository.class);
    private final RolePermsRepository rolePermsRepo = mock(RolePermsRepository.class);

    private final RbacReadService service =
            new RbacReadService(entPermRepo, roleInfoRepo, userRoleRepo, rolePermsRepo);

    private static RoleInfo role(int id, String name, String tenantId) {
        RoleInfo r = new RoleInfo();
        r.setId(id);
        r.setName(name);
        r.setKind("team");
        r.setKindId(tenantId);
        return r;
    }

    private static UserRole userRole(String userId, String roleId) {
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        return ur;
    }

    private static RolePerms rolePerm(int roleId, int code, int appId) {
        RolePerms rp = new RolePerms();
        rp.setRoleId(roleId);
        rp.setPermCode(code);
        rp.setAppId(appId);
        return rp;
    }

    @Test
    void list_roles_splits_identity() {
        EnterpriseUserPerm perm = new EnterpriseUserPerm();
        perm.setIdentity("admin,app_store");
        when(entPermRepo.findFirstByEnterpriseIdAndUserId("e1", 1)).thenReturn(Optional.of(perm));
        assertThat(service.listRoles("e1", 1)).containsExactly("admin", "app_store");
    }

    @Test
    void list_roles_empty_when_no_record() {
        when(entPermRepo.findFirstByEnterpriseIdAndUserId("e1", 1)).thenReturn(Optional.empty());
        assertThat(service.listRoles("e1", 1)).isEmpty();
    }

    @Test
    void team_roles_only_within_team_scope() {
        String tid = "t1";
        when(roleInfoRepo.findByKindAndKindId("team", tid))
                .thenReturn(List.of(role(1, "管理员", tid), role(2, "开发者", tid)));
        // 用户有团队角色 1 与一个不属于该团队的角色 99
        when(userRoleRepo.findByUserId("700002"))
                .thenReturn(List.of(userRole("700002", "1"), userRole("700002", "99")));
        List<Map<String, Object>> out = service.getUserTeamRoles(tid, 700002);
        assertThat(out).hasSize(1);
        assertThat(out.get(0)).containsEntry("role_id", "1").containsEntry("role_name", "管理员");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tenant_actions_all_true_for_owner() {
        Map<String, Object> ta = service.getUserTeamActions("t1", 700002, true, false);
        assertThat(allLeavesTrue(ta)).isTrue();
        assertEmptyAppManage(ta);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tenant_actions_all_true_for_enterprise_admin() {
        Map<String, Object> ta = service.getUserTeamActions("t1", 700002, false, true);
        assertThat(allLeavesTrue(ta)).isTrue();
        assertEmptyAppManage(ta);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tenant_actions_by_codes_for_member() {
        String tid = "t1";
        when(roleInfoRepo.findByKindAndKindId("team", tid)).thenReturn(List.of(role(5, "观察者", tid)));
        when(userRoleRepo.findByUserId("3")).thenReturn(List.of(userRole("3", "5")));
        // 只给 team_overview.describe(200001) 全局码
        when(rolePermsRepo.findByRoleIdIn(anyList())).thenReturn(List.of(rolePerm(5, 200001, -1)));

        Map<String, Object> ta = service.getUserTeamActions(tid, 3, false, false);
        Map<String, Object> teamBody = (Map<String, Object>) ta.get("team");
        List<Object> subs = (List<Object>) teamBody.get("sub_models");
        Map<String, Object> overview = (Map<String, Object>) ((Map<String, Object>) subs.get(0)).get("team_overview");
        List<Map<String, Boolean>> ovPerms = (List<Map<String, Boolean>>) overview.get("perms");
        assertThat(ovPerms.get(0).get("describe")).isTrue();
        assertThat(ovPerms.get(1).get("app_list")).isFalse();
        assertEmptyAppManage(ta);
    }

    @Test
    void tenant_actions_member_without_roles_all_false() {
        String tid = "t1";
        when(roleInfoRepo.findByKindAndKindId("team", tid)).thenReturn(List.of(role(5, "观察者", tid)));
        when(userRoleRepo.findByUserId("9")).thenReturn(List.of()); // 非成员
        Map<String, Object> ta = service.getUserTeamActions(tid, 9, false, false);
        assertThat(allLeavesTrue(ta)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static boolean allLeavesTrue(Map<String, Object> tree) {
        for (Object v : tree.values()) {
            Map<String, Object> body = (Map<String, Object>) v;
            for (Object sub : (List<Object>) body.get("sub_models")) {
                if (!allLeavesTrue((Map<String, Object>) sub)) {
                    return false;
                }
            }
            Object perms = body.get("perms");
            if (perms instanceof List<?> leaves) {
                for (Object leaf : leaves) {
                    if (((Map<String, Boolean>) leaf).values().stream().anyMatch(b -> !b)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void assertEmptyAppManage(Map<String, Object> ta) {
        Map<String, Object> teamBody = (Map<String, Object>) ta.get("team");
        List<Object> subs = (List<Object>) teamBody.get("sub_models");
        Map<String, Object> appManage = null;
        for (Object sub : subs) {
            Map<String, Object> subMap = (Map<String, Object>) sub;
            if (subMap.containsKey("team_app_manage")) {
                appManage = (Map<String, Object>) subMap.get("team_app_manage");
            }
        }
        assertThat(appManage).isNotNull();
        assertThat((List<Object>) appManage.get("sub_models")).isEmpty();
        assertThat((Map<String, Object>) appManage.get("perms")).isEmpty();
    }
}
