package cn.kuship.console.modules.region;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.service.RegionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RegionService.parseToken 单测：YAML 解析 + 错误顺序对齐 rainbond `parse_token`。
 */
class RegionParseTokenTest {

    private final RegionService svc = new RegionService(null, null, null);

    private static final String GOOD_TOKEN = """
            ca.pem: |
              -----BEGIN CERTIFICATE-----
              CA-CONTENT
              -----END CERTIFICATE-----
            client.pem: |
              -----BEGIN CERTIFICATE-----
              CLIENT-CERT
              -----END CERTIFICATE-----
            client.key.pem: |
              -----BEGIN PRIVATE KEY-----
              CLIENT-KEY
              -----END PRIVATE KEY-----
            apiAddress: https://172.20.0.5:6443
            websocketAddress: wss://172.20.0.5:6060
            defaultDomainSuffix: gr-test.local
            defaultTCPHost: 172.20.0.5
            """;

    @Test
    void parseToken_valid() {
        RegionInfo r = svc.parseToken(GOOD_TOKEN, "r1", "测试集群", List.of("public"));
        assertEquals("r1", r.getRegionName());
        assertEquals("测试集群", r.getRegionAlias());
        assertEquals("[\"public\"]", r.getRegionType());
        assertEquals("https://172.20.0.5:6443", r.getUrl());
        assertEquals("wss://172.20.0.5:6060", r.getWsurl());
        assertEquals("gr-test.local", r.getHttpdomain());
        assertEquals("172.20.0.5", r.getTcpdomain());
        assertNotNull(r.getSslCaCert());
        assertNotNull(r.getCertFile());
        assertNotNull(r.getKeyFile());
        assertNotNull(r.getRegionId());
        assertEquals(32, r.getRegionId().length());
    }

    @Test
    void parseToken_missingCa_returnsCnError() {
        String bad = GOOD_TOKEN.replace("ca.pem:", "ignored:");
        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> svc.parseToken(bad, "r1", "alias", List.of()));
        assertEquals(400, ex.getCode());
        assertEquals("CA证书不存在", ex.getMsgShow());
    }

    @Test
    void parseToken_missingApiAddress() {
        String bad = GOOD_TOKEN.replace("apiAddress:", "ignored:");
        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> svc.parseToken(bad, "r1", "alias", List.of()));
        assertEquals("API地址不存在", ex.getMsgShow());
    }

    @Test
    void parseToken_invalidYaml() {
        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> svc.parseToken(":\n  bad indent\n: -\n", "r1", "alias", List.of()));
        assertEquals(400, ex.getCode());
        assertEquals("Region Config 内容不是有效YAML格式", ex.getMsgShow());
    }

    @Test
    void parseToken_emptyToken() {
        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> svc.parseToken("", "r1", "alias", List.of()));
        assertEquals("Region Config 内容不是有效YAML格式", ex.getMsgShow());
    }
}
