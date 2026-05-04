package cn.kuship.console.modules.misc.webhook.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * In-memory de-duplication for webhook delivery IDs (harden-webhook-hmac).
 *
 * <p>GitHub retries failed webhooks at 60-second intervals up to 3 times. Without de-duplication,
 * a transient downstream failure (region API timeout) can cause the same logical "push to main"
 * to trigger {@code lifecycleOps.upgradeService} multiple times, generating duplicate deployments
 * and rollback noise.
 *
 * <p>This component keeps a 5-minute window of {@code <serviceId>:<deliveryId>} keys via Caffeine,
 * capped at 1024 entries (LRU eviction beyond that). Multi-instance clusters do <strong>not</strong>
 * share state — see {@code add-distributed-webhook-dedup} hardening for a Redis-backed variant.
 */
@Component
public class WebhookDeliveryDeduper {

    private final Cache<String, Boolean> seen = Caffeine.newBuilder()
            .maximumSize(1024)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /**
     * Records the {@code (serviceId, deliveryId)} pair and returns whether the delivery should be
     * accepted (i.e. it is the first time we've seen it within the 5-minute window).
     *
     * <p>If {@code deliveryId} is null or blank (older webhook clients that don't send a delivery
     * header), this method returns {@code true} unconditionally — degrading to "no dedup" rather
     * than blocking legitimate traffic.
     */
    public boolean tryAccept(String serviceId, String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            return true;
        }
        String key = serviceId + ":" + deliveryId;
        synchronized (seen) {
            if (seen.getIfPresent(key) != null) {
                return false;
            }
            seen.put(key, Boolean.TRUE);
        }
        return true;
    }
}
