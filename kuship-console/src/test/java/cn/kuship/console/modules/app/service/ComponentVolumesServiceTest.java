package cn.kuship.console.modules.app.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.entity.TenantServiceVolume;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.app.repository.TenantServiceVolumeRepository;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 组件持久化：未部署→not_bound 纯 DB(不调 region)、first=true、空。 */
class ComponentVolumesServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final TenantsRepository tenantsRepo = mock(TenantsRepository.class);
    private final TenantRegionInfoRepository trRepo = mock(TenantRegionInfoRepository.class);
    private final TenantServiceVolumeRepository volRepo = mock(TenantServiceVolumeRepository.class);
    private final RegionClient client = mock(RegionClient.class);
    private final ComponentVolumesService service =
            new ComponentVolumesService(svcRepo, tenantsRepo, trRepo, volRepo, client);

    private TenantServiceInfo svc(String createStatus) {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn("sid");
        when(s.getServiceRegion()).thenReturn("rainbond");
        when(s.getCreateStatus()).thenReturn(createStatus);
        return s;
    }

    @Test
    @SuppressWarnings("unchecked")
    void uncomplete_not_bound_no_region() {
        TenantServiceInfo s = svc("creating");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        Tenants t = mock(Tenants.class);
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
        TenantServiceVolume v = mock(TenantServiceVolume.class);
        when(v.getVolumeName()).thenReturn("pdata");
        java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
        d.put("ID", 1); d.put("volume_name", "pdata"); d.put("allow_expansion", false); d.put("mode", null);
        when(v.toDict()).thenReturn(d);
        when(volRepo.findByServiceIdAndVolumeTypeNotOrderById("sid", "config-file")).thenReturn(List.of(v));

        List<Map<String, Object>> r = service.listVolumes("default", "a", "rainbond");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).get("status")).isEqualTo("not_bound");
        assertThat(r.get(0).get("dep_services")).isNull();
        assertThat(r.get(0).get("first")).isEqualTo(true);
        verifyNoInteractions(client); // 未部署不调 region
    }

    @Test
    void empty_when_no_volumes() {
        TenantServiceInfo s = svc("creating");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        Tenants t = mock(Tenants.class);
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
        when(volRepo.findByServiceIdAndVolumeTypeNotOrderById("sid", "config-file")).thenReturn(List.of());
        assertThat(service.listVolumes("default", "a", "rainbond")).isEmpty();
    }
}
