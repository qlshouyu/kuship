package cn.kuship.console.modules.misc.sms.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default SMS provider used by dev / local / contract-test profiles. Prints the code to stdout
 * instead of dispatching a real SMS so the rest of the stack (verification flow, rate limiter,
 * failure limiter) can be exercised without aliyun credentials.
 */
@Component
@ConditionalOnProperty(name = "kuship.sms.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

    @Override
    public SmsResult send(String phone, String code, String purpose) {
        log.info("[SMS-MVP] phone={} purpose={} code={} (dev only — no real SMS sent)",
                phone, purpose, code);
        return SmsResult.ok("logging-" + UUID.randomUUID());
    }

    @Override
    public String identifier() {
        return "logging";
    }
}
