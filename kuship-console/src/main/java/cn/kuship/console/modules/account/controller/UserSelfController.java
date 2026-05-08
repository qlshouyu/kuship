package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.page.PageRequestAdapter;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import tools.jackson.databind.ObjectMapper;
import cn.kuship.console.modules.account.dto.ConsoleConfigItem;
import cn.kuship.console.modules.account.entity.ConsoleConfig;
import cn.kuship.console.modules.account.entity.PermRelTenant;
import cn.kuship.console.modules.account.entity.TenantRegionInfo;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.PermRelTenantRepository;
import cn.kuship.console.modules.account.repository.TenantRegionRepository;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.account.service.CustomConfigsService;
import cn.kuship.console.modules.account.service.UserService;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /console/users/details} / {@code team_details} / {@code query}。 */
@RestController
@RequestMapping("/console/users")
public class UserSelfController {

    private static final Logger log = LoggerFactory.getLogger(UserSelfController.class);

    private final RequestContext requestContext;
    private final UserService userService;
    private final TenantsRepository tenantsRepository;
    private final PermRelTenantRepository permRelTenantRepository;
    private final TenantRegionRepository tenantRegionRepository;
    private final RegionInfoEntityRepository regionInfoRepository;
    private final PageRequestAdapter pageAdapter;
    private final CustomConfigsService customConfigsService;
    private final ObjectMapper objectMapper;
    private final Resource tenantActionsAdminTemplate;
    private volatile Map<String, Object> tenantActionsAdminCache;

    public UserSelfController(RequestContext requestContext,
                               UserService userService,
                               TenantsRepository tenantsRepository,
                               PermRelTenantRepository permRelTenantRepository,
                               TenantRegionRepository tenantRegionRepository,
                               RegionInfoEntityRepository regionInfoRepository,
                               PageRequestAdapter pageAdapter,
                               CustomConfigsService customConfigsService,
                               ObjectMapper objectMapper,
                               @Value("classpath:static/perms/tenant-actions-admin.json") Resource tenantActionsAdminTemplate) {
        this.requestContext = requestContext;
        this.userService = userService;
        this.tenantsRepository = tenantsRepository;
        this.permRelTenantRepository = permRelTenantRepository;
        this.tenantRegionRepository = tenantRegionRepository;
        this.regionInfoRepository = regionInfoRepository;
        this.pageAdapter = pageAdapter;
        this.customConfigsService = customConfigsService;
        this.objectMapper = objectMapper;
        this.tenantActionsAdminTemplate = tenantActionsAdminTemplate;
    }

    /**
     * 复刻 rainbond {@code views/user_operation.py::UserDetailsView.get} 输出契约：
     * 含 {@code teams[]}（每项含 {@code region[]}）+ {@code is_enterprise_admin}，
     * UI {@code layouts/Auto.js} 据此选择登录后跳转目标
     * （有团队 → /team/{name}/region/{region}/index；无团队 + 企业管理员 → /enterprise/.../index；否则 → /account/center/personal）。
     *
     * <p>占位字段（依赖未迁移的能力，UI 端均有 {@code ||} 兜底）：
     * <ul>
     *   <li>{@code roles} / {@code permissions}：依赖企业级 RBAC 角色查询，待 follow-up</li>
     *   <li>{@code tenant_actions} / {@code role_name_list}：每个 team 的角色 / 权限码列表，待 follow-up</li>
     *   <li>{@code oauth_services}：依赖未迁移的 OAuthServices 表</li>
     *   <li>{@code is_enterprise_active}：占位 false；依赖企业激活流程</li>
     * </ul>
     */
    @GetMapping(value = {"/details", "/details/"})
    public Map<String, Object> details() {
        Integer userId = requireUser();
        UserInfo user = userService.findById(userId)
                .orElseThrow(() -> new ServiceHandleException(404, "user not found", "用户不存在"));

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("user_id", user.getUserId());
        bean.put("user_name", user.getNickName());
        bean.put("real_name", user.getRealName());
        bean.put("logo", user.getLogo());
        bean.put("email", user.getEmail());
        bean.put("enterprise_id", user.getEnterpriseId());
        bean.put("phone", user.getPhone());
        boolean sysAdmin = Boolean.TRUE.equals(user.getSysAdmin());
        bean.put("is_sys_admin", sysAdmin);
        bean.put("is_enterprise_active", false);
        // sys_admin 视为 enterprise_admin（rainbond 历史等价；独立 enterprise admin 角色查询留作 follow-up）
        bean.put("is_enterprise_admin", sysAdmin);
        bean.put("roles", List.of());
        bean.put("permissions", List.of());
        bean.put("teams", buildTeams(userId));
        bean.put("oauth_services", List.of());
        return bean;
    }

