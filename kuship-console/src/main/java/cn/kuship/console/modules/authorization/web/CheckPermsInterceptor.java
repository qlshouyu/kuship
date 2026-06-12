package cn.kuship.console.modules.authorization.web;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.NoPermissionsException;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.authorization.annotation.PermScope;
import cn.kuship.console.modules.authorization.annotation.RequiresPerms;
import cn.kuship.console.modules.authorization.service.AuthorizationService;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.service.TeamContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 强制鉴权（对齐 rainbond {@code check_perms}）：在认证（P0）与团队上下文解析之后、业务之前，
 * 读取处理方法的 {@link RequiresPerms}，按作用域计算用户权限码，与所需码求交，缺则抛
 * {@link NoPermissionsException}（403/10402）。无注解或所需码为空则放行。
 */
@Component
public class CheckPermsInterceptor implements HandlerInterceptor {

    private final RequestContext requestContext;
    private final AuthorizationService authorizationService;
    private final TeamContextResolver teamContextResolver;

    public CheckPermsInterceptor(RequestContext requestContext,
                                 AuthorizationService authorizationService,
                                 TeamContextResolver teamContextResolver) {
        this.requestContext = requestContext;
        this.authorizationService = authorizationService;
        this.teamContextResolver = teamContextResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresPerms required = method.getMethodAnnotation(RequiresPerms.class);
        if (required == null) {
            return true; // 未标注 → 放行
        }
        List<Integer> requiredCodes = Arrays.stream(required.codes()).boxed().toList();
        if (requiredCodes.isEmpty()) {
            return true; // 空所需码 → 放行
        }
        UserInfo user = requestContext.getCurrentUser();
        if (user == null) {
            return true; // 理论上 P0 已拦截未认证；防御性放行交安全层处理
        }

        Set<Integer> userCodes;
        if (required.kind() == PermScope.TEAM) {
            // 团队不存在则在此抛 "团队不存在"（与 rainbond initial 先解析团队、再 check_perms 的次序一致）
            Tenants team = teamContextResolver.requireTeam(requestContext.getTeamName());
            boolean isOwner = team.getCreater() != null && team.getCreater().equals(user.getUserId());
            userCodes = authorizationService.teamPermCodes(team.getTenantId(), user, isOwner);
        } else {
            userCodes = authorizationService.enterprisePermCodes(user);
        }

        if (!authorizationService.hasPerms(userCodes, requiredCodes)) {
            throw new NoPermissionsException();
        }
        return true;
    }
}
