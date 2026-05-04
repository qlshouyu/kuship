package cn.kuship.console.infrastructure.region.response;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher;
import cn.kuship.console.infrastructure.region.exception.ClusterLackOfMemoryException;
import cn.kuship.console.infrastructure.region.exception.InvalidLicenseException;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.exception.RegionApiFrequentException;
import cn.kuship.console.infrastructure.region.exception.TenantLackOfMemoryException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionApiResponseProcessorTest {

    private final ObjectMapper json = JsonMapper.builder().build();
    private final RegionProperties props = new RegionProperties(5, false, 0,
            List.of("操作过于频繁，请稍后再试", "wait a moment please", "just wait a moment"));
    private final RegionErrorMsgEnricher enricher = new RegionErrorMsgEnricher();
    private final RegionApiResponseProcessor processor = new RegionApiResponseProcessor(json, props, enricher);

    record Bean(String name, int value) {}

    @Test
    void success200_extractsBean() {
        String body = "{\"code\":200,\"msg\":\"success\",\"msg_show\":\"OK\","
                + "\"data\":{\"bean\":{\"name\":\"alice\",\"value\":42}}}";
        Bean b = processor.extractBean(ResponseEntity.ok(body), Bean.class, "test", "/u", "GET");
        assertEquals("alice", b.name());
        assertEquals(42, b.value());
    }

    @Test
    void success200_emptyBody_throwsRegionApiException() {
        var resp = ResponseEntity.ok("");
        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> processor.extractBean(resp, Bean.class, "test", "/u", "GET"));
        assertEquals("集群请求网络异常", ex.getMsgShow());
    }

    @Test
    void error500_withCode_throwsRegionApiException() {
        String body = "{\"code\":1001,\"msg\":\"team not found\",\"msg_show\":\"团队不存在\"}";
        var resp = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> processor.extractBean(resp, Bean.class, "test", "/u", "GET"));
        assertEquals(1001, ex.getCode());
        assertEquals("团队不存在", ex.getMsgShow());
    }

    @Test
    void http401_withBeanCode10400_throwsInvalidLicense() {
        String body = "{\"data\":{\"bean\":{\"code\":10400,\"msg\":\"license expired\"}}}";
        var resp = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        assertThrows(InvalidLicenseException.class,
                () -> processor.extractBean(resp, Bean.class, "test", "/u", "GET"));
    }

    @Test
    void http409_frequentMessage_throwsRegionApiFrequent() {
        String body = "{\"code\":409,\"msg\":\"操作过于频繁，请稍后再试\"}";
        var resp = ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        assertThrows(RegionApiFrequentException.class,
                () -> processor.extractBean(resp, Bean.class, "test", "/u", "GET"));
    }

    @Test
    void http409_nonFrequentMessage_throwsGenericRegionApiException() {
        String body = "{\"code\":409,\"msg\":\"tenant already exists\",\"msg_show\":\"团队已存在\"}";
        var resp = ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> processor.extractBean(resp, Bean.class, "test", "/u", "GET"));
        assertEquals(409, ex.getCode());
        assertEquals("团队已存在", ex.getMsgShow());
    }

    @Test
    void http412_clusterLackOfMemory_throwsSpecific() {
        String body = "{\"msg\":\"cluster_lack_of_memory\"}";
        var resp = ResponseEntity.status(412).body(body);
        assertThrows(ClusterLackOfMemoryException.class,
                () -> processor.extractBean(resp, Bean.class, "test", "/u", "GET"));
    }

    @Test
    void http412_tenantLackOfMemory_throwsSpecific() {
        String body = "{\"msg\":\"tenant_lack_of_memory\"}";
        var resp = ResponseEntity.status(412).body(body);
        assertThrows(TenantLackOfMemoryException.class,
                () -> processor.extractBean(resp, Bean.class, "test", "/u", "GET"));
    }

    @Test
    void http500_msgEnrichedByEnricher_helmConflict() {
        String body = "{\"code\":500,\"msg\":\"ConfigMap \\\"x\\\" in namespace \\\"ns\\\" "
                + "exists and cannot be imported into the current release: invalid ownership metadata; "
                + "key \\\"meta.helm.sh/release-name\\\": must be set to \\\"r\\\"; "
                + "key \\\"meta.helm.sh/release-namespace\\\": must be set to \\\"ns\\\"\"}";
        var resp = ResponseEntity.status(500).body(body);
        RegionApiException ex = assertThrows(RegionApiException.class,
                () -> processor.extractBean(resp, Bean.class, "test", "/u", "GET"));
        assertTrue(ex.getMsgShow().contains("命名空间 ns"), "msg_show 应被汉化");
    }
}
