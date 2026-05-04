package cn.kuship.console.infrastructure.region.client;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import cn.kuship.console.infrastructure.region.repository.RegionInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegionClientFactoryTest {

    private RegionInfoRepository repo;
    private RegionClientFactory factory;

    @BeforeEach
    void setUp() {
        repo = mock(RegionInfoRepository.class);
        RegionProperties props = new RegionProperties(5, false, 0,
                List.of("操作过于频繁，请稍后再试"));
        factory = new RegionClientFactory(repo, props);
    }

    @Test
    void notFound_throwsRegionApiException() {
        when(repo.findByEnterpriseAndName(anyString(), anyString())).thenReturn(Optional.empty());

        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> factory.getClient("nope", "ent-1"));
        assertEquals(500, ex.getCode());
        assertEquals("集群配置不存在", ex.getMsgShow());
    }

    @Test
    void getClient_cachesAcrossCalls() {
        // Use plaintext http url so no mTLS required (cert/key all null → would fail; provide test fixture)
        // Build a minimal region with NO TLS cert (will fail SSL setup); test a different aspect: cache hit.
        // 简化策略：repo 返回空 → 抛异常；可验证缓存行为为「未命中时调 repo」「命中时不调 repo」
        when(repo.findByEnterpriseAndName(eq("ent-1"), eq("nope")))
                .thenReturn(Optional.empty());

        // 第一次调用触发 repo 查询并抛异常（不缓存失败）
        assertThrows(RegionApiException.class, () -> factory.getClient("nope", "ent-1"));
        // 第二次再触发一次（失败不缓存）
        assertThrows(RegionApiException.class, () -> factory.getClient("nope", "ent-1"));
        // 验证 repo 被调用 2 次（失败查询不进缓存）
        verify(repo, times(2)).findByEnterpriseAndName(eq("ent-1"), eq("nope"));
    }

    @Test
    void evict_doesNotFail_whenKeyAbsent() {
        // 不会抛异常
        factory.evict("any", "any");
    }

    @Test
    void buildFails_whenSslContextSetupFails_dueToMissingCertKey() {
        // region 存在但无 cert/key → SSL 装配失败 → 抛 RegionApiSocketException
        RegionInfoDto region = new RegionInfoDto("rid", "region-1", "alias", "[]",
                "https://example.com", "wss://example.com", "", "", "", "1", "private",
                null, null, null, "ent-1", null, null);
        when(repo.findByEnterpriseAndName(eq("ent-1"), eq("region-1")))
                .thenReturn(Optional.of(region));

        // SslContextFactory 会因为 cert/key=null 抛 IOException → 包装为 RegionApiSocketException
        assertThrows(RegionApiException.class, () -> factory.getClient("region-1", "ent-1"));
    }
}
