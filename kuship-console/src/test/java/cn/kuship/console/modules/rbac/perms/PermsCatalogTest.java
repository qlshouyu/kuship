package cn.kuship.console.modules.rbac.perms;

import cn.kuship.console.modules.rbac.perms.PermsCatalog.AssembledPerm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验权限码体系与 7070（v6.9.0）一致：权限码/名无重复、企业 permissions 展开、团队权限树结构与打包语义。
 * 期望值取自 docs/p1b-7070-reference.md（interop 实测）。
 */
class PermsCatalogTest {

    @Test
    void no_duplicate_names_or_codes() {
        List<AssembledPerm> all = PermsCatalog.allTeamAndEnterprisePerms();
        List<String> names = all.stream().map(AssembledPerm::name).toList();
        List<Integer> codes = all.stream().map(AssembledPerm::code).toList();
        assertThat(names).doesNotHaveDuplicates();
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void enterprise_permissions_for_admin_match_7070() {
        Set<String> perms = PermsCatalog.listEnterprisePermsByRoles(List.of("admin"));
        assertThat(perms).containsExactlyInAnyOrder(
                "app_store.create_app", "app_store.edit_app", "app_store.delete_app", "app_store.import_app",
                "app_store.export_app", "app_store.create_app_store", "app_store.get_app_store",
                "app_store.edit_app_store", "app_store.delete_app_store", "app_store.edit_app_version",
                "app_store.delete_app_version", "app_store.get_ent_teams");
    }

    @Test
    void enterprise_permissions_for_empty_roles_keep_common() {
        // 无角色仍叠加 app_store.<common_perms>（6 项，对齐 list_enterprise_perms_by_roles 尾部）
        Set<String> perms = PermsCatalog.listEnterprisePermsByRoles(List.of());
        assertThat(perms).containsExactlyInAnyOrder(
                "app_store.create_app", "app_store.edit_app", "app_store.delete_app",
                "app_store.import_app", "app_store.get_app_store", "app_store.get_ent_teams");
    }

    @Test
    @SuppressWarnings("unchecked")
    void team_tree_structure_matches_7070() {
        Map<String, Object> tree = PermsCatalog.packRolePermsTree("team", PermsCatalog.team(), Set.of(), true);
        assertThat(tree).containsOnlyKeys("team");
        Map<String, Object> teamBody = (Map<String, Object>) tree.get("team");
        // 字段顺序：sub_models 先于 perms（对齐 7070）
        assertThat(new ArrayList<>(teamBody.keySet())).containsExactly("sub_models", "perms");
        List<Object> subs = (List<Object>) teamBody.get("sub_models");
        // 6 个子分组，顺序固定
        List<String> subNames = subs.stream().map(s -> ((Map<String, Object>) s).keySet().iterator().next()).toList();
        assertThat(subNames).containsExactly(
                "team_overview", "team_app_create", "team_app_manage",
                "team_gateway_manage", "team_plugin_manage", "team_manage");
        // team_overview 含 2 个叶子，owner 全 true
        Map<String, Object> overview = (Map<String, Object>) ((Map<String, Object>) subs.get(0)).get("team_overview");
        List<Map<String, Boolean>> ovPerms = (List<Map<String, Boolean>>) overview.get("perms");
        assertThat(ovPerms).hasSize(2);
        assertThat(ovPerms).allMatch(leaf -> leaf.values().iterator().next());
        assertThat(leafNames(ovPerms)).containsExactly("describe", "app_list");
    }

    @Test
    @SuppressWarnings("unchecked")
    void non_owner_only_true_for_matched_codes() {
        // 只给 team_overview.describe(200001) 的码，普通成员（isOwner=false）
        Map<String, Object> tree = PermsCatalog.packRolePermsTree("team", PermsCatalog.team(), Set.of(200001), false);
        List<Object> subs = (List<Object>) ((Map<String, Object>) tree.get("team")).get("sub_models");
        Map<String, Object> overview = (Map<String, Object>) ((Map<String, Object>) subs.get(0)).get("team_overview");
        List<Map<String, Boolean>> ovPerms = (List<Map<String, Boolean>>) overview.get("perms");
        // describe(200001)=true, app_list(200002)=false
        assertThat(ovPerms.get(0).get("describe")).isTrue();
        assertThat(ovPerms.get(1).get("app_list")).isFalse();
        // team_app_create.describe(300001) 未给码 → false
        Map<String, Object> appCreate = (Map<String, Object>) ((Map<String, Object>) subs.get(1)).get("team_app_create");
        List<Map<String, Boolean>> acPerms = (List<Map<String, Boolean>>) appCreate.get("perms");
        assertThat(acPerms.get(0).get("describe")).isFalse();
    }

    private static List<String> leafNames(List<Map<String, Boolean>> leaves) {
        return leaves.stream().map(m -> m.keySet().iterator().next()).collect(Collectors.toList());
    }
}
