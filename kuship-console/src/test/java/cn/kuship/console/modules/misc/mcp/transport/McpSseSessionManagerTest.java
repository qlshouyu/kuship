package cn.kuship.console.modules.misc.mcp.transport;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

class McpSseSessionManagerTest {

    @Test
    void register_get_remove_roundtrip() {
        McpSseSessionManager mgr = new McpSseSessionManager(16, 30L);
        SseEmitter emitter = new SseEmitter();
        McpSseSession s = mgr.register(42, "alice", "alice@k.local", "ent-1", true, emitter);
        assertNotNull(s.sessionId());
        assertEquals(32, s.sessionId().length());
        assertTrue(mgr.get(s.sessionId()).isPresent());
        mgr.remove(s.sessionId());
        assertTrue(mgr.get(s.sessionId()).isEmpty());
    }

    @Test
    void unknown_session_id_returns_empty() {
        McpSseSessionManager mgr = new McpSseSessionManager(16, 30L);
        assertTrue(mgr.get("ghost").isEmpty());
        assertTrue(mgr.get(null).isEmpty());
        assertTrue(mgr.get("").isEmpty());
    }

    @Test
    void session_ids_are_unique() {
        McpSseSessionManager mgr = new McpSseSessionManager(1024, 30L);
        var ids = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) {
            McpSseSession s = mgr.register(i, "u", null, "ent", false, new SseEmitter());
            assertTrue(ids.add(s.sessionId()), "duplicate id at iter " + i);
        }
    }
}
