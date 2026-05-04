package cn.kuship.console.infrastructure.region.errormsg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionErrorMsgEnricherTest {

    private final RegionErrorMsgEnricher enricher = new RegionErrorMsgEnricher();

    @Test
    void helmOwnershipConflict_fullCapture_renderedWithDetails() {
        String msg = "ConfigMap \"my-config\" in namespace \"my-ns\" exists and cannot be imported into the current release: invalid ownership metadata; "
                + "label validation error: missing key \"app.kubernetes.io/managed-by\": must be set to \"Helm\"; "
                + "annotation validation error: missing key \"meta.helm.sh/release-name\": must be set to \"my-release\"; "
                + "annotation validation error: missing key \"meta.helm.sh/release-namespace\": must be set to \"my-ns\"";
        String result = enricher.enrich(msg);
        assertTrue(result.contains("命名空间 my-ns"));
        assertTrue(result.contains("ConfigMap/my-config"));
        assertTrue(result.contains("Release my-release"));
    }

    @Test
    void helmOwnershipConflict_keywordsButNotMatch_returnsFallback() {
        // contains both keywords but the regex's structured tail won't match
        String msg = "Some resource exists and cannot be imported into the current release: invalid ownership metadata; (truncated)";
        String result = enricher.enrich(msg);
        assertEquals("命名空间中已存在同名资源，且缺少 Helm 接管元数据，请先删除冲突资源或补齐 Helm 元数据后重试", result);
    }

    @Test
    void domainConflict_fullCapture_renderedWithDetails() {
        String msg = "domain conflict: domain 'a.com' conflicts with existing domain 'b.com' in namespace 'ns' (resource: ingress/foo)";
        String result = enricher.enrich(msg);
        assertTrue(result.contains("a.com"));
        assertTrue(result.contains("b.com"));
        assertTrue(result.contains("ingress/foo"));
        assertTrue(result.contains("命名空间 ns"));
    }

    @Test
    void domainConflict_keywordsButNotMatch_returnsFallback() {
        String msg = "domain conflict: domain 'malformed' (truncated) conflicts with existing domain";
        String result = enricher.enrich(msg);
        assertEquals("域名与现有证书配置冲突，请先清理冲突配置后重试。", result);
    }

    @Test
    void plainEnglishMessage_passThroughUnchanged() {
        String msg = "request timeout";
        assertEquals("request timeout", enricher.enrich(msg));
    }

    @Test
    void blankInput_returnedAsIs() {
        assertEquals(null, enricher.enrich(null));
        assertEquals("", enricher.enrich(""));
    }
}
