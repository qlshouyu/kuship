package cn.kuship.console.modules.rbac.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.rbac.entity.RoleInfo;
import cn.kuship.console.modules.rbac.entity.RolePerms;
import cn.kuship.console.modules.rbac.perms.PermsCatalog;
import cn.kuship.console.modules.rbac.repository.RoleInfoRepository;
import cn.kuship.console.modules.rbac.repository.RolePermsRepository;
import cn.kuship.console.modules.rbac.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 团队角色管理写（对齐 rainbond {@code RoleKindService} / {@code RolePermService}）：
 * 角色 CRUD、角色权限树读（list/single）、权限树更新（事务重建 role_perms）。
 * 默认角色（{@code kind_id="default"}）不可改删；写联调用可丢弃角色，勿动既有默认 3 角色。
 */
@Service
public class TeamRoleWriteService {

    private static final String KIND_TEAM = "team";
    private static final String DEFAULT_KIND_ID = "default";
    private static final int GLOBAL_APP_ID = -1;

    private final RoleInfoRepository roleInfoRepository;
    private final RolePermsRepository rolePermsRepository;
    private final UserRoleRepository userRoleRepository;
    private final cn.kuship.console.modules.app.repository.ServiceGroupRepository serviceGroupRepository;

    public TeamRoleWriteService(RoleInfoRepository roleInfoRepository,
                                RolePermsRepository rolePermsRepository,
                                UserRoleRepository userRoleRepository,
                                cn.kuship.console.modules.app.repository.ServiceGroupRepository serviceGroupRepository) {
        this.roleInfoRepository = roleInfoRepository;
        this.rolePermsRepository = rolePermsRepository;
        this.userRoleRepository = userRoleRepository;
        this.serviceGroupRepository = serviceGroupRepository;
    }

    private List<String> withDefaultKindIds(String tenantId) {
        return List.of(tenantId, DEFAULT_KIND_ID);
    }

    // ---- 角色 CRUD ----

    /** 团队角色（含默认），每项 {name, ID}（对齐 TeamRolesLCView.get 的 list）。 */
    public List<Map<String, Object>> listRoles(String tenantId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RoleInfo r : roleInfoRepository.findByKindAndKindIdInOrderById(KIND_TEAM, withDefaultKindIds(tenantId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getName());
            m.put("ID", r.getId());
            out.add(m);
        }
        return out;
    }

    /** 创建角色：名非空 + 唯一（含默认）；新角色 kind=team/kind_id=tenant_id。返回全 to_dict bean（含 kind/kind_id）。 */
    public Map<String, Object> createRole(String tenantId, String name) {
        if (name == null || name.isBlank()) {
            throw ServiceHandleException.badRequest("role name exit", "角色名称不能为空");
        }
        if (roleInfoRepository.findByKindAndKindIdInAndName(KIND_TEAM, withDefaultKindIds(tenantId), name).isPresent()) {
            throw ServiceHandleException.badRequest("role name exit", "角色名称已存在");
        }
        RoleInfo role = new RoleInfo();
        role.setKind(KIND_TEAM);
        role.setKindId(tenantId);
        role.setName(name);
        RoleInfo saved = roleInfoRepository.save(role);
        return toDict(saved, true);
    }

    /** 角色详情（含默认），bean 去 kind/kind_id。 */
    public Map<String, Object> getRole(String tenantId, Integer roleId) {
        RoleInfo role = requireRoleWithDefault(tenantId, roleId);
        return toDict(role, false);
    }

    /** 改名：名非空 + 唯一（与他角色重名报错；与自身同名幂等）；仅团队自有角色可改。bean 去 kind/kind_id。 */
    public Map<String, Object> updateRole(String tenantId, Integer roleId, String name) {
        if (name == null || name.isBlank()) {
            throw ServiceHandleException.badRequest("role name exit", "角色名称不能为空");
        }
        RoleInfo existing = roleInfoRepository
                .findByKindAndKindIdInAndName(KIND_TEAM, withDefaultKindIds(tenantId), name).orElse(null);
        if (existing != null) {
            if (!existing.getId().equals(roleId)) {
                throw ServiceHandleException.badRequest("role name exit", "角色名称已存在");
            }
            return toDict(existing, false); // 同名幂等
        }
        RoleInfo role = roleInfoRepository.findByKindAndKindIdAndId(KIND_TEAM, tenantId, roleId)
                .orElseThrow(() -> new ServiceHandleException(404, "role no found", "角色不存在或为默认角色"));
        role.setName(name);
        return toDict(roleInfoRepository.save(role), false);
    }

