package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import org.springframework.stereotype.Service;

/**
 * 企业上下文解析（对齐 rainbond JWTAuthApiView：self.enterprise = 当前用户 enterprise_id 对应企业）。
 * 本轮单企业模型：enterprise 作用域路由的 {enterprise_id} 必须等于当前用户的 enterprise_id。
 */
@Service
public class EnterpriseContextResolver {

    private final RequestContext requestContext;
    private final TenantEnterpriseRepository enterpriseRepository;

    public EnterpriseContextResolver(RequestContext requestContext,
                                     TenantEnterpriseRepository enterpriseRepository) {
        this.requestContext = requestContext;
        this.enterpriseRepository = enterpriseRepository;
    }

    /** 解析当前登录用户所属企业并注入上下文。 */
    public TenantEnterprise resolveForCurrentUser() {
        String eid = requestContext.getCurrentUser().getEnterpriseId();
        TenantEnterprise enterprise = enterpriseRepository.findByEnterpriseId(eid).orElse(null);
        requestContext.setEnterprise(enterprise);
        return enterprise;
    }

    /**
     * 校验并解析 enterprise 作用域路由的 {enterprise_id}。HTTP 状态码/文案以 7070 实测校准。
     */
    public TenantEnterprise requireEnterprise(String pathEnterpriseId) {
        String userEid = requestContext.getCurrentUser().getEnterpriseId();
        if (userEid == null || !userEid.equals(pathEnterpriseId)) {
            throw new ServiceHandleException(403, "no permission for enterprise", "无权访问该企业");
        }
        TenantEnterprise enterprise = enterpriseRepository.findByEnterpriseId(pathEnterpriseId)
                .orElseThrow(() -> ServiceHandleException.notFound("enterprise not found", "企业不存在"));
        requestContext.setEnterprise(enterprise);
        return enterprise;
    }
}
