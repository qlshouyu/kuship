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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 团队成员角色管理（对齐 rainbond {@code UserKindRoleService}/{@code UserKindPermService} + team 成员查询）：
 * 成员列表/角色读、成员角色写（{@code user_role} 重建）、成员权限树视图。本轮只写 {@code user_role}，
 * 不动 {@code tenant_perms}（成员加入/移除留 P1-f）。
 */
@Service
public class TeamMemberRoleService {

    private static final String KIND_TEAM = "team";
    private static final int PAGE_SIZE = 8;

    private final PermRelTenantRepository permRelTenantRepository;
    private final UserInfoRepository userInfoRepository;
    private final RoleInfoRepository roleInfoRepository;
    private final UserRoleRepository userRoleRepository;
    private final EnterpriseUserPermRepository enterpriseUserPermRepository;
    private final RbacReadService rbacReadService;

    public TeamMemberRoleService(PermRelTenantRepository permRelTenantRepository,
                                 UserInfoRepository userInfoRepository,
                                 RoleInfoRepository roleInfoRepository,
                                 UserRoleRepository userRoleRepository,
                                 EnterpriseUserPermRepository enterpriseUserPermRepository,
                                 RbacReadService rbacReadService) {
        this.permRelTenantRepository = permRelTenantRepository;
        this.userInfoRepository = userInfoRepository;
        this.roleInfoRepository = roleInfoRepository;
        this.userRoleRepository = userRoleRepository;
        this.enterpriseUserPermRepository = enterpriseUserPermRepository;
        this.rbacReadService = rbacReadService;
    }

    /** 团队成员用户（tenant_perms 按团队 PK → user_info），可按 query 模糊 nick/real_name。 */
    public List<UserInfo> getTeamUsers(Tenants team, String query) {
        List<Integer> userIds = permRelTenantRepository.findByTenantId(team.getId()).stream()
                .map(PermRelTenant::getUserId).collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<UserInfo> users = userInfoRepository.findByUserIdIn(userIds);
        if (query != null && !query.isBlank()) {
            String q = query;
            users = users.stream().filter(u ->
                    (u.getNickName() != null && u.getNickName().contains(q))
                            || (u.getRealName() != null && u.getRealName().contains(q)))
                    .collect(Collectors.toList());
        }
        return users;
    }

    /** 团队角色 ID→名 映射。 */
    private Map<String, String> teamRoleIdName(String tenantId) {
        Map<String, String> m = new LinkedHashMap<>();
        for (RoleInfo r : roleInfoRepository.findByKindAndKindId(KIND_TEAM, tenantId)) {
            m.put(String.valueOf(r.getId()), r.getName());
        }
        return m;
    }

    /** 某用户在团队的角色项 [{role_id(string), role_name}]（对齐 get_user_roles 的 roles）。 */
    private List<Map<String, Object>> userTeamRoleItems(String tenantId, Integer userId, Map<String, String> idName) {
        List<String> roleIds = new ArrayList<>(idName.keySet());
        List<Map<String, Object>> out = new ArrayList<>();
        if (roleIds.isEmpty()) {
            return out;
        }
        for (UserRole ur : userRoleRepository.findByUserIdAndRoleIdIn(String.valueOf(userId), roleIds)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role_id", ur.getRoleId());
            item.put("role_name", idName.get(ur.getRoleId()));
            out.add(item);
        }
        return out;
    }

