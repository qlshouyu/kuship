package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 企业读路径业务：企业列表、企业概览。字段对照 docs/p1a-7070-reference.md。 */
@Service
public class EnterpriseReadService {

    private final RequestContext requestContext;
    private final TenantEnterpriseRepository enterpriseRepository;
    private final TenantsRepository tenantsRepository;
    private final UserInfoRepository userInfoRepository;

    public EnterpriseReadService(RequestContext requestContext,
                                 TenantEnterpriseRepository enterpriseRepository,
                                 TenantsRepository tenantsRepository,
                                 UserInfoRepository userInfoRepository) {
        this.requestContext = requestContext;
        this.enterpriseRepository = enterpriseRepository;
        this.tenantsRepository = tenantsRepository;
        this.userInfoRepository = userInfoRepository;
    }

    /** 当前用户可见企业（单企业模型：其所属企业）。 */
    public List<Map<String, Object>> listEnterprisesForCurrentUser() {
        String eid = requestContext.getCurrentUser().getEnterpriseId();
        List<Map<String, Object>> list = new ArrayList<>();
        enterpriseRepository.findByEnterpriseId(eid).ifPresent(e -> list.add(toEnterpriseMap(e)));
        return list;
    }

    /** 企业概览：shared_apps / total_teams / total_users。 */
    public Map<String, Object> overview(String enterpriseId) {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("shared_apps", 0); // 应用市场共享应用数，P1-a 暂 0（重聚合留后续）
        bean.put("total_teams", tenantsRepository.countByEnterpriseId(enterpriseId));
        bean.put("total_users", userInfoRepository.countByEnterpriseId(enterpriseId));
        return bean;
    }

    private Map<String, Object> toEnterpriseMap(TenantEnterprise e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", e.getId());
        m.put("enterprise_alias", e.getEnterpriseAlias());
        m.put("enterprise_name", e.getEnterpriseName());
        m.put("is_active", e.getIsActive());
        m.put("enterprise_id", e.getEnterpriseId());
        m.put("enterprise_token", e.getEnterpriseToken());
        m.put("create_time", e.getCreateTime());
        m.put("enable_team_resource_view", e.getEnableTeamResourceView());
        return m;
    }
}
