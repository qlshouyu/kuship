package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.page.PageRequestAdapter;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.PermRelTenant;
import cn.kuship.console.modules.account.entity.TenantEnterprise;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.PermRelTenantRepository;
import cn.kuship.console.modules.account.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /console/enterprise/info}（公开） / {@code /console/enterprises} / {@code /console/enterprise/{id}} 等。 */
@RestController
@RequestMapping("/console")
public class EnterpriseController {

    private final TenantEnterpriseRepository enterpriseRepo;
    private final TenantsRepository tenantsRepo;
    private final PermRelTenantRepository permRelRepo;
    private final RequestContext requestContext;
    private final PageRequestAdapter pageAdapter;

    public EnterpriseController(TenantEnterpriseRepository enterpriseRepo,
                                  TenantsRepository tenantsRepo,
                                  PermRelTenantRepository permRelRepo,
                                  RequestContext requestContext,
                                  PageRequestAdapter pageAdapter) {
        this.enterpriseRepo = enterpriseRepo;
        this.tenantsRepo = tenantsRepo;
        this.permRelRepo = permRelRepo;
        this.requestContext = requestContext;
        this.pageAdapter = pageAdapter;
    }

    /** 公开端点：仅返回脱敏的平台默认 enterprise 信息（不含 token / 用户列表）。 */
    @GetMapping(value = {"/enterprise/info", "/enterprise/info/"})
    public ApiResult info() {
        TenantEnterprise enterprise = enterpriseRepo.findFirstByIsActiveOrderByIdAsc(1)
                .or(() -> enterpriseRepo.findFirstByIsActiveOrderByIdAsc(0))
                .orElse(null);
        if (enterprise == null) {
            return GeneralMessage.ok(Map.of());
        }
        return GeneralMessage.ok(serializePublic(enterprise));
    }

