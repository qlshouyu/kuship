package cn.kuship.console.modules.app.service;

import cn.kuship.console.modules.app.entity.Labels;
import cn.kuship.console.modules.app.entity.NodeLabels;
import cn.kuship.console.modules.app.entity.ServiceLabels;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.LabelsRepository;
import cn.kuship.console.modules.app.repository.NodeLabelsRepository;
import cn.kuship.console.modules.app.repository.ServiceLabelsRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 组件标签：空态、used/unused 装配 + node 排除已用。 */
class ComponentLabelsServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final RegionConfigRepository regionRepo = mock(RegionConfigRepository.class);
    private final ServiceLabelsRepository slRepo = mock(ServiceLabelsRepository.class);
    private final NodeLabelsRepository nlRepo = mock(NodeLabelsRepository.class);
    private final LabelsRepository lRepo = mock(LabelsRepository.class);
    private final ComponentLabelsService service =
            new ComponentLabelsService(svcRepo, regionRepo, slRepo, nlRepo, lRepo);

    private TenantServiceInfo svc() {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn("sid");
        when(s.getServiceRegion()).thenReturn("rainbond");
        return s;
    }

    @Test
    void empty_when_no_labels() {
        TenantServiceInfo s = svc();
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        when(slRepo.findByServiceId("sid")).thenReturn(List.of());
        when(regionRepo.findByRegionName("rainbond")).thenReturn(Optional.empty());
        Map<String, Object> bean = service.getLabels("a", "rainbond");
        assertThat((List<?>) bean.get("used_labels")).isEmpty();
        assertThat((List<?>) bean.get("unused_labels")).isEmpty();
        assertThat(bean.keySet()).containsExactly("used_labels", "unused_labels");
    }

    @Test
    @SuppressWarnings("unchecked")
    void node_labels_exclude_used() {
        TenantServiceInfo s = svc();
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        ServiceLabels sl = mock(ServiceLabels.class);
        when(sl.getLabelId()).thenReturn("L1");
        when(slRepo.findByServiceId("sid")).thenReturn(List.of(sl));
        RegionConfig rc = mock(RegionConfig.class);
        when(rc.getRegionId()).thenReturn("rid");
        when(regionRepo.findByRegionName("rainbond")).thenReturn(Optional.of(rc));
        NodeLabels n1 = mock(NodeLabels.class); when(n1.getLabelId()).thenReturn("L1"); // 已用→排除
        NodeLabels n2 = mock(NodeLabels.class); when(n2.getLabelId()).thenReturn("L2");
        when(nlRepo.findByRegionId("rid")).thenReturn(List.of(n1, n2));
        Labels used = mock(Labels.class); when(used.toDict()).thenReturn(Map.of("label_id", "L1"));
        Labels unused = mock(Labels.class); when(unused.toDict()).thenReturn(Map.of("label_id", "L2"));
        when(lRepo.findByLabelIdInOrderById(List.of("L1"))).thenReturn(List.of(used));
        when(lRepo.findByLabelIdInOrderById(List.of("L2"))).thenReturn(List.of(unused));

        Map<String, Object> bean = service.getLabels("a", "rainbond");
        assertThat((List<Map<String, Object>>) bean.get("used_labels")).extracting(m -> m.get("label_id")).containsExactly("L1");
        assertThat((List<Map<String, Object>>) bean.get("unused_labels")).extracting(m -> m.get("label_id")).containsExactly("L2");
    }
}
