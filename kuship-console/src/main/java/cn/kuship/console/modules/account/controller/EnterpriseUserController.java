package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.page.PageRequestAdapter;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.dto.CreateEnterpriseUserReq;
import cn.kuship.console.modules.account.dto.UpdateUserRolesReq;
import cn.kuship.console.modules.account.entity.EnterpriseUserPerm;
import cn.kuship.console.modules.account.entity.PermRelTenant;
import cn.kuship.console.modules.account.entity.TenantEnterprise;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.account.perm.PermService;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import cn.kuship.console.modules.account.repository.EnterpriseUserPermRepository;
import cn.kuship.console.modules.account.repository.PermRelTenantRepository;
import cn.kuship.console.modules.account.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.account.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** enterprise 内用户管理与跨 team 角色调整。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}")
public class EnterpriseUserController {

    private final UserInfoRepository userRepo;
    private final TenantEnterpriseRepository enterpriseRepo;
    private final TenantsRepository tenantsRepo;
    private final PermRelTenantRepository permRelRepo;
    private final EnterpriseUserPermRepository enterpriseUserPermRepo;
    private final LegacyPasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final PermService permService;
    private final RequestContext requestContext;
    private final PageRequestAdapter pageAdapter;

    public EnterpriseUserController(UserInfoRepository userRepo,
                                      TenantEnterpriseRepository enterpriseRepo,
                                      TenantsRepository tenantsRepo,
                                      PermRelTenantRepository permRelRepo,
                                      EnterpriseUserPermRepository enterpriseUserPermRepo,
                                      LegacyPasswordEncoder passwordEncoder,
                                      RoleService roleService,
                                      PermService permService,
                                      RequestContext requestContext,
                                      PageRequestAdapter pageAdapter) {
        this.userRepo = userRepo;
        this.enterpriseRepo = enterpriseRepo;
        this.tenantsRepo = tenantsRepo;
        this.permRelRepo = permRelRepo;
        this.enterpriseUserPermRepo = enterpriseUserPermRepo;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
        this.permService = permService;
        this.requestContext = requestContext;
        this.pageAdapter = pageAdapter;
    }

    @GetMapping(value = {"/users", "/users/"})
    public ApiResult listUsers(@PathVariable("enterprise_id") String enterpriseId) {
        requireUser();
        List<UserInfo> users = userRepo.findByEnterpriseId(enterpriseId);
        return GeneralMessage.okList(users.stream().map(this::serializeUser).toList());
    }

    @PostMapping(value = {"/users", "/users/"})
    @RequireEnterpriseAdmin
    public ApiResult createUser(@PathVariable("enterprise_id") String enterpriseId,
                                  @RequestBody @Valid CreateEnterpriseUserReq req) {
        if (userRepo.existsByNickName(req.nickName())) {
            throw new ServiceHandleException(400, "nick_name exists", "用户名已被占用");
        }
        if (userRepo.existsByEmail(req.email())) {
            throw new ServiceHandleException(400, "email exists", "邮箱已被占用");
        }
        UserInfo u = new UserInfo();
        u.setNickName(req.nickName());
        u.setEmail(req.email());
        u.setRealName(req.realName());
        u.setPhone(req.phone());
        u.setEnterpriseId(enterpriseId);
        u.setActive(true);
        u.setSysAdmin(false);
        u.setCreateTime(LocalDateTime.now());
        u.setPassword(passwordEncoder.encode(req.email() + req.password()));
        userRepo.save(u);
        return GeneralMessage.ok(serializeUser(u));
    }

    @PutMapping(value = {"/user/{user_id}", "/user/{user_id}/"})
    @RequireEnterpriseAdmin
    public ApiResult updateUser(@PathVariable("enterprise_id") String enterpriseId,
                                  @PathVariable("user_id") Integer userId,
                                  @RequestBody Map<String, Object> body) {
        UserInfo u = userRepo.findById(userId)
                .orElseThrow(() -> new ServiceHandleException(404, "user not found", "用户不存在"));
        if (!enterpriseId.equals(u.getEnterpriseId())) {
            throw new ServiceHandleException(403, "user not in enterprise", "用户不属于该企业");
        }
        if (body.get("real_name") instanceof String s) u.setRealName(s);
        if (body.get("phone") instanceof String s) u.setPhone(s);
        if (body.get("logo") instanceof String s) u.setLogo(s);
        if (body.get("is_active") instanceof Boolean b) u.setActive(b);
        userRepo.save(u);
        return GeneralMessage.ok(serializeUser(u));
    }

    @DeleteMapping(value = {"/user/{user_id}", "/user/{user_id}/"})
    @RequireEnterpriseAdmin
    public ApiResult deleteUser(@PathVariable("enterprise_id") String enterpriseId,
                                  @PathVariable("user_id") Integer userId) {
        UserInfo u = userRepo.findById(userId)
                .orElseThrow(() -> new ServiceHandleException(404, "user not found", "用户不存在"));
        if (!enterpriseId.equals(u.getEnterpriseId())) {
            throw new ServiceHandleException(403, "user not in enterprise", "用户不属于该企业");
        }
        userRepo.delete(u);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/user/{user_id}/teams", "/user/{user_id}/teams/"})
    public ApiResult userTeams(@PathVariable("enterprise_id") String enterpriseId,
                                 @PathVariable("user_id") Integer userId) {
        requireUser();
        List<PermRelTenant> rels = permRelRepo.findByUserId(userId);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (PermRelTenant rel : rels) {
            tenantsRepo.findById(rel.getTenantId())
                    .filter(t -> enterpriseId.equals(t.getEnterpriseId()))
                    .ifPresent(t -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("tenant_id", t.getTenantId());
                        m.put("team_name", t.getTenantName());
                        m.put("team_alias", t.getTenantAlias());
                        m.put("identity", rel.getIdentity());
                        m.put("role_id", rel.getRoleId());
                        result.add(m);
                    });
        }
        return GeneralMessage.okList(result);
    }

