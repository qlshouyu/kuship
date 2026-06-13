package cn.kuship.console.modules.app.service;

import cn.kuship.console.modules.app.entity.ComponentGraph;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.ComponentGraphRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 组件监控图：to_dict 列表、空。 */
class ComponentGraphServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final ComponentGraphRepository graphRepo = mock(ComponentGraphRepository.class);
    private final ComponentGraphService service = new ComponentGraphService(svcRepo, graphRepo);

    private TenantServiceInfo svc() {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn("sid");
        return s;
    }

    @Test
    void lists_to_dict() {
        TenantServiceInfo s = svc();
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        ComponentGraph g = mock(ComponentGraph.class);
        when(g.toDict()).thenReturn(Map.of("title", "Mem", "sequence", 0));
        when(graphRepo.findByComponentIdOrderBySequence("sid")).thenReturn(List.of(g));
        assertThat(service.listGraphs("a")).hasSize(1).first().isEqualTo(Map.of("title", "Mem", "sequence", 0));
    }

    @Test
    void empty() {
        TenantServiceInfo s = svc();
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        when(graphRepo.findByComponentIdOrderBySequence("sid")).thenReturn(List.of());
        assertThat(service.listGraphs("a")).isEmpty();
    }
}
