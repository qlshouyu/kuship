package cn.kuship.console.modules.app.service;

import cn.kuship.console.modules.app.entity.ServiceGroup;
import cn.kuship.console.modules.app.entity.ServiceGroupRelation;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 组件详情：to_dict 全列、namespace 覆盖、组名、disk_cap、ws url、is_third。 */
class ComponentDetailServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final TenantsRepository tenantsRepo = mock(TenantsRepository.class);
    private final ServiceGroupRelationRepository relRepo = mock(ServiceGroupRelationRepository.class);
    private final ServiceGroupRepository groupRepo = mock(ServiceGroupRepository.class);
    private final RegionConfigRepository regionRepo = mock(RegionConfigRepository.class);
    private final ComponentDetailService service =
            new ComponentDetailService(svcRepo, tenantsRepo, relRepo, groupRepo, regionRepo);

    private TenantServiceInfo svc(String source, String extend) {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn("sid");
        when(s.getServiceRegion()).thenReturn("rainbond");
        when(s.getServiceSource()).thenReturn(source);
        when(s.getExtendMethod()).thenReturn(extend);
        java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
        d.put("service_id", "sid");
        d.put("namespace", "goodrain"); // DB 值，应被 tenant.namespace 覆盖
        when(s.toDict()).thenReturn(d);
        return s;
    }

    @SuppressWarnings("unchecked")
    @Test
    void builds_detail_with_group_and_overrides() {
        TenantServiceInfo sv = svc("docker_image", "stateless_multiple");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(sv));
        Tenants t = mock(Tenants.class);
        when(t.getNamespace()).thenReturn("default");
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
        ServiceGroupRelation rel = mock(ServiceGroupRelation.class);
        when(rel.getGroupId()).thenReturn(8);
        when(relRepo.findByServiceId("sid")).thenReturn(Optional.of(rel));
        ServiceGroup g = mock(ServiceGroup.class);
        when(g.getGroupName()).thenReturn("parity-app2");
        when(groupRepo.findById(8)).thenReturn(Optional.of(g));
        RegionConfig rc = mock(RegionConfig.class);
        when(rc.getWsurl()).thenReturn("ws://172.20.0.2:6060");
        when(regionRepo.findByRegionName("rainbond")).thenReturn(Optional.of(rc));

        Map<String, Object> bean = service.getDetail("default", "a", "rainbond", "localhost");
        Map<String, Object> sm = (Map<String, Object>) bean.get("service");
        assertThat(sm.get("namespace")).isEqualTo("default");       // 覆盖
        assertThat(sm.get("group_name")).isEqualTo("parity-app2");
        assertThat(sm.get("group_id")).isEqualTo(8);
        assertThat(sm.get("disk_cap")).isEqualTo(10);               // 非 vm
        assertThat(bean.get("event_websocket_url")).isEqualTo("ws://172.20.0.2:6060/event_log");
        assertThat(bean.get("is_third")).isEqualTo(false);
        assertThat(bean.keySet()).containsExactly("service", "event_websocket_url", "is_third");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ungrouped_and_third_party_and_auto_ws() {
        TenantServiceInfo sv = svc("third_party", "stateless_multiple");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(sv));
        Tenants t = mock(Tenants.class);
        when(t.getNamespace()).thenReturn("default");
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
        when(relRepo.findByServiceId("sid")).thenReturn(Optional.empty());
        RegionConfig rc = mock(RegionConfig.class);
        when(rc.getWsurl()).thenReturn("auto");
        when(regionRepo.findByRegionName("rainbond")).thenReturn(Optional.of(rc));

        Map<String, Object> bean = service.getDetail("default", "a", "rainbond", "myhost");
        Map<String, Object> sm = (Map<String, Object>) bean.get("service");
        assertThat(sm.get("group_name")).isEqualTo("未分组");
        assertThat(sm.get("group_id")).isEqualTo(-1);
        assertThat(bean.get("is_third")).isEqualTo(true);
        assertThat(bean.get("event_websocket_url")).isEqualTo("ws://myhost:6060/event_log");
    }
}
