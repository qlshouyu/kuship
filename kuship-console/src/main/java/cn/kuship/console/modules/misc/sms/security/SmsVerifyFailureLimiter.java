package cn.kuship.console.modules.misc.sms.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute-force guard for SMS code verification (add-aliyun-sms).
 *
 * <p>Counts failed verify attempts per (phone, purpose) key inside a 5-minute sliding window.
 * Once the threshold (default 5) is reached, the verify endpoint short-circuits with a 429
 * "verify locked" response without consulting the {@code sms_verification_code} table — keeping
 * the DB cheap under attack.
 */
@Component
public class SmsVerifyFailureLimiter {

    private final int maxFailures;
    private final Cache<String, AtomicInteger> failures;

    public SmsVerifyFailureLimiter(
            @Value("${kuship.sms.verify-failure.max-failures:5}") int maxFailures,
            @Value("${kuship.sms.verify-failure.window-seconds:300}") long windowSeconds) {
        this.maxFailures = maxFailures;
        this.failures = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .build();
    }

    /** True if the (phone, purpose) is currently locked out. */
    public boolean isLocked(String phone, String purpose) {
        AtomicInteger counter = failures.getIfPresent(key(phone, purpose));
        return counter != null && counter.get() >= maxFailures;
    }

    /** Increment the failure counter and return the resulting count. */
    public int recordFailure(String phone, String purpose) {
        AtomicInteger counter = failures.get(key(phone, purpose), k -> new AtomicInteger());
        return counter.incrementAndGet();
    }

    /** Clear the counter, e.g. after a successful verify. */
    public void reset(String phone, String purpose) {
        failures.invalidate(key(phone, purpose));
    }

    private static String key(String phone, String purpose) {
        return phone + ":" + purpose;
    }
}
