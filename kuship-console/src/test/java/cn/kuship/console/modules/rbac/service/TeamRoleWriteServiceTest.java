package cn.kuship.console.modules.rbac.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.rbac.entity.RoleInfo;
import cn.kuship.console.modules.rbac.entity.RolePerms;
import cn.kuship.console.modules.rbac.perms.PermsCatalog;
import cn.kuship.console.modules.rbac.repository.RoleInfoRepository;
import cn.kuship.console.modules.rbac.repository.RolePermsRepository;
import cn.kuship.console.modules.rbac.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 角色写服务：创建/重名/改名/删除连带清理/默认角色保护/权限树读写。 */
class TeamRoleWriteServiceTest {

    private final RoleInfoRepository roleRepo = mock(RoleInfoRepository.class);
    private final RolePermsRepository permRepo = mock(RolePermsRepository.class);
    private final UserRoleRepository userRoleRepo = mock(UserRoleRepository.class);
    private final TeamRoleWriteService service = new TeamRoleWriteService(roleRepo, permRepo, userRoleRepo);

    private static final String TID = "t-uuid";

    private static RoleInfo role(int id, String name, String kindId) {
        RoleInfo r = new RoleInfo();
        r.setId(id);
        r.setName(name);
        r.setKind("team");
        r.setKindId(kindId);
        return r;
    }

    @Test
    void create_role_returns_full_dict() {
        when(roleRepo.findByKindAndKindIdInAndName(eq("team"), anyList(), eq("p1d_tmp"))).thenReturn(Optional.empty());
        when(roleRepo.save(any())).thenAnswer(inv -> {
            RoleInfo r = inv.getArgument(0);
            r.setId(4);
            return r;
        });
        Map<String, Object> bean = service.createRole(TID, "p1d_tmp");
        assertThat(bean).containsEntry("ID", 4).containsEntry("name", "p1d_tmp")
                .containsEntry("kind", "team").containsEntry("kind_id", TID);
    }

    @Test
    void create_role_rejects_duplicate_name() {
        when(roleRepo.findByKindAndKindIdInAndName(eq("team"), anyList(), eq("管理员")))
                .thenReturn(Optional.of(role(1, "管理员", TID)));
        assertThatThrownBy(() -> service.createRole(TID, "管理员"))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("role name");
    }

    @Test
    void create_role_rejects_blank_name() {
        assertThatThrownBy(() -> service.createRole(TID, "  ")).isInstanceOf(ServiceHandleException.class);
    }

    @Test
    void update_role_strips_kind() {
        when(roleRepo.findByKindAndKindIdInAndName(eq("team"), anyList(), eq("newname"))).thenReturn(Optional.empty());
        when(roleRepo.findByKindAndKindIdAndId("team", TID, 4)).thenReturn(Optional.of(role(4, "old", TID)));
        when(roleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> bean = service.updateRole(TID, 4, "newname");
        assertThat(bean).containsOnlyKeys("ID", "name");
        assertThat(bean).containsEntry("name", "newname");
    }

    @Test
    void update_role_same_name_idempotent() {
        when(roleRepo.findByKindAndKindIdInAndName(eq("team"), anyList(), eq("dev")))
                .thenReturn(Optional.of(role(4, "dev", TID)));
        Map<String, Object> bean = service.updateRole(TID, 4, "dev");
        assertThat(bean).containsEntry("ID", 4).containsEntry("name", "dev");
        verify(roleRepo, never()).save(any());
    }

    @Test
    void delete_role_cascades() {
        when(roleRepo.findByKindAndKindIdAndId("team", TID, 4)).thenReturn(Optional.of(role(4, "tmp", TID)));
        String name = service.deleteRole(TID, 4);
        assertThat(name).isEqualTo("tmp");
        verify(permRepo).deleteByRoleId(4);
        verify(userRoleRepo).deleteByRoleId("4");
        verify(roleRepo).delete(any());
    }

    @Test
    void delete_missing_role_rejected() {
        when(roleRepo.findByKindAndKindIdAndId("team", TID, 99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteRole(TID, 99))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("role no found");
        verify(permRepo, never()).deleteByRoleId(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_role_perms_single_string_id_and_empty_app_manage() {
        when(roleRepo.findByKindAndKindIdInAndId(eq("team"), anyList(), eq(1))).thenReturn(Optional.of(role(1, "管理员", TID)));
        RolePerms rp = new RolePerms();
        rp.setRoleId(1);
        rp.setPermCode(200001);
        rp.setAppId(-1);
        when(permRepo.findByRoleId(1)).thenReturn(List.of(rp));
        Map<String, Object> bean = service.getRolePerms(TID, 1);
        assertThat(bean.get("role_id")).isEqualTo("1"); // string
        Map<String, Object> tree = (Map<String, Object>) bean.get("permissions");
        List<Object> subs = (List<Object>) ((Map<String, Object>) tree.get("team")).get("sub_models");
        Map<String, Object> am = (Map<String, Object>) ((Map<String, Object>) subs.get(2)).get("team_app_manage");
        assertThat((List<Object>) am.get("sub_models")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_roles_perms_int_id_and_keeps_app_submodels() {
        when(roleRepo.findByKindAndKindIdInOrderById(eq("team"), anyList())).thenReturn(List.of(role(1, "管理员", TID)));
        when(permRepo.findByRoleId(1)).thenReturn(List.of());
        List<Map<String, Object>> list = service.listRolesPerms(TID);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("role_id")).isEqualTo(1); // int
        Map<String, Object> tree = (Map<String, Object>) list.get(0).get("permissions");
        List<Object> subs = (List<Object>) ((Map<String, Object>) tree.get("team")).get("sub_models");
        Map<String, Object> am = (Map<String, Object>) ((Map<String, Object>) subs.get(2)).get("team_app_manage");
        assertThat((List<Object>) am.get("sub_models")).hasSize(7); // 模型默认 7 个 app 子模型
    }

    @Test
    void update_role_perms_rebuilds_role_perms() {
        when(roleRepo.findByKindAndKindIdInAndId(eq("team"), anyList(), eq(4))).thenReturn(Optional.of(role(4, "tmp", TID)));
        when(permRepo.findByRoleId(4)).thenReturn(List.of());
        Map<String, Object> tree = PermsCatalog.packRolePermsTree("team", PermsCatalog.team(), java.util.Set.of(200001), false);
        Map<String, Object> body = service.updateRolePerms(TID, 4, tree);
        verify(permRepo).deleteByRoleId(4);
        verify(permRepo).saveAll(anyList());
        assertThat(body).containsKey("permissions");
    }
}
