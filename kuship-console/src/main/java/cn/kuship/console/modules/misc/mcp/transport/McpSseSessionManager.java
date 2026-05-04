package cn.kuship.console.modules.misc.mcp.transport;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

@Component
public class McpSseSessionManager {

    private static final Logger log = LoggerFactory.getLogger(McpSseSessionManager.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private final Cache<String, McpSseSession> sessions;

    public McpSseSessionManager(@Value("${kuship.mcp.max-sessions:1024}") int maxSessions,
                                    @Value("${kuship.mcp.session-ttl-minutes:30}") long ttlMinutes) {
        this.sessions = Caffeine.newBuilder()
                .maximumSize(maxSessions)
                .expireAfterAccess(Duration.ofMinutes(ttlMinutes))
                .removalListener((String key, McpSseSession value, RemovalCause cause) -> {
                    if (value == null) return;
                    if (cause == RemovalCause.EXPIRED || cause == RemovalCause.SIZE) {
                        log.info("[MCP] session evicted sid={} cause={}", key, cause);
                        try { value.emitter().complete(); } catch (RuntimeException ignored) {}
                    }
                })
                .build();
    }

    public McpSseSession register(Integer userId, String nickName, String email,
                                       String enterpriseId, boolean sysAdmin, SseEmitter emitter) {
        String sid = generateSessionId();
        McpSseSession session = new McpSseSession(sid, userId, nickName, email, enterpriseId, sysAdmin, emitter);
        sessions.put(sid, session);
        return session;
    }

    public Optional<McpSseSession> get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        McpSseSession s = sessions.getIfPresent(sessionId);
        if (s != null) s.touch();
        return Optional.ofNullable(s);
    }

    public void remove(String sessionId) {
        if (sessionId == null) return;
        sessions.invalidate(sessionId);
    }

    public long size() {
        sessions.cleanUp();
        return sessions.estimatedSize();
    }

    private static String generateSessionId() {
        char[] buf = new char[32];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = BASE32_ALPHABET[RANDOM.nextInt(BASE32_ALPHABET.length)];
        }
        return new String(buf);
    }
}
