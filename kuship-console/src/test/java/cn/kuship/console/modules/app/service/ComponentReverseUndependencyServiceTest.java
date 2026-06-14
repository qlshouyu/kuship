package cn.kuship.console.modules.app.service;

import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.app.repository.TenantServiceRelationRepository;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 可被依赖但未依赖：排除自己、过滤、分组。 */
class ComponentReverseUndependencyServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final TenantsRepository tenantsRepo = mock(TenantsRepository.class);
    private final TenantServiceRelationRepository relRepo = mock(TenantServiceRelationRepository.class);
    private final ServiceGroupRelationRepository grRepo = mock(ServiceGroupRelationRepository.class);
    private final ServiceGroupRepository gRepo = mock(ServiceGroupRepository.class);
    private final ComponentReverseUndependencyService service =
            new ComponentReverseUndependencyService(svcRepo, tenantsRepo, relRepo, grRepo, gRepo);

    private TenantServiceInfo svc(String id, String alias, String cname) {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn(id);
        when(s.getServiceAlias()).thenReturn(alias);
        when(s.getServiceCname()).thenReturn(cname);
        when(s.getServiceRegion()).thenReturn("rainbond");
        when(s.getServiceType()).thenReturn("application");
        return s;
    }

    private void wireTeam(TenantServiceInfo self, List<TenantServiceInfo> regionServices) {
        when(svcRepo.findByServiceAlias("self")).thenReturn(Optional.of(self));
        Tenants t = mock(Tenants.class);
        when(t.getTenantId()).thenReturn("tid");
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
        when(relRepo.findByTenantIdAndDepServiceId("tid", self.getServiceId())).thenReturn(List.of());
        when(grRepo.findByServiceId(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(svcRepo.findByTenantIdAndServiceRegion("tid", "rainbond")).thenReturn(regionServices);
    }

    @Test
    void excludes_self_returns_others() {
        TenantServiceInfo self = svc("S", "self", "Self");
        TenantServiceInfo other = svc("O", "other", "OtherApp");
        wireTeam(self, List.of(self, other));
        ComponentReverseUndependencyService.Result r = service.list("default", "self", 1, 25, null, null);
        assertThat(r.total()).isEqualTo(1);
        assertThat(r.list()).hasSize(1);
        assertThat(r.list().get(0)).containsEntry("service_id", "O").containsEntry("group_name", "未分组").containsEntry("group_id", -1);
        assertThat(r.list().get(0).keySet()).containsExactly("service_cname","service_id","service_type","service_alias","group_name","group_id");
    }

    @Test
    void search_filters_by_service_name() {
        TenantServiceInfo self = svc("S", "self", "Self");
        TenantServiceInfo a = svc("A", "a", "AppFoo");
        TenantServiceInfo b = svc("B", "b", "AppBar");
        wireTeam(self, List.of(self, a, b));
        ComponentReverseUndependencyService.Result r = service.list("default", "self", 1, 25, "foo", "service_name");
        assertThat(r.list()).hasSize(1);
        assertThat(r.list().get(0)).containsEntry("service_id", "A");
    }

    @Test
    void single_component_empty() {
        TenantServiceInfo self = svc("S", "self", "Self");
        wireTeam(self, List.of(self));
        assertThat(service.list("default", "self", 1, 25, null, null).list()).isEmpty();
    }
}
