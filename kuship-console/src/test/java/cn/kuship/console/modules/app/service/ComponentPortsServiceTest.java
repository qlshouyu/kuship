package cn.kuship.console.modules.app.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.modules.app.entity.TenantServiceEnvVar;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.entity.TenantServicesPort;
import cn.kuship.console.modules.app.repository.TenantServiceEnvVarRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.app.repository.TenantServicesPortRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 组件端口：environment/inner_url、关闭 TCP 端口网关空→is_outer false、字段顺序。 */
class ComponentPortsServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final TenantsRepository tenantsRepo = mock(TenantsRepository.class);
    private final TenantServicesPortRepository portRepo = mock(TenantServicesPortRepository.class);
    private final TenantServiceEnvVarRepository envRepo = mock(TenantServiceEnvVarRepository.class);
    private final RegionConfigRepository regionRepo = mock(RegionConfigRepository.class);
    private final RegionClient client = mock(RegionClient.class);
    private final ComponentPortsService service = new ComponentPortsService(
            svcRepo, tenantsRepo, portRepo, envRepo, regionRepo, client);

    private TenantServiceEnvVar env(String name, String attrName, String value) {
        TenantServiceEnvVar e = mock(TenantServiceEnvVar.class);
        when(e.getName()).thenReturn(name);
        when(e.getAttrName()).thenReturn(attrName);
        when(e.getAttrValue()).thenReturn(value);
        return e;
    }

    @Test
    @SuppressWarnings("unchecked")
    void inner_tcp_port_closed_outer() {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getTenantId()).thenReturn("tid");
        when(s.getServiceId()).thenReturn("sid");
        when(s.getServiceRegion()).thenReturn("rainbond");
        when(s.getServiceAlias()).thenReturn("grc8ca26");
        when(svcRepo.findByServiceAlias("grc8ca26")).thenReturn(Optional.of(s));
        Tenants t = mock(Tenants.class);
        when(t.getTenantName()).thenReturn("default");
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
        RegionConfig rc = mock(RegionConfig.class);
        when(regionRepo.findByRegionName("rainbond")).thenReturn(Optional.of(rc));

        TenantServicesPort port = mock(TenantServicesPort.class);
        java.util.Map<String, Object> pd = new java.util.LinkedHashMap<>();
        pd.put("ID", 1); pd.put("container_port", 5000); pd.put("is_outer_service", false);
        when(port.toDict()).thenReturn(pd);
        when(port.isInnerService()).thenReturn(true);
        when(port.isOuterService()).thenReturn(false);
        when(port.getProtocol()).thenReturn("tcp");
        when(port.getContainerPort()).thenReturn(5000);
        when(portRepo.findByTenantIdAndServiceIdOrderById("tid", "sid")).thenReturn(List.of(port));

        TenantServiceEnvVar host = env("连接地址", "PARITYPORT_HOST", "grc8ca26");
        TenantServiceEnvVar pport = env("端口", "PARITYPORT_PORT", "5000");
        when(envRepo.findByTenantIdAndServiceIdAndContainerPortOrderById("tid", "sid", 5000))
                .thenReturn(List.of(host, pport));
        // 网关 tcp domains 返回空
        when(client.exchange(any(), any(), any(), any(), any(), any())).thenReturn("{\"list\":[]}");

        List<Map<String, Object>> r = service.listPorts("default", "grc8ca26", "rainbond");
        assertThat(r).hasSize(1);
        Map<String, Object> p = r.get(0);
        assertThat(p.get("service_alias")).isEqualTo("grc8ca26");
        List<Map<String, Object>> envv = (List<Map<String, Object>>) p.get("environment");
        assertThat(envv).hasSize(2);
        assertThat(envv.get(0)).containsEntry("desc", "连接地址").containsEntry("name", "PARITYPORT_HOST").containsEntry("value", "grc8ca26");
        assertThat(p.get("inner_url")).isEqualTo("grc8ca26:5000");
        assertThat(p.get("outer_url")).isEqualTo("");
        assertThat((List<?>) p.get("bind_domains")).isEmpty();
        assertThat((List<?>) p.get("bind_tcp_domains")).isEmpty();
        assertThat(p.get("is_outer_service")).isEqualTo(false);
    }

    @Test
    void no_ports_empty_list() {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getTenantId()).thenReturn("tid");
        when(s.getServiceId()).thenReturn("sid");
        when(s.getServiceRegion()).thenReturn("rainbond");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        Tenants t = mock(Tenants.class);
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
        when(regionRepo.findByRegionName("rainbond")).thenReturn(Optional.empty());
        when(portRepo.findByTenantIdAndServiceIdOrderById("tid", "sid")).thenReturn(List.of());
        assertThat(service.listPorts("default", "a", "rainbond")).isEmpty();
    }
}
