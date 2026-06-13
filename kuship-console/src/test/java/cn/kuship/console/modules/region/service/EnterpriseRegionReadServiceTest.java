package cn.kuship.console.modules.region.service;

import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 企业集群列表：22 字段 safe map、资源默认、region_type 解析、enterprise_alias。 */
class EnterpriseRegionReadServiceTest {

    private final RegionConfigRepository regionRepo = mock(RegionConfigRepository.class);
    private final TenantEnterpriseRepository entRepo = mock(TenantEnterpriseRepository.class);
    private final cn.kuship.console.infrastructure.region.RegionClient regionClient = mock(cn.kuship.console.infrastructure.region.RegionClient.class);
    private final EnterpriseRegionReadService service = new EnterpriseRegionReadService(regionRepo, entRepo, regionClient);

    private static RegionConfig region(String type) {
        RegionConfig r = mock(RegionConfig.class);
        when(r.getRegionId()).thenReturn("rid");
        when(r.getRegionName()).thenReturn("rainbond");
        when(r.getRegionAlias()).thenReturn("默认集群");
        when(r.getStatus()).thenReturn("1");
        when(r.getRegionType()).thenReturn(type);
        when(r.getEnterpriseId()).thenReturn("e1");
        when(r.getUrl()).thenReturn("https://x:8443");
        when(r.getScope()).thenReturn("default");
        when(r.getDesc()).thenReturn("d");
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void safe_dict_fields_and_defaults() {
        RegionConfig rc = region(null);
        when(regionRepo.findByEnterpriseIdOrderById("e1")).thenReturn(List.of(rc));
        TenantEnterprise e = new TenantEnterprise();
        e.setEnterpriseId("e1");
        e.setEnterpriseAlias("KuShip");
        when(entRepo.findByEnterpriseId("e1")).thenReturn(Optional.of(e));

        Map<String, Object> m = service.listRegions("e1", null, null).get(0);
        assertThat(m).containsEntry("region_name", "rainbond").containsEntry("region_alias", "默认集群")
                .containsEntry("scope", "default").containsEntry("enterprise_alias", "KuShip");
        // 资源默认
        assertThat(m).containsEntry("total_memory", 0).containsEntry("rbd_version", "unknown")
                .containsEntry("health_status", "ok").containsEntry("resource_proxy_status", false);
        // region_type null → []
        assertThat((List<Object>) m.get("region_type")).isEmpty();
        // safe 级不含敏感字段
        assertThat(m).doesNotContainKeys("wsurl", "httpdomain", "ssl_ca_cert", "cert_file", "key_file", "token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void region_type_json_parsed() {
        RegionConfig rc = region("[\"x\",\"y\"]");
        when(regionRepo.findByEnterpriseIdOrderById("e1")).thenReturn(List.of(rc));
        when(entRepo.findByEnterpriseId("e1")).thenReturn(Optional.empty());
        Map<String, Object> m = service.listRegions("e1", null, null).get(0);
        assertThat((List<Object>) m.get("region_type")).containsExactly("x", "y");
        assertThat(m).containsEntry("enterprise_alias", null);
    }

    @Test
    void status_filter_uses_status_query() {
        RegionConfig rc = region(null);
        when(regionRepo.findByEnterpriseIdAndStatusOrderById("e1", "1")).thenReturn(List.of(rc));
        when(entRepo.findByEnterpriseId("e1")).thenReturn(Optional.empty());
        assertThat(service.listRegions("e1", "1", null)).hasSize(1);
    }
}