    @GetMapping(value = {"/admin/user", "/admin/user/"})
    @RequireEnterpriseAdmin
    public ApiResult listAdmins(@PathVariable("enterprise_id") String enterpriseId) {
        List<EnterpriseUserPerm> admins = enterpriseUserPermRepo
                .findByEnterpriseIdAndIdentity(enterpriseId, "admin");
        List<Map<String, Object>> rows = admins.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("user_id", p.getUserId());
            m.put("identity", p.getIdentity());
            userRepo.findById(p.getUserId()).ifPresent(u -> {
                m.put("nick_name", u.getNickName());
                m.put("email", u.getEmail());
            });
            return m;
        }).toList();
        return GeneralMessage.okList(rows);
    }

    @PostMapping(value = {"/admin/user", "/admin/user/"})
    @RequireEnterpriseAdmin
    public ApiResult addAdmin(@PathVariable("enterprise_id") String enterpriseId,
                                @RequestBody Map<String, Object> body) {
        Integer userId = (Integer) body.get("user_id");
        if (userId == null) {
            throw new ServiceHandleException(400, "user_id required", "缺少 user_id 参数");
        }
        if (enterpriseUserPermRepo.findByUserIdAndEnterpriseId(userId, enterpriseId).isPresent()) {
            throw new ServiceHandleException(400, "already admin", "用户已是管理员");
        }
        EnterpriseUserPerm p = new EnterpriseUserPerm();
        p.setUserId(userId);
        p.setEnterpriseId(enterpriseId);
        p.setIdentity("admin");
        p.setToken(cn.kuship.console.common.util.UuidGenerator.makeUuid());
        enterpriseUserPermRepo.save(p);
        permService.evictEnterpriseAdmin(userId, enterpriseId);
        return GeneralMessage.ok();
    }

    @DeleteMapping(value = {"/admin/user/{user_id}", "/admin/user/{user_id}/"})
    @RequireEnterpriseAdmin
    public ApiResult removeAdmin(@PathVariable("enterprise_id") String enterpriseId,
                                   @PathVariable("user_id") Integer userId) {
        EnterpriseUserPerm p = enterpriseUserPermRepo.findByUserIdAndEnterpriseId(userId, enterpriseId)
                .orElseThrow(() -> new ServiceHandleException(404, "admin not found", "管理员不存在"));
        enterpriseUserPermRepo.delete(p);
        permService.evictEnterpriseAdmin(userId, enterpriseId);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/admin/roles", "/admin/roles/"})
    public ApiResult adminRoles(@PathVariable("enterprise_id") String enterpriseId) {
        requireUser();
        // 简化：返回管理员身份的固定枚举（rainbond 实际有 admin/manager/financial 等）
        List<Map<String, Object>> roles = List.of(
                Map.of("identity", "admin", "name", "企业管理员"),
                Map.of("identity", "manager", "name", "运维管理员"));
        return GeneralMessage.okList(roles);
    }

    @GetMapping(value = {"/users/{user_id}/teams/{tenant_name}/roles",
            "/users/{user_id}/teams/{tenant_name}/roles/"})
    @RequireEnterpriseAdmin
    public ApiResult getCrossTeamRoles(@PathVariable("enterprise_id") String enterpriseId,
                                          @PathVariable("user_id") Integer userId,
                                          @PathVariable("tenant_name") String tenantName) {
        Tenants team = tenantsRepo.findByTenantName(tenantName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        if (!enterpriseId.equals(team.getEnterpriseId())) {
            throw new ServiceHandleException(403, "team not in enterprise", "团队不属于该企业");
        }
        var rels = roleService.userRoles(userId);
        List<Integer> roleIds = rels.stream().map(ur -> {
            try { return Integer.parseInt(ur.getRoleId()); } catch (NumberFormatException e) { return null; }
        }).filter(java.util.Objects::nonNull).toList();
        return GeneralMessage.okList(roleIds);
    }

    @PutMapping(value = {"/users/{user_id}/teams/{tenant_name}/roles",
            "/users/{user_id}/teams/{tenant_name}/roles/"})
    @RequireEnterpriseAdmin
    public ApiResult updateCrossTeamRoles(@PathVariable("enterprise_id") String enterpriseId,
                                             @PathVariable("user_id") Integer userId,
                                             @PathVariable("tenant_name") String tenantName,
                                             @RequestBody @Valid UpdateUserRolesReq req) {
        Tenants team = tenantsRepo.findByTenantName(tenantName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        if (!enterpriseId.equals(team.getEnterpriseId())) {
            throw new ServiceHandleException(403, "team not in enterprise", "团队不属于该企业");
        }
        roleService.replaceUserRoles(userId, req);
        permService.evictUserTeamPerms(userId, tenantName);
        return GeneralMessage.ok();
    }

    private Integer requireUser() {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        return userId;
    }

    private Map<String, Object> serializeUser(UserInfo u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user_id", u.getUserId());
        m.put("nick_name", u.getNickName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("real_name", u.getRealName());
        m.put("is_active", u.getActive());
        m.put("enterprise_id", u.getEnterpriseId());
        return m;
    }
}
