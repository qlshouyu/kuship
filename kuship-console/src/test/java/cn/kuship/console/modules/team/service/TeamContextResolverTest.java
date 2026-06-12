package cn.kuship.console.modules.team.service;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamContextResolverTest {

    private final TenantsRepository tenantsRepo = mock(TenantsRepository.class);

    private RequestContext ctxWithUser(String eid) {
        RequestContext ctx = new RequestContext();
        UserInfo u = new UserInfo();
        u.setUserId(1);
        u.setEnterpriseId(eid);
        ctx.setCurrentUser(u);
        return ctx;
    }

    @Test
    void resolves_team_under_user_enterprise_and_injects_context() {
        RequestContext ctx = ctxWithUser("ent-1");
        Tenants t = new Tenants();
        t.setTenantName("default");
        t.setEnterpriseId("ent-1");
        when(tenantsRepo.findByTenantNameAndEnterpriseId("default", "ent-1")).thenReturn(Optional.of(t));

        Tenants out = new TeamContextResolver(ctx, tenantsRepo).requireTeam("default");

        assertThat(out).isSameAs(t);
        assertThat(ctx.getTeam()).isSameAs(t);
        assertThat(ctx.getTeamName()).isEqualTo("default");
    }

    @Test
    void team_not_found_raises_team_not_found() {
        RequestContext ctx = ctxWithUser("ent-1");
        when(tenantsRepo.findByTenantNameAndEnterpriseId("ghost", "ent-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new TeamContextResolver(ctx, tenantsRepo).requireTeam("ghost"))
                .isInstanceOf(ServiceHandleException.class)
                .satisfies(e -> {
                    ServiceHandleException ex = (ServiceHandleException) e;
                    assertThat(ex.getMsg()).isEqualTo("team not found");
                    assertThat(ex.getMsgShow()).isEqualTo("团队不存在");
                });
    }
}
