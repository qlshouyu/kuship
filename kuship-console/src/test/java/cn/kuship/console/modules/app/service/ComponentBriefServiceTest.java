package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 组件概览：bean = service.toDict()；不存在→404。 */
class ComponentBriefServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final ComponentBriefService service = new ComponentBriefService(svcRepo);

    @Test
    void returns_to_dict() {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        Map<String, Object> d = Map.of("service_id", "sid", "namespace", "goodrain");
        when(s.toDict()).thenReturn(d);
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        assertThat(service.getBrief("a")).isEqualTo(d);
    }

    @Test
    void not_found() {
        when(svcRepo.findByServiceAlias("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getBrief("x")).isInstanceOf(ServiceHandleException.class);
    }
}
