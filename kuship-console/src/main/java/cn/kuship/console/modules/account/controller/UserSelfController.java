package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.page.PageRequestAdapter;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.dto.ConsoleConfigItem;
import cn.kuship.console.modules.account.dto.UserDetailDto;
import cn.kuship.console.modules.account.entity.ConsoleConfig;
import cn.kuship.console.modules.account.entity.PermRelTenant;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.account.repository.PermRelTenantRepository;
import cn.kuship.console.modules.account.service.CustomConfigsService;
import cn.kuship.console.modules.account.service.UserService;
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

    private final RequestContext requestContext;
    private final UserService userService;
    private final TenantsRepository tenantsRepository;
    private final PermRelTenantRepository permRelTenantRepository;
    private final PageRequestAdapter pageAdapter;
    private final CustomConfigsService customConfigsService;

    public UserSelfController(RequestContext requestContext,
                               UserService userService,
                               TenantsRepository tenantsRepository,
                               PermRelTenantRepository permRelTenantRepository,
                               PageRequestAdapter pageAdapter,
                               CustomConfigsService customConfigsService) {
        this.requestContext = requestContext;
        this.userService = userService;
        this.tenantsRepository = tenantsRepository;
        this.permRelTenantRepository = permRelTenantRepository;
        this.pageAdapter = pageAdapter;
        this.customConfigsService = customConfigsService;
    }

    @GetMapping(value = {"/details", "/details/"})
    public UserDetailDto details() {
        Integer userId = requireUser();
        UserInfo user = userService.findById(userId)
                .orElseThrow(() -> new ServiceHandleException(404, "user not found", "用户不存在"));
        return UserDetailDto.from(user);
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
