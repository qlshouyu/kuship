package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnterpriseContextResolverTest {

    private final TenantEnterpriseRepository entRepo = mock(TenantEnterpriseRepository.class);

    private RequestContext ctxWithUser(String eid) {
        RequestContext ctx = new RequestContext();
        UserInfo u = new UserInfo();
        u.setUserId(1);
        u.setEnterpriseId(eid);
        ctx.setCurrentUser(u);
        return ctx;
    }

    @Test
    void require_enterprise_matching_user_resolves_and_injects() {
        RequestContext ctx = ctxWithUser("ent-1");
        TenantEnterprise e = new TenantEnterprise();
        e.setEnterpriseId("ent-1");
        when(entRepo.findByEnterpriseId("ent-1")).thenReturn(Optional.of(e));

        TenantEnterprise out = new EnterpriseContextResolver(ctx, entRepo).requireEnterprise("ent-1");

        assertThat(out).isSameAs(e);
        assertThat(ctx.getEnterprise()).isSameAs(e);
    }

    @Test
    void require_enterprise_mismatch_is_rejected_403() {
        RequestContext ctx = ctxWithUser("ent-1");
        assertThatThrownBy(() -> new EnterpriseContextResolver(ctx, entRepo).requireEnterprise("other-ent"))
                .isInstanceOf(ServiceHandleException.class)
                .satisfies(e -> assertThat(((ServiceHandleException) e).getStatus()).isEqualTo(403));
    }
}
