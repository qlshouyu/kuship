package cn.kuship.console.modules.region.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 集群命名空间：解析 body.list；region_id→region_name。 */
class RegionNamespaceServiceTest {

    private final RegionConfigRepository repo = mock(RegionConfigRepository.class);
    private final RegionClient client = mock(RegionClient.class);
    private final RegionNamespaceService service = new RegionNamespaceService(repo, client);

    @Test
    @SuppressWarnings("unchecked")
    void parses_list_from_body() {
        RegionConfig rc = mock(RegionConfig.class);
        when(rc.getRegionName()).thenReturn("rainbond");
        when(repo.findByRegionId("rid")).thenReturn(Optional.of(rc));
        when(client.exchange(eq("rainbond"), eq("GET"), any(), any(), any(), any()))
                .thenReturn("{\"list\":[\"default\",\"rbd-system\"]}");
        Object bean = service.listNamespaces("e1", "rid", "all");
        assertThat((List<Object>) bean).containsExactly("default", "rbd-system");
    }

    @Test
    @SuppressWarnings("unchecked")
    void empty_list_when_null() {
        RegionConfig rc = mock(RegionConfig.class);
        when(rc.getRegionName()).thenReturn("rainbond");
        when(repo.findByRegionId("rid")).thenReturn(Optional.of(rc));
        when(client.exchange(any(), any(), any(), any(), any(), any())).thenReturn("{}");
        assertThat((List<Object>) service.listNamespaces("e1", "rid", "all")).isEmpty();
    }
}
