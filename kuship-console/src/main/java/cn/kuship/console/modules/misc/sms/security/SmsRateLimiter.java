package cn.kuship.console.modules.misc.sms.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-phone send rate limiter (add-aliyun-sms).
 *
 * <p>Always wired (no {@code @ConditionalOnProperty}) — the {@code enabled} flag is read at
 * construction time and gates the {@link #tryAcquire(String)} contract. This keeps the consumer
 * code free of {@code @Autowired(required=false)} and lets dev profiles disable limiting via
 * {@code kuship.sms.rate-limit.enabled=false} without reshuffling Spring beans.
 */
@Component
public class SmsRateLimiter {

    private final boolean enabled;
    private final Cache<String, Boolean> sent;

    public SmsRateLimiter(
            @Value("${kuship.sms.rate-limit.enabled:false}") boolean enabled,
            @Value("${kuship.sms.rate-limit.window-seconds:60}") long windowSeconds) {
        this.enabled = enabled;
        this.sent = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .build();
    }

    /**
     * Returns true if the phone is allowed to send right now (and records the send), false if
     * the previous send is still inside the rate-limit window.
     */
    public boolean tryAcquire(String phone) {
        if (!enabled || phone == null || phone.isBlank()) {
            return true;
        }
        synchronized (sent) {
            if (sent.getIfPresent(phone) != null) {
                return false;
            }
            sent.put(phone, Boolean.TRUE);
        }
        return true;
    }
}
