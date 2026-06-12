package cn.kuship.console.common.context;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 路径变量 {team_name}/{region_name} → RequestContext 注入。 */
class TenantContextInterceptorTest {

    @Test
    void injects_team_and_region_from_path_variables() {
        RequestContext ctx = new RequestContext();
        TenantContextInterceptor interceptor = new TenantContextInterceptor(ctx);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("team_name", "team-a", "region_name", "rainbond"));

        boolean proceed = interceptor.preHandle(request, null, new Object());

        assertThat(proceed).isTrue();
        assertThat(ctx.getTeamName()).isEqualTo("team-a");
        assertThat(ctx.getRegionName()).isEqualTo("rainbond");
    }

    @Test
    void no_path_variables_leaves_context_empty() {
        RequestContext ctx = new RequestContext();
        TenantContextInterceptor interceptor = new TenantContextInterceptor(ctx);

        interceptor.preHandle(new MockHttpServletRequest(), null, new Object());

        assertThat(ctx.getTeamName()).isNull();
        assertThat(ctx.getRegionName()).isNull();
    }
}
