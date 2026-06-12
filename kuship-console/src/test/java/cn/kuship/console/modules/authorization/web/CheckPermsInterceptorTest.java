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
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** check_perms 拦截器：有权放行 / 无权抛 NoPermissions / 无注解放行 / 空所需码放行。 */
class CheckPermsInterceptorTest {

    private final RequestContext ctx = new RequestContext();
    private final AuthorizationService authz = mock(AuthorizationService.class);
    private final TeamContextResolver teamResolver = mock(TeamContextResolver.class);
    private final CheckPermsInterceptor interceptor = new CheckPermsInterceptor(ctx, authz, teamResolver);

    private final HttpServletRequest req = mock(HttpServletRequest.class);
    private final HttpServletResponse resp = mock(HttpServletResponse.class);

    // ---- 测试用的受保护/未保护处理方法 ----
    static class Sample {
        @RequiresPerms(kind = PermScope.TEAM, codes = {200001})
        public void teamGuarded() {
        }

        @RequiresPerms(kind = PermScope.TEAM, codes = {})
        public void teamOpen() {
        }

        public void unannotated() {
        }
    }

    private HandlerMethod handler(String name) throws NoSuchMethodException {
        Method m = Sample.class.getMethod(name);
        return new HandlerMethod(new Sample(), m);
    }

    private UserInfo user() {
        UserInfo u = new UserInfo();
        u.setUserId(700002);
        u.setEnterpriseId("e1");
        ctx.setCurrentUser(u);
        ctx.setTeamName("default");
        return u;
    }

    private Tenants team(int creater) {
        Tenants t = new Tenants();
        t.setTenantId("t-uuid");
        t.setCreater(creater);
        return t;
    }

    @Test
    void non_handler_method_passes() {
        assertThat(interceptor.preHandle(req, resp, new Object())).isTrue();
    }

    @Test
    void unannotated_passes() throws Exception {
        user();
        assertThat(interceptor.preHandle(req, resp, handler("unannotated"))).isTrue();
    }

    @Test
    void empty_required_codes_passes() throws Exception {
        user();
        assertThat(interceptor.preHandle(req, resp, handler("teamOpen"))).isTrue();
    }

    @Test
    void team_guarded_passes_when_has_perm() throws Exception {
        UserInfo u = user();
        when(teamResolver.requireTeam("default")).thenReturn(team(700002));
        when(authz.teamPermCodes(anyString(), any(), anyBoolean())).thenReturn(Set.of(200001));
        when(authz.hasPerms(Set.of(200001), java.util.List.of(200001))).thenReturn(true);
        assertThat(interceptor.preHandle(req, resp, handler("teamGuarded"))).isTrue();
    }

    @Test
    void team_guarded_throws_when_lacking_perm() throws Exception {
        user();
        when(teamResolver.requireTeam("default")).thenReturn(team(999)); // 非 owner
        when(authz.teamPermCodes(anyString(), any(), anyBoolean())).thenReturn(Set.of(120000));
        when(authz.hasPerms(Set.of(120000), java.util.List.of(200001))).thenReturn(false);
        assertThatThrownBy(() -> interceptor.preHandle(req, resp, handler("teamGuarded")))
                .isInstanceOf(NoPermissionsException.class);
    }
}
