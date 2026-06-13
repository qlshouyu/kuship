package cn.kuship.console.modules.region.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CNB 框架：解析 body.list；lang 缺省 nodejs。 */
class RegionCnbServiceTest {

    private final RegionConfigRepository repo = mock(RegionConfigRepository.class);
    private final RegionClient client = mock(RegionClient.class);
    private final RegionCnbService service = new RegionCnbService(repo, client);

    @Test
    void parses_list_and_defaults_lang() {
        RegionConfig rc = mock(RegionConfig.class);
        when(rc.getRegionName()).thenReturn("rainbond");
        when(repo.findByRegionId("rid")).thenReturn(Optional.of(rc));
        when(client.exchange(eq("rainbond"), eq("GET"), any(), any(), any(), any()))
                .thenReturn("{\"list\":[{\"name\":\"nextjs\"},{\"name\":\"nuxt\"}]}");

        List<Object> list = service.showFrameworks("e1", "rid", null);
        assertThat(list).hasSize(2);

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(client).exchange(eq("rainbond"), eq("GET"), path.capture(), any(), any(), any());
        assertThat(path.getValue()).isEqualTo("/v2/cluster/cnb/frameworks?lang=nodejs");
    }

    @Test
    void empty_when_no_list() {
        RegionConfig rc = mock(RegionConfig.class);
        when(rc.getRegionName()).thenReturn("rainbond");
        when(repo.findByRegionId("rid")).thenReturn(Optional.of(rc));
        when(client.exchange(any(), any(), any(), any(), any(), any())).thenReturn("{}");
        assertThat(service.showFrameworks("e1", "rid", "go")).isEmpty();
    }
}
