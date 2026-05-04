package cn.kuship.console.modules.misc.sms.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmsVerifyFailureLimiterTest {

    @Test
    void locks_after_max_failures() {
        SmsVerifyFailureLimiter limiter = new SmsVerifyFailureLimiter(5, 300);
        for (int i = 1; i <= 5; i++) {
            assertThat(limiter.isLocked("13800000005", "login")).isFalse();
            limiter.recordFailure("13800000005", "login");
        }
        assertThat(limiter.isLocked("13800000005", "login")).isTrue();
    }

    @Test
    void different_purposes_separate_counters() {
        SmsVerifyFailureLimiter limiter = new SmsVerifyFailureLimiter(5, 300);
        for (int i = 0; i < 5; i++) limiter.recordFailure("13800000006", "login");
        assertThat(limiter.isLocked("13800000006", "login")).isTrue();
        assertThat(limiter.isLocked("13800000006", "register")).isFalse();
    }

    @Test
    void reset_clears_lock() {
        SmsVerifyFailureLimiter limiter = new SmsVerifyFailureLimiter(5, 300);
        for (int i = 0; i < 5; i++) limiter.recordFailure("13800000007", "login");
        assertThat(limiter.isLocked("13800000007", "login")).isTrue();
        limiter.reset("13800000007", "login");
        assertThat(limiter.isLocked("13800000007", "login")).isFalse();
    }
}
