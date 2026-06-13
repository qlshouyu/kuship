package cn.kuship.console.modules.app.service;

import cn.kuship.console.modules.app.entity.ServiceProbe;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.ServiceProbeRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 组件探针：有探针 to_dict、无探针 null、mode 路由。 */
class ComponentProbeServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final ServiceProbeRepository probeRepo = mock(ServiceProbeRepository.class);
    private final ComponentProbeService service = new ComponentProbeService(svcRepo, probeRepo);

    private TenantServiceInfo svc(String source) {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn("sid");
        when(s.getServiceSource()).thenReturn(source);
        return s;
    }

    @Test
    void with_mode_returns_to_dict() {
        TenantServiceInfo s = svc("docker_image");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        ServiceProbe p = mock(ServiceProbe.class);
        Map<String, Object> d = Map.of("mode", "readiness", "is_used", true);
        when(p.toDict()).thenReturn(d);
        when(probeRepo.findFirstByServiceIdAndMode("sid", "readiness")).thenReturn(Optional.of(p));
        assertThat(service.getProbe("a", "readiness")).isEqualTo(d);
    }

    @Test
    void no_probe_returns_null() {
        TenantServiceInfo s = svc("docker_image");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        when(probeRepo.findFirstByServiceIdAndMode("sid", "readiness")).thenReturn(Optional.empty());
        assertThat(service.getProbe("a", "readiness")).isNull();
    }

    @Test
    void third_party_ignores_mode_uses_first() {
        TenantServiceInfo s = svc("third_party");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        ServiceProbe p = mock(ServiceProbe.class);
        when(p.toDict()).thenReturn(Map.of("mode", "liveness"));
        when(probeRepo.findFirstByServiceId("sid")).thenReturn(Optional.of(p));
        assertThat(service.getProbe("a", "readiness")).containsEntry("mode", "liveness");
    }
}
