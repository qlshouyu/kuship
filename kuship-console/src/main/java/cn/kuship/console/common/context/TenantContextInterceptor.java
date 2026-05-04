package cn.kuship.console.common.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * 从 path variable 提取 {@code team_name}/{@code region_name} 写入 {@link RequestContext}。
 *
 * <p>注意路径变量名严格保留 snake_case（与 init change 锁定的 URL 契约一致）。
 *
 * <p>用 {@link ObjectProvider} 注入 RequestContext，避免应用启动时（无活动请求）解析失败。
 */
@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    private final ObjectProvider<RequestContext> requestContextProvider;

    public TenantContextInterceptor(ObjectProvider<RequestContext> requestContextProvider) {
        this.requestContextProvider = requestContextProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object pathVarsAttr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(pathVarsAttr instanceof Map<?, ?> rawPathVars)) {
            return true;
        }
        Map<String, String> pathVars = (Map<String, String>) rawPathVars;
        if (pathVars.isEmpty()) {
            return true;
        }
        String teamName = pathVars.get("team_name");
        String regionName = pathVars.get("region_name");
        if (teamName == null && regionName == null) {
            return true;
        }
        RequestContext ctx = requestContextProvider.getIfAvailable();
        if (ctx == null) {
            return true;
        }
        if (teamName != null) {
            ctx.setTeamName(teamName);
        }
        if (regionName != null) {
            ctx.setRegionName(regionName);
        }
        return true;
    }
}