    /** 用户在团队的角色名列表（对齐 get_user_roles["roles"]，不含 owner 追加）。 */
    public List<String> userTeamRoleNames(String tenantId, Integer userId) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> it : userTeamRoleItems(tenantId, userId, teamRoleIdName(tenantId))) {
            names.add(String.valueOf(it.get("role_name")));
        }
        return names;
    }

    /**
     * 成员分页列表（对齐 TeamUserView.get）：每项 {user_id,user_name,nick_name,email,role_info}；
     * role_info 取「请求者」在该团队的角色（忠实 rainbond 现状，逐行相同）。返回 {list, total}。
     */
    public Map<String, Object> listUsers(Tenants team, UserInfo requester, String query, int page) {
        List<UserInfo> users = getTeamUsers(team, query);
        List<Map<String, String>> requesterRoles = teamRoleIdName(team.getTenantId()).entrySet().stream()
                .filter(e -> !userRoleRepository
                        .findByUserIdAndRoleIdIn(String.valueOf(requester.getUserId()), List.of(e.getKey())).isEmpty())
                .map(e -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("role_id", e.getKey());
                    m.put("role_name", e.getValue());
                    return m;
                }).collect(Collectors.toList());

        List<Map<String, Object>> all = new ArrayList<>();
        for (UserInfo u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("user_id", u.getUserId());
            m.put("user_name", u.getNickName());
            m.put("nick_name", u.getNickName());
            m.put("email", u.getEmail());
            m.put("role_info", requesterRoles);
            all.add(m);
        }
        int total = all.size();
        int from = Math.max(0, (page - 1) * PAGE_SIZE);
        int to = Math.min(all.size(), from + PAGE_SIZE);
        List<Map<String, Object>> pageList = from >= all.size() ? new ArrayList<>() : all.subList(from, to);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", pageList);
        out.put("total", total);
        return out;
    }

    /** 全部成员 + 角色（对齐 get_users_roles）：创建者额外附 {role_id:0,role_name:"拥有者"}。 */
    public List<Map<String, Object>> getUsersRoles(Tenants team) {
        Map<String, String> idName = teamRoleIdName(team.getTenantId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserInfo u : getTeamUsers(team, null)) {
            List<Map<String, Object>> roles = userTeamRoleItems(team.getTenantId(), u.getUserId(), idName);
            if (u.getUserId().equals(team.getCreater())) {
                Map<String, Object> owner = new LinkedHashMap<>();
                owner.put("role_id", 0); // int 0
                owner.put("role_name", "拥有者");
                roles.add(owner);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nick_name", u.getNickName());
            m.put("email", u.getEmail());
            m.put("user_id", u.getUserId());
            m.put("roles", roles);
            out.add(m);
        }
        return out;
    }

    /** 单成员角色（对齐 get_user_roles）：{nick_name,user_id,roles}。 */
    public Map<String, Object> getUserRoles(Tenants team, Integer userId) {
        UserInfo user = requireMember(team, userId);
        Map<String, String> idName = teamRoleIdName(team.getTenantId());
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("nick_name", user.getNickName());
        bean.put("user_id", user.getUserId());
        bean.put("roles", userTeamRoleItems(team.getTenantId(), userId, idName));
        return bean;
    }

    /** 重建成员团队角色（对齐 update_user_roles）：删该用户团队角色 + roleIds∩团队角色 批量写。 */
    @Transactional
    public Map<String, Object> updateUserRoles(Tenants team, Integer userId, List<Integer> roleIds) {
        UserInfo user = requireMember(team, userId);
        List<String> teamRoleIds = new ArrayList<>(teamRoleIdName(team.getTenantId()).keySet());
        // 删该用户在团队角色内的 user_role
        if (!teamRoleIds.isEmpty()) {
            userRoleRepository.deleteByUserIdAndRoleIdIn(String.valueOf(userId), teamRoleIds);
        }
        List<String> requested = roleIds == null ? List.of()
                : roleIds.stream().map(String::valueOf).collect(Collectors.toList());
        List<String> toAssign = requested.stream().filter(teamRoleIds::contains).collect(Collectors.toList());
        if (toAssign.isEmpty() && !requested.isEmpty()) {
            throw new ServiceHandleException(404, "no found can update params", "传入角色不可被分配，请检查参数");
        }
        List<UserRole> rows = new ArrayList<>();
        for (String rid : toAssign) {
            UserRole ur = new UserRole();
            ur.setUserId(String.valueOf(userId));
            ur.setRoleId(rid);
            rows.add(ur);
        }
        userRoleRepository.saveAll(rows);
        return getUserRoles(team, userId);
    }

    /** 清空成员团队角色（对齐 delete_user_roles）。 */
    @Transactional
    public Map<String, Object> deleteUserRoles(Tenants team, Integer userId) {
        requireMember(team, userId);
        List<String> teamRoleIds = new ArrayList<>(teamRoleIdName(team.getTenantId()).keySet());
        if (!teamRoleIds.isEmpty()) {
            userRoleRepository.deleteByUserIdAndRoleIdIn(String.valueOf(userId), teamRoleIds);
        }
        return getUserRoles(team, userId);
    }

    /**
     * 成员权限树视图（对齐 TeamUserPermsLView）：{user_id, permissions}。
     * 用「请求者」的 is_owner/is_ent_admin（rainbond 现状）。
     */
    public Map<String, Object> getUserPerms(Tenants team, Integer targetUserId, UserInfo requester) {
        boolean requesterOwner = team.getCreater() != null && team.getCreater().equals(requester.getUserId());
        boolean requesterEntAdmin = enterpriseUserPermRepository
                .findFirstByEnterpriseIdAndUserId(requester.getEnterpriseId(), requester.getUserId()).isPresent();
        Map<String, Object> tree = rbacReadService
                .getUserTeamActions(team.getTenantId(), targetUserId, requesterOwner, requesterEntAdmin);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("user_id", targetUserId);
        bean.put("permissions", tree);
        return bean;
    }

    /**
     * 未加入该团队的企业用户（对齐 get_not_join_users）：企业用户 − 团队成员，query 模糊 nick_name，分页。
     * 返回 {list, page, page_size, total}。
     */
    public Map<String, Object> listNotJoinUsers(Tenants team, String enterpriseId, String query, int page, int pageSize) {
        java.util.Set<Integer> memberIds = permRelTenantRepository.findByTenantId(team.getId()).stream()
                .map(PermRelTenant::getUserId).collect(Collectors.toSet());
        List<UserInfo> candidates = userInfoRepository.findByEnterpriseId(enterpriseId).stream()
                .filter(u -> !memberIds.contains(u.getUserId()))
                .filter(u -> query == null || query.isBlank()
                        || (u.getNickName() != null && u.getNickName().contains(query)))
                .collect(Collectors.toList());
        int total = candidates.size();
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(candidates.size(), from + pageSize);
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserInfo u : (from >= candidates.size() ? List.<UserInfo>of() : candidates.subList(from, to))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("user_id", u.getUserId());
            m.put("nick_name", u.getNickName());
            m.put("enterprise_id", u.getEnterpriseId());
            m.put("email", u.getEmail());
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("page", page);
        out.put("page_size", pageSize);
        out.put("total", total);
        return out;
    }

    /**
     * 批量移除团队成员（对齐 batch_delete_users + UserDelView 校验）：
     * 空/含自身/含创建者 → 400；否则事务删 tenant_perms（成员关系）+ 团队内 user_role。
     */
    @Transactional
    public void batchRemoveMembers(Tenants team, Integer requesterUserId, List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw ServiceHandleException.badRequest("failed", "删除成员不能为空");
        }
        if (userIds.contains(requesterUserId)) {
            throw ServiceHandleException.badRequest("failed", "不能删除自己");
        }
        if (team.getCreater() != null && userIds.contains(team.getCreater())) {
            throw ServiceHandleException.badRequest("failed", "不能删除团队创建者！");
        }
        permRelTenantRepository.deleteByUserIdInAndTenantId(userIds, team.getId());
        List<String> teamRoleIds = new ArrayList<>(teamRoleIdName(team.getTenantId()).keySet());
        if (!teamRoleIds.isEmpty()) {
            List<String> userIdStrs = userIds.stream().map(String::valueOf).collect(Collectors.toList());
            userRoleRepository.deleteByUserIdInAndRoleIdIn(userIdStrs, teamRoleIds);
        }
    }

    /** 目标必须是团队成员，否则"用户不存在"（对齐 get_team_users().filter(user_id=..) 取空）。 */
    private UserInfo requireMember(Tenants team, Integer userId) {
        return getTeamUsers(team, null).stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ServiceHandleException(404, "no found user", "用户不存在"));
    }
}