    /** 当前用户所属所有 enterprise（已认证）。 */
    @GetMapping(value = {"/enterprises", "/enterprises/"})
    public ApiResult myEnterprises() {
        Integer userId = requireUser();
        // 通过 user_info.enterprise_id 至少包含一个；后续可扩展 enterprise_user_perm 拿全部
        TenantEnterprise primary = null;
        if (requestContext.getEnterpriseId() != null) {
            primary = enterpriseRepo.findByEnterpriseId(requestContext.getEnterpriseId()).orElse(null);
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        if (primary != null) {
            rows.add(serialize(primary));
        }
        return GeneralMessage.okList(rows);
    }

    @GetMapping(value = {"/enterprise/{enterprise_id}", "/enterprise/{enterprise_id}/"})
    public ApiResult get(@PathVariable("enterprise_id") String enterpriseId) {
        requireUser();
        TenantEnterprise enterprise = enterpriseRepo.findByEnterpriseId(enterpriseId)
                .orElseThrow(() -> new ServiceHandleException(404, "enterprise not found", "企业不存在"));
        return GeneralMessage.ok(serialize(enterprise));
    }

    /** 对齐 rainbond {@code EnterpriseRUDView}（{@code views/enterprise.py:127}）：
     *  返回 enterprise 字段 + default_region 占位 + EnterpriseConfigService 的配置（kuship 暂不接入，留空）。 */
    @GetMapping(value = {"/enterprise/{enterprise_id}/info", "/enterprise/{enterprise_id}/info/"})
    public ApiResult getInfo(@PathVariable("enterprise_id") String enterpriseId) {
        requireUser();
        TenantEnterprise enterprise = enterpriseRepo.findByEnterpriseId(enterpriseId)
                .orElseThrow(() -> new ServiceHandleException(404, "enterprise not found", "企业不存在"));
        Map<String, Object> ent = serialize(enterprise);
        ent.put("default_region", Map.of());
        return GeneralMessage.ok(ent);
    }

    /** 对齐 rainbond {@code PlatformSettingsView}（{@code views/platform_settings.py:9}）。 */
    @GetMapping(value = {"/enterprise/{enterprise_id}/platform-settings",
            "/enterprise/{enterprise_id}/platform-settings/"})
    public ApiResult platformSettings(@PathVariable("enterprise_id") String enterpriseId) {
        requireUser();
        TenantEnterprise enterprise = enterpriseRepo.findByEnterpriseId(enterpriseId)
                .orElseThrow(() -> new ServiceHandleException(404, "enterprise not found", "企业不存在"));
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("enable_team_resource_view", Boolean.TRUE.equals(enterprise.getEnableTeamResourceView()));
        return GeneralMessage.ok(bean);
    }

    @PutMapping(value = {"/enterprise/{enterprise_id}", "/enterprise/{enterprise_id}/"})
    public ApiResult update(@PathVariable("enterprise_id") String enterpriseId,
                              @RequestBody Map<String, Object> body) {
        requireUser();
        TenantEnterprise enterprise = enterpriseRepo.findByEnterpriseId(enterpriseId)
                .orElseThrow(() -> new ServiceHandleException(404, "enterprise not found", "企业不存在"));
        Object alias = body.get("enterprise_alias");
        if (alias instanceof String s) enterprise.setEnterpriseAlias(s);
        Object logo = body.get("logo");
        if (logo instanceof String s) enterprise.setLogo(s);
        enterpriseRepo.save(enterprise);
        return GeneralMessage.ok(serialize(enterprise));
    }

    @GetMapping(value = {"/enterprise/{enterprise_id}/teams", "/enterprise/{enterprise_id}/teams/"})
    public ApiResult listTeams(@PathVariable("enterprise_id") String enterpriseId,
                                 @RequestParam(value = "page", required = false) Integer page,
                                 @RequestParam(value = "page_size", required = false) Integer pageSize) {
        requireUser();
        Page<Tenants> teams = tenantsRepo.findByEnterpriseId(enterpriseId,
                pageAdapter.toPageable(page, pageSize));
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("total", teams.getTotalElements());
        return GeneralMessage.okWithExtras(bean,
                teams.getContent().stream().map(this::serializeTeam).toList(), null);
    }

    @GetMapping(value = {"/enterprise/{enterprise_id}/myteams", "/enterprise/{enterprise_id}/myteams/"})
    public ApiResult myTeams(@PathVariable("enterprise_id") String enterpriseId) {
        Integer userId = requireUser();
        List<PermRelTenant> rels = permRelRepo.findByUserId(userId);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (PermRelTenant rel : rels) {
            tenantsRepo.findById(rel.getTenantId())
                    .filter(t -> enterpriseId.equals(t.getEnterpriseId()))
                    .ifPresent(t -> {
                        Map<String, Object> m = serializeTeam(t);
                        m.put("identity", rel.getIdentity());
                        result.add(m);
                    });
        }
        return GeneralMessage.okList(result);
    }

    private Integer requireUser() {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        return userId;
    }

    private Map<String, Object> serializePublic(TenantEnterprise e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enterprise_id", e.getEnterpriseId());
        m.put("enterprise_alias", e.getEnterpriseAlias());
        m.put("logo", e.getLogo());
        m.put("is_active", e.getIsActive());
        return m;
    }

    private Map<String, Object> serialize(TenantEnterprise e) {
        Map<String, Object> m = serializePublic(e);
        m.put("enterprise_name", e.getEnterpriseName());
        m.put("create_time", e.getCreateTime());
        m.put("enable_team_resource_view", e.getEnableTeamResourceView());
        return m;
    }

    private Map<String, Object> serializeTeam(Tenants t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenant_id", t.getTenantId());
        m.put("team_name", t.getTenantName());
        m.put("team_alias", t.getTenantAlias());
        m.put("namespace", t.getNamespace());
        m.put("enterprise_id", t.getEnterpriseId());
        m.put("creater", t.getCreater());
        return m;
    }
}
