package cn.kuship.console.modules.grayrelease.service;

import cn.kuship.console.infrastructure.region.api.GatewayOperations;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApisixRouteWeightUpdaterTest {

    private final GatewayOperations gateway = new GatewayOperations() {};
    private final ApisixRouteWeightUpdater updater = new ApisixRouteWeightUpdater(gateway);

    @Test
    void buildBody_zero_ratio_two_backends() {
        Map<String, Object> body = updater.buildBody("orig-svc", "gray-svc", 80, 0, 42, "tn-x", Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> backends = (List<Map<String, Object>>) body.get("backends");
        assertEquals(2, backends.size());
        assertEquals(100, backends.get(0).get("weight"));
        assertEquals(0, backends.get(1).get("weight"));
        assertEquals("orig-svc", backends.get(0).get("service_name"));
        assertEquals("gray-svc", backends.get(1).get("service_name"));
    }

    @Test
    void buildBody_fifty_ratio_split() {
        Map<String, Object> body = updater.buildBody("orig", "gray", 8080, 50, 1, "tn", Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> backends = (List<Map<String, Object>>) body.get("backends");
        assertEquals(50, backends.get(0).get("weight"));
        assertEquals(50, backends.get(1).get("weight"));
        assertEquals(8080, backends.get(0).get("service_port"));
    }

    @Test
    void buildBody_passes_through_plugins_and_authentication() {
        Map<String, Object> domain = Map.of(
                "name", "route-a",
                "hosts", List.of("api.example.com"),
                "plugins", List.of(Map.of("name", "rate-limit", "config", Map.of("rate", 10))),
                "authentication", Map.of("type", "jwt", "secret", "x"),
                "websocket", true);
        Map<String, Object> body = updater.buildBody("o", "g", 80, 30, 7, "tn", domain);
        assertEquals("route-a", body.get("name"));
        assertNotNull(body.get("plugins"));
        assertEquals(domain.get("plugins"), body.get("plugins"));
        assertEquals(domain.get("authentication"), body.get("authentication"));
        assertEquals(true, body.get("websocket"));
    }

    @Test
    void update_rejects_out_of_range_ratio() {
        assertThrows(IllegalArgumentException.class,
                () -> updater.update("r", "ent", "tn", 1, "alias", 80, "o", "g", 150, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> updater.update("r", "ent", "tn", 1, "alias", 80, "o", "g", -1, Map.of()));
    }
}
