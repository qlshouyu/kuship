package cn.kuship.console.modules.app.service;

import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.entity.TenantServicesPort;
import cn.kuship.console.modules.app.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.app.repository.TenantServiceRelationRepository;
import cn.kuship.console.modules.app.repository.TenantServicesPortRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 组件依赖：空依赖 list=[]、port_list、正向无 service_id / 反向有。 */
class ComponentDependencyServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final TenantServiceRelationRepository relRepo = mock(TenantServiceRelationRepository.class);
    private final TenantServicesPortRepository portRepo = mock(TenantServicesPortRepository.class);
    private final ServiceGroupRelationRepository grRepo = mock(ServiceGroupRelationRepository.class);
    private final ServiceGroupRepository gRepo = mock(ServiceGroupRepository.class);
    private final ComponentDependencyService service =
            new ComponentDependencyService(svcRepo, relRepo, portRepo, grRepo, gRepo);

    private void wire() {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn("sid");
        when(s.getTenantId()).thenReturn("tid");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        TenantServicesPort port = mock(TenantServicesPort.class);
        when(port.getContainerPort()).thenReturn(5000);
        when(portRepo.findByTenantIdAndServiceIdOrderById("tid", "sid")).thenReturn(List.of(port));
        when(grRepo.findByServiceId("sid")).thenReturn(Optional.empty());
    }

    @Test
    void forward_empty_no_service_id() {
        wire();
        when(relRepo.findByTenantIdAndServiceId("tid", "sid")).thenReturn(List.of());
        ComponentDependencyService.Result r = service.forward("a", 1, 25);
        assertThat(r.list()).isEmpty();
        assertThat(r.total()).isZero();
        assertThat(r.bean()).containsEntry("total", 0).containsEntry("port_list", List.of(5000));
        assertThat(r.bean()).doesNotContainKey("service_id");
        assertThat(r.bean().keySet()).containsExactly("port_list", "total");
    }

    @Test
    void reverse_empty_with_service_id() {
        wire();
        when(relRepo.findByTenantIdAndDepServiceId("tid", "sid")).thenReturn(List.of());
        ComponentDependencyService.Result r = service.reverse("a", 1, 25);
        assertThat(r.list()).isEmpty();
        assertThat(r.bean().keySet()).containsExactly("service_id", "port_list", "total");
        assertThat(r.bean().get("service_id")).isEqualTo("sid");
    }
}
