package cn.kuship.console.modules.team.service;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.springframework.stereotype.Service;

/**
 * 团队上下文解析（对齐 rainbond TenantHeaderView.initial）：
 * 按 {team_name} + 当前用户 enterprise_id 解析 tenant_info；查不到 → "团队不存在"。
 * 本轮仅校验"团队属于当前用户企业"，不做逐用户成员/权限码强校验（P1-b）。
 */
@Service
public class TeamContextResolver {

    private final RequestContext requestContext;
    private final TenantsRepository tenantsRepository;

    public TeamContextResolver(RequestContext requestContext, TenantsRepository tenantsRepository) {
        this.requestContext = requestContext;
        this.tenantsRepository = tenantsRepository;
    }

    /** 解析并注入团队上下文；不存在抛 ServiceHandleException（HTTP code 以 7070 实测校准）。 */
    public Tenants requireTeam(String teamName) {
        String eid = requestContext.getCurrentUser().getEnterpriseId();
        Tenants team = tenantsRepository.findByTenantNameAndEnterpriseId(teamName, eid)
                .orElseThrow(() -> ServiceHandleException.notFound("team not found", "团队不存在"));
        requestContext.setTeam(team);
        return team;
    }
}
