package cn.kuship.console.modules.misc.webhook.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeliveryDeduperTest {

    private final WebhookDeliveryDeduper deduper = new WebhookDeliveryDeduper();

    @Test
    void first_delivery_accepted_second_rejected() {
        assertThat(deduper.tryAccept("svc-1", "delivery-abc")).isTrue();
        assertThat(deduper.tryAccept("svc-1", "delivery-abc")).isFalse();
    }

    @Test
    void same_delivery_id_different_service_both_accepted() {
        assertThat(deduper.tryAccept("svc-a", "delivery-abc")).isTrue();
        assertThat(deduper.tryAccept("svc-b", "delivery-abc")).isTrue();
    }

    @Test
    void null_or_blank_delivery_id_always_accepted() {
        assertThat(deduper.tryAccept("svc-1", null)).isTrue();
        assertThat(deduper.tryAccept("svc-1", "")).isTrue();
        assertThat(deduper.tryAccept("svc-1", "   ")).isTrue();
        // Even repeated null deliveries are accepted (degrades to "no dedup" cleanly).
        assertThat(deduper.tryAccept("svc-1", null)).isTrue();
    }
}
