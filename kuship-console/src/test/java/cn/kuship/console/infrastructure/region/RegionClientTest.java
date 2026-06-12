package cn.kuship.console.infrastructure.region;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** RegionClient 骨架：认证优先级与 region 解析。 */
class RegionClientTest {

    private final RegionConfigRepository repo = mock(RegionConfigRepository.class);
    private final RegionClient client = new RegionClient(repo, new RegionProperties());

    private RegionEndpoint endpoint(String token) {
        return new RegionEndpoint("rainbond", "https://r:8443", token, null, null, null);
    }

    @Test
    void enterprise_token_takes_priority_over_region_token() {
        assertThat(client.chooseAuthToken("ent-token", endpoint("region-token"))).isEqualTo("ent-token");
    }

    @Test
    void falls_back_to_region_token_when_no_enterprise_token() {
        assertThat(client.chooseAuthToken(null, endpoint("region-token"))).isEqualTo("region-token");
        assertThat(client.chooseAuthToken("  ", endpoint("region-token"))).isEqualTo("region-token");
    }

    @Test
    void returns_null_when_no_token_available() {
        assertThat(client.chooseAuthToken(null, endpoint(""))).isNull();
    }

    @Test
    void resolve_unknown_region_throws_not_found() {
        when(repo.findByRegionName("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> client.resolveEndpoint("ghost"))
                .isInstanceOf(ServiceHandleException.class)
                .satisfies(e -> assertThat(((ServiceHandleException) e).getStatus()).isEqualTo(404));
    }
}