    private List<Map<String, Object>> buildTeams(Integer userId) {
        List<Map<String, Object>> teams = new java.util.ArrayList<>();
        List<PermRelTenant> rels = permRelTenantRepository.findByUserId(userId);
        for (PermRelTenant rel : rels) {
            tenantsRepository.findById(rel.getTenantId()).ifPresent(t -> {
                List<Map<String, Object>> regions = buildRegions(t.getTenantId());
                if (regions.isEmpty()) {
                    // 与 rainbond UserDetailsView 行为一致：没 region 的 team 不进列表
                    return;
                }
                boolean isOwner = t.getCreater() != null && t.getCreater().equals(userId);
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("team_id", t.getId());
                tm.put("team_name", t.getTenantName());
                tm.put("team_alias", t.getTenantAlias());
                tm.put("limit_memory", t.getLimitMemory());
                tm.put("region", regions);
                tm.put("creater", t.getCreater());
                tm.put("create_time", t.getCreateTime());
                tm.put("namespace", t.getNamespace());
                tm.put("role_name_list", List.of());
                // tenant_actions 是嵌套权限树，UI utils/newRole.js::queryPermissionsInfo 在这树里
                // 递归查找 team_app_create / team_member 等节点决定显示什么按钮 / 菜单。
                // sys_admin / team_owner 直接返完整 admin 模板（解锁所有 dashboard 元素）；
                // 普通成员细粒度 RBAC 留作 follow-up，当前同样返 admin 模板（先解锁，避免新建应用等卡片消失）。
                tm.put("tenant_actions", loadAdminTenantActions());
                tm.put("is_team_owner", isOwner);
                teams.add(tm);
            });
        }
        return teams;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadAdminTenantActions() {
        Map<String, Object> cached = tenantActionsAdminCache;
        if (cached != null) return cached;
        try (var in = tenantActionsAdminTemplate.getInputStream()) {
            cached = objectMapper.readValue(in, Map.class);
            tenantActionsAdminCache = cached;
            return cached;
        } catch (java.io.IOException | RuntimeException e) {
            log.warn("[UserSelf] failed to load tenant_actions admin template; returning empty perms", e);
            return Map.of();
        }
    }

    private List<Map<String, Object>> buildRegions(String tenantUuid) {
        List<TenantRegionInfo> regionRels = tenantRegionRepository.findByTenantId(tenantUuid);
        return regionRels.stream().map(tr -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("team_region_name", tr.getRegionName());
            r.put("region_tenant_id", tr.getRegionTenantId());
            r.put("is_active", Boolean.TRUE.equals(tr.getActive()));
            r.put("service_status", tr.getServiceStatus() != null ? tr.getServiceStatus() : 0);
            r.put("region_scope", tr.getRegionScope());
            regionInfoRepository.findByRegionName(tr.getRegionName()).ifPresent(ri -> {
                r.put("region_id", ri.getRegionId());
                r.put("team_region_alias", ri.getRegionAlias());
                r.put("region_status", ri.getStatus());
                r.put("websocket_uri", ri.getWsurl());
                r.put("tcpdomain", ri.getTcpdomain());
                r.put("region_create_time", ri.getCreateTime());
            });
            return r;
        }).toList();
    }

    @GetMapping(value = {"/team_details", "/team_details/"})
    public ApiResult teamDetails() {
        Integer userId = requireUser();
        List<PermRelTenant> rels = permRelTenantRepository.findByUserId(userId);
        List<Map<String, Object>> teams = new java.util.ArrayList<>();
        for (PermRelTenant rel : rels) {
            tenantsRepository.findById(rel.getTenantId()).ifPresent(t -> teams.add(serializeTeam(t, rel)));
        }
        return GeneralMessage.okList(teams);
    }

    @GetMapping(value = {"/query", "/query/"})
    public ApiResult query(@RequestParam(name = "query", required = false) String query,
                            @RequestParam(name = "page", required = false) Integer page,
                            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        requireUser();
        Page<UserInfo> result = userService.search(query, pageAdapter.toPageable(page, pageSize));
        List<Map<String, Object>> users = result.getContent().stream()
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("user_id", u.getUserId());
                    m.put("nick_name", u.getNickName());
                    m.put("email", u.getEmail());
                    m.put("phone", u.getPhone());
                    return m;
                })
                .toList();
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("total", result.getTotalElements());
        return GeneralMessage.okWithExtras(bean, users, null);
    }

    @GetMapping(value = {"/custom_configs", "/custom_configs/"})
    public ApiResult getCustomConfigs() {
        requireUser();
        String nick = requestContext.getUsername();
        List<ConsoleConfigItem> items = customConfigsService.list(nick).stream()
                .map(ConsoleConfigItem::from).toList();
        return GeneralMessage.okList(items);
    }

    @PutMapping(value = {"/custom_configs", "/custom_configs/"})
    public ApiResult putCustomConfigs(@RequestBody List<ConsoleConfigItem> items) {
        requireUser();
        if (items == null) {
            throw new ServiceHandleException(400, "request body must be a list", "请求参数必须为列表");
        }
        String nick = requestContext.getUsername();
        List<ConsoleConfigItem> saved = customConfigsService.bulkCreateOrUpdate(items, nick).stream()
                .map(ConsoleConfigItem::from).toList();
        return GeneralMessage.okList(saved);
    }

    private Integer requireUser() {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        return userId;
    }

    private Map<String, Object> serializeTeam(Tenants t, PermRelTenant rel) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenant_id", t.getTenantId());
        m.put("team_name", t.getTenantName());
        m.put("team_alias", t.getTenantAlias());
        m.put("namespace", t.getNamespace());
        m.put("enterprise_id", t.getEnterpriseId());
        m.put("identity", rel.getIdentity());
        m.put("role_id", rel.getRoleId());
        return m;
    }
}
