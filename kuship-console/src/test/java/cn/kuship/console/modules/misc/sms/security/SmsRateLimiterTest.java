package cn.kuship.console.modules.misc.sms.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmsRateLimiterTest {

    @Test
    void disabled_always_allows() {
        SmsRateLimiter limiter = new SmsRateLimiter(false, 60);
        assertThat(limiter.tryAcquire("13800000001")).isTrue();
        assertThat(limiter.tryAcquire("13800000001")).isTrue();
        assertThat(limiter.tryAcquire("13800000001")).isTrue();
    }

    @Test
    void enabled_blocks_second_send_within_window() {
        SmsRateLimiter limiter = new SmsRateLimiter(true, 60);
        assertThat(limiter.tryAcquire("13800000002")).isTrue();
        assertThat(limiter.tryAcquire("13800000002")).isFalse();
    }

    @Test
    void different_phones_independent() {
        SmsRateLimiter limiter = new SmsRateLimiter(true, 60);
        assertThat(limiter.tryAcquire("13800000003")).isTrue();
        assertThat(limiter.tryAcquire("13800000004")).isTrue();
    }

    @Test
    void blank_phone_always_allows() {
        SmsRateLimiter limiter = new SmsRateLimiter(true, 60);
        assertThat(limiter.tryAcquire(null)).isTrue();
        assertThat(limiter.tryAcquire("")).isTrue();
    }
}
