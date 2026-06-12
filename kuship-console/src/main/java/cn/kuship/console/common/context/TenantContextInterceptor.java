package cn.kuship.console.common.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * 从 URL 路径变量 {@code {team_name}} / {@code {region_name}} 注入 {@link RequestContext}。
 * 保留 snake_case 变量名，与 rainbond-console 路由一致。
 */
@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    private final RequestContext requestContext;

    public TenantContextInterceptor(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attr instanceof Map<?, ?> vars) {
            Map<String, String> pathVars = (Map<String, String>) vars;
            String teamName = pathVars.get("team_name");
            if (teamName != null) {
                requestContext.setTeamName(teamName);
            }
            String regionName = pathVars.get("region_name");
            if (regionName != null) {
                requestContext.setRegionName(regionName);
            }
        }
        return true;
    }
}