    /** 删除团队自有角色（事务连带删 role_perms + user_role）；默认/不存在拒绝。返回被删角色名（用于日志/文案）。 */
    @Transactional
    public String deleteRole(String tenantId, Integer roleId) {
        RoleInfo role = roleInfoRepository.findByKindAndKindIdAndId(KIND_TEAM, tenantId, roleId)
                .orElseThrow(() -> new ServiceHandleException(404, "role no found or is default", "角色不存在或为默认角色"));
        rolePermsRepository.deleteByRoleId(role.getId());
        userRoleRepository.deleteByRoleId(String.valueOf(role.getId()));
        roleInfoRepository.delete(role);
        return role.getName();
    }

    // ---- 角色权限树 ----

    /** 全部团队角色（含默认）的权限树（对齐 get_roles_perms）：list 项 {role_id(int), permissions}，team_app_manage 保留模型 7 子模型。 */
    public List<Map<String, Object>> listRolesPerms(String tenantId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RoleInfo r : roleInfoRepository.findByKindAndKindIdInOrderById(KIND_TEAM, withDefaultKindIds(tenantId))) {
            Set<Integer> codes = rolePermsRepository.findByRoleId(r.getId()).stream()
                    .map(RolePerms::getPermCode).collect(Collectors.toSet());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role_id", r.getId()); // int
            item.put("permissions", PermsCatalog.packRolePermsTree("team", PermsCatalog.team(), codes, false));
            out.add(item);
        }
        return out;
    }

    /** 单角色权限树（对齐 get_role_perms）：bean {role_id(string), permissions}，team_app_manage 按团队应用重建。 */
    public Map<String, Object> getRolePerms(String tenantId, Integer roleId) {
        RoleInfo role = requireRoleWithDefault(tenantId, roleId);
        List<RolePerms> perms = rolePermsRepository.findByRoleId(role.getId());
        Set<Integer> globalCodes = perms.stream()
                .filter(rp -> GLOBAL_APP_ID == (rp.getAppId() == null ? GLOBAL_APP_ID : rp.getAppId()))
                .map(RolePerms::getPermCode).collect(Collectors.toSet());
        Map<Integer, Set<Integer>> appCodes = new java.util.HashMap<>();
        for (RolePerms rp : perms) {
            int appId = rp.getAppId() == null ? GLOBAL_APP_ID : rp.getAppId();
            if (appId != GLOBAL_APP_ID) {
                appCodes.computeIfAbsent(appId, k -> new java.util.HashSet<>()).add(rp.getPermCode());
            }
        }
        Map<String, Object> tree = PermsCatalog.packRolePermsTree("team", PermsCatalog.team(), globalCodes, false);
        List<Integer> appIds = serviceGroupRepository.findByTenantId(tenantId).stream()
                .map(g -> g.getId()).collect(Collectors.toList());
        PermsCatalog.applyAppManage(tree, appIds, appCodes, false);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("role_id", String.valueOf(role.getId())); // string
        bean.put("permissions", tree);
        return bean;
    }

    /** 更新角色权限树（事务）：删该角色 role_perms，再把提交树降维落库。返回最新权限树 bean。 */
    @Transactional
    public Map<String, Object> updateRolePerms(String tenantId, Integer roleId, Map<String, Object> permsTree) {
        RoleInfo role = requireRoleWithDefault(tenantId, roleId);
        rolePermsRepository.deleteByRoleId(role.getId());
        List<RolePerms> rows = new ArrayList<>();
        for (PermsCatalog.RolePermCode c : PermsCatalog.unpackRolePermsTree(permsTree)) {
            RolePerms rp = new RolePerms();
            rp.setRoleId(role.getId());
            rp.setPermCode(c.code());
            rp.setAppId(c.appId());
            rows.add(rp);
        }
        rolePermsRepository.saveAll(rows);
        return getRolePerms(tenantId, roleId);
    }

    // ---- 内部 ----

    private RoleInfo requireRoleWithDefault(String tenantId, Integer roleId) {
        return roleInfoRepository.findByKindAndKindIdInAndId(KIND_TEAM, withDefaultKindIds(tenantId), roleId)
                .orElseThrow(() -> new ServiceHandleException(404, "role no found", "角色不存在"));
    }

    /** RoleInfo → bean；{@code withKind=true} 保留 kind/kind_id（create），否则去除（RUD get/put）。 */
    private Map<String, Object> toDict(RoleInfo role, boolean withKind) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", role.getId());
        m.put("name", role.getName());
        if (withKind) {
            m.put("kind_id", role.getKindId());
            m.put("kind", role.getKind());
        }
        return m;
    }
}
