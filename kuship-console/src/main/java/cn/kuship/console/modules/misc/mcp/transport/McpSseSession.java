package cn.kuship.console.modules.misc.mcp.transport;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public class McpSseSession {

    private final String sessionId;
    private final Integer userId;
    private final String nickName;
    private final String email;
    private final String enterpriseId;
    private final boolean sysAdmin;
    private final Instant createdAt;
    private final SseEmitter emitter;
    private final AtomicReference<Instant> lastAccess;

    public McpSseSession(String sessionId, Integer userId, String nickName, String email,
                            String enterpriseId, boolean sysAdmin, SseEmitter emitter) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.nickName = nickName;
        this.email = email;
        this.enterpriseId = enterpriseId;
        this.sysAdmin = sysAdmin;
        this.emitter = emitter;
        this.createdAt = Instant.now();
        this.lastAccess = new AtomicReference<>(this.createdAt);
    }

    public String sessionId() { return sessionId; }
    public Integer userId() { return userId; }
    public String nickName() { return nickName; }
    public String email() { return email; }
    public String enterpriseId() { return enterpriseId; }
    public boolean sysAdmin() { return sysAdmin; }
    public Instant createdAt() { return createdAt; }
    public Instant lastAccess() { return lastAccess.get(); }
    public SseEmitter emitter() { return emitter; }

    public void touch() {
        lastAccess.set(Instant.now());
    }
}
