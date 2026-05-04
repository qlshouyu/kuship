package cn.kuship.console.modules.account.perm;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 权限注解切面：拦截 {@link RequirePerm} 与 {@link RequireEnterpriseAdmin} 标注的方法。
 *
 * <p>无权限抛 {@link ServiceHandleException}（code=403），由 {@code GlobalExceptionHandler}
 * 包装为 general_message 形状响应。
 *
 * <p>{@code RequestContext.sysAdmin=true} 直接放行（与 rainbond {@code is_sys_admin} 行为一致）。
 */
@Aspect
@Component
public class PermAspect {

    private final RequestContext requestContext;
    private final PermService permService;

    public PermAspect(RequestContext requestContext, PermService permService) {
        this.requestContext = requestContext;
        this.permService = permService;
    }

    @Before("@annotation(requirePerm)")
    public void checkTeamPerm(RequirePerm requirePerm) {
        if (requestContext.isSysAdmin()) {
            return;
        }
        Integer userId = requestContext.getUserId();
        String tenantName = requestContext.getTeamName();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        if (tenantName == null || tenantName.isBlank()) {
            throw new ServiceHandleException(403, "missing team context for perm check",
                    "您无操作此功能的权限");
        }
        if (!permService.userHasAnyPerm(userId, tenantName, requirePerm.value())) {
            throw new ServiceHandleException(403, "no permission: " + String.join(",", requirePerm.value()),
                    "您无操作此功能的权限");
        }
    }

    @Before("@annotation(requireEnterpriseAdmin)")
    public void checkEnterpriseAdmin(RequireEnterpriseAdmin requireEnterpriseAdmin) {
        if (requestContext.isSysAdmin()) {
            return;
        }
        Integer userId = requestContext.getUserId();
        String enterpriseId = requestContext.getEnterpriseId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        if (enterpriseId == null || enterpriseId.isBlank()
                || !permService.isEnterpriseAdmin(userId, enterpriseId)) {
            throw new ServiceHandleException(403, "not an enterprise admin",
                    "您无操作此功能的权限");
        }
    }
}
