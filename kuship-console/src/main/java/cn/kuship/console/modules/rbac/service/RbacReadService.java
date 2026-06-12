package cn.kuship.console.modules.rbac.service;

import cn.kuship.console.modules.enterprise.entity.EnterpriseUserPerm;
import cn.kuship.console.modules.enterprise.repository.EnterpriseUserPermRepository;
import cn.kuship.console.modules.rbac.entity.RoleInfo;
import cn.kuship.console.modules.rbac.entity.RolePerms;
import cn.kuship.console.modules.rbac.entity.UserRole;
import cn.kuship.console.modules.rbac.perms.PermsCatalog;
import cn.kuship.console.modules.rbac.repository.RoleInfoRepository;
import cn.kuship.console.modules.rbac.repository.RolePermsRepository;
import cn.kuship.console.modules.rbac.repository.UserRoleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 读路径解析：企业角色/权限、团队角色名（role_name_list）、团队权限树（tenant_actions）。
 * 对照 rainbond-console {@code user_services.list_roles}、{@code user_kind_role_service.get_user_roles}、
 * {@code user_kind_perm_service.get_user_perms}。本类只读，不写。
 */
@Service
public class RbacReadService {

    private static final int GLOBAL_APP_ID = -1;
    private static final String KIND_TEAM = "team";

    private final EnterpriseUserPermRepository enterpriseUserPermRepository;
    private final RoleInfoRepository roleInfoRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermsRepository rolePermsRepository;

    public RbacReadService(EnterpriseUserPermRepository enterpriseUserPermRepository,
                           RoleInfoRepository roleInfoRepository,
                           UserRoleRepository userRoleRepository,
                           RolePermsRepository rolePermsRepository) {
        this.enterpriseUserPermRepository = enterpriseUserPermRepository;
        this.roleInfoRepository = roleInfoRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermsRepository = rolePermsRepository;
    }

    /** 企业角色名列表（对齐 {@code list_roles}）：enterprise_user_perm.identity 逗号分隔；无记录返回空。 */
    public List<String> listRoles(String enterpriseId, Integer userId) {
        EnterpriseUserPerm perm = enterpriseUserPermRepository
                .findFirstByEnterpriseIdAndUserId(enterpriseId, userId).orElse(null);
        if (perm == null || perm.getIdentity() == null || perm.getIdentity().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> roles = new ArrayList<>();
        for (String r : perm.getIdentity().split(",")) {
            roles.add(r);
        }
        return roles;
    }

    /** 企业权限标识集合（对齐 {@code list_enterprise_perms_by_roles}）。 */
    public List<String> listEnterprisePermissions(List<String> roles) {
        return new ArrayList<>(PermsCatalog.listEnterprisePermsByRoles(roles));
    }

    /**
     * 用户在某团队下的角色项列表（对齐 {@code get_user_roles} 的 roles）：
     * 每项 {@code {role_id(字符串), role_name}}。kindId 取 tenant_id。
     */
    public List<Map<String, Object>> getUserTeamRoles(String tenantId, Integer userId) {
        List<RoleInfo> roles = roleInfoRepository.findByKindAndKindId(KIND_TEAM, tenantId);
        Map<String, String> idToName = new HashMap<>();
        for (RoleInfo r : roles) {
            idToName.put(String.valueOf(r.getId()), r.getName());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserRole ur : userRoleRepository.findByUserId(String.valueOf(userId))) {
            if (!idToName.containsKey(ur.getRoleId())) {
                continue; // 仅保留该团队范围内的角色
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role_id", ur.getRoleId());
            item.put("role_name", idToName.get(ur.getRoleId()));
            out.add(item);
        }
        return out;
    }

    /**
     * 用户在某团队下的权限树 tenant_actions（对齐 {@code get_user_perms} → {@code get_roles_union_perms}）：
     * owner 或企业管理员短路为全 true；否则取其团队角色的全局（app_id=-1）权限码并集装配。
     * {@code team_app_manage} 节点按团队应用列表重建——kuship 暂无应用域，恒为空（见 docs/p1b-7070-reference.md §4）。
     */
    public Map<String, Object> getUserTeamActions(String tenantId, Integer userId, boolean isOwner, boolean isEntAdmin) {
        boolean owner = isOwner || isEntAdmin;
        Set<Integer> trueCodes = owner ? Set.of() : globalPermCodes(tenantId, userId);
        Map<String, Object> tree = PermsCatalog.packRolePermsTree("team", PermsCatalog.team(), trueCodes, owner);
        applyEmptyAppManage(tree);
        return tree;
    }

    /** 普通成员：其团队角色的全局（app_id=-1）权限码并集。 */
    private Set<Integer> globalPermCodes(String tenantId, Integer userId) {
        List<RoleInfo> teamRoles = roleInfoRepository.findByKindAndKindId(KIND_TEAM, tenantId);
        Set<String> teamRoleIds = teamRoles.stream().map(r -> String.valueOf(r.getId())).collect(Collectors.toSet());
        List<Integer> userTeamRoleIds = userRoleRepository.findByUserId(String.valueOf(userId)).stream()
                .map(UserRole::getRoleId)
                .filter(teamRoleIds::contains)
                .map(Integer::valueOf)
                .collect(Collectors.toList());
        if (userTeamRoleIds.isEmpty()) {
            return Set.of();
        }
        return rolePermsRepository.findByRoleIdIn(userTeamRoleIds).stream()
                .filter(rp -> GLOBAL_APP_ID == (rp.getAppId() == null ? GLOBAL_APP_ID : rp.getAppId()))
                .map(RolePerms::getPermCode)
                .collect(Collectors.toSet());
    }

    /**
     * 用团队应用列表重建 team_app_manage 节点；kuship 无应用域 → {@code {"sub_models": [], "perms": {}}}。
     * 对齐 {@code get_roles_union_perms} 对 sub_models[2] 的覆盖。
     */
    @SuppressWarnings("unchecked")
    private void applyEmptyAppManage(Map<String, Object> tree) {
        Map<String, Object> teamBody = (Map<String, Object>) tree.get("team");
        List<Object> subs = (List<Object>) teamBody.get("sub_models");
        for (Object sub : subs) {
            Map<String, Object> subMap = (Map<String, Object>) sub;
            if (subMap.containsKey("team_app_manage")) {
                Map<String, Object> emptyApp = new LinkedHashMap<>();
                emptyApp.put("sub_models", new ArrayList<>());
                emptyApp.put("perms", new LinkedHashMap<>());
                subMap.put("team_app_manage", emptyApp);
                return;
            }
        }
    }
}
