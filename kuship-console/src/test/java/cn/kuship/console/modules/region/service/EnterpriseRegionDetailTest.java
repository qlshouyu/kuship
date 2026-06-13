package cn.kuship.console.modules.region.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 单集群详情：open 级 28 字段、字段顺序、region_id 不存在→null。 */
class EnterpriseRegionDetailTest {

    private final RegionConfigRepository repo = mock(RegionConfigRepository.class);
    private final TenantEnterpriseRepository entRepo = mock(TenantEnterpriseRepository.class);
    private final RegionClient client = mock(RegionClient.class);
    private final EnterpriseRegionReadService service =
            new EnterpriseRegionReadService(repo, entRepo, client);

    @Test
    void open_dict_has_open_fields_in_order() {
        RegionConfig r = mock(RegionConfig.class);
        when(r.getRegionId()).thenReturn("rid");
        when(r.getRegionName()).thenReturn("rainbond");
        when(r.getEnterpriseId()).thenReturn("e1");
        when(r.getScope()).thenReturn("private");
        when(r.getWsurl()).thenReturn("ws://x");
        when(r.getHttpdomain()).thenReturn("h.example");
        when(r.getSslCaCert()).thenReturn("CA");
        when(repo.findByRegionId("rid")).thenReturn(Optional.of(r));
        TenantEnterprise e = mock(TenantEnterprise.class);
        when(e.getEnterpriseAlias()).thenReturn("ent");
        when(entRepo.findByEnterpriseId("e1")).thenReturn(Optional.of(e));

        Map<String, Object> bean = service.getRegion("e1", "rid");
        assertThat(bean).containsKeys("wsurl", "httpdomain", "tcpdomain",
                "ssl_ca_cert", "cert_file", "key_file");
        assertThat(bean.get("ssl_ca_cert")).isEqualTo("CA");
        assertThat(bean.get("rbd_version")).isEqualTo("unknown");
        assertThat(bean.get("health_status")).isEqualTo("ok");
        assertThat(bean.get("enterprise_alias")).isEqualTo("ent");
        // open 块在 provider_cluster_id 之后、desc 之前
        List<String> keys = new ArrayList<>(bean.keySet());
        assertThat(keys.indexOf("wsurl")).isEqualTo(keys.indexOf("provider_cluster_id") + 1);
        assertThat(keys.indexOf("key_file")).isEqualTo(keys.indexOf("desc") - 1);
        assertThat(bean).hasSize(28);
    }

    @Test
    void null_when_region_missing() {
        when(repo.findByRegionId("nope")).thenReturn(Optional.empty());
        assertThat(service.getRegion("e1", "nope")).isNull();
    }
}
