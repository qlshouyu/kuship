package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.entity.TenantServicesPort;
import cn.kuship.console.modules.app.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.app.repository.TenantServiceRelationRepository;
import cn.kuship.console.modules.app.repository.TenantServicesPortRepository;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 可依赖但未依赖：须 inner 端口、排除已依赖、condition 非法→400。 */
class ComponentUndependencyServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final TenantsRepository tenantsRepo = mock(TenantsRepository.class);
    private final TenantServiceRelationRepository relRepo = mock(TenantServiceRelationRepository.class);
    private final TenantServicesPortRepository portRepo = mock(TenantServicesPortRepository.class);
    private final ServiceGroupRelationRepository grRepo = mock(ServiceGroupRelationRepository.class);
    private final ServiceGroupRepository gRepo = mock(ServiceGroupRepository.class);
    private final ComponentUndependencyService service =
            new ComponentUndependencyService(svcRepo, tenantsRepo, relRepo, portRepo, grRepo, gRepo);

    private TenantServiceInfo svc(String id, String alias, String cname) {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn(id);
        when(s.getServiceAlias()).thenReturn(alias);
        when(s.getServiceCname()).thenReturn(cname);
        when(s.getServiceRegion()).thenReturn("rainbond");
        when(s.getServiceType()).thenReturn("application");
        return s;
    }

    private TenantServicesPort innerPort(boolean inner) {
        TenantServicesPort p = mock(TenantServicesPort.class);
        when(p.isInnerService()).thenReturn(inner);
        return p;
    }

    private void wire(TenantServiceInfo self, List<TenantServiceInfo> region) {
        when(svcRepo.findByServiceAlias("self")).thenReturn(Optional.of(self));
        Tenants t = mock(Tenants.class);
        when(t.getTenantId()).thenReturn("tid");
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
        when(relRepo.findByTenantIdAndServiceId("tid", self.getServiceId())).thenReturn(List.of());
        when(grRepo.findByServiceId(anyString())).thenReturn(Optional.empty());
        when(svcRepo.findByTenantIdAndServiceRegion("tid", "rainbond")).thenReturn(region);
    }

    @Test
    void only_inner_port_candidates() {
        TenantServiceInfo self = svc("S", "self", "Self");
        TenantServiceInfo withInner = svc("A", "a", "WithInner");
        TenantServiceInfo noInner = svc("B", "b", "NoInner");
        wire(self, List.of(self, withInner, noInner));
        TenantServicesPort pA = innerPort(true);
        TenantServicesPort pB = innerPort(false);
        when(portRepo.findByTenantIdAndServiceIdOrderById("tid", "A")).thenReturn(List.of(pA));
        when(portRepo.findByTenantIdAndServiceIdOrderById("tid", "B")).thenReturn(List.of(pB));
        ComponentUndependencyService.Result r = service.list("default", "self", 1, 25, null, null);
        assertThat(r.list()).hasSize(1);
        assertThat(r.list().get(0)).containsEntry("service_id", "A");
    }

    @Test
    void invalid_condition_400_when_candidate_exists() {
        TenantServiceInfo self = svc("S", "self", "Self");
        TenantServiceInfo a = svc("A", "a", "X");
        wire(self, List.of(self, a));
        TenantServicesPort pA = innerPort(true);
        when(portRepo.findByTenantIdAndServiceIdOrderById("tid", "A")).thenReturn(List.of(pA));
        assertThatThrownBy(() -> service.list("default", "self", 1, 25, "k", "bad"))
                .isInstanceOf(ServiceHandleException.class);
    }

    @Test
    void single_component_empty() {
        TenantServiceInfo self = svc("S", "self", "Self");
        wire(self, List.of(self));
        assertThat(service.list("default", "self", 1, 25, null, null).list()).isEmpty();
    }
}
