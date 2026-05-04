package cn.kuship.console.native_;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity check that the test-scope native hints registrar discovers the expected number of types.
 *
 * <p>The 100-class floor is a coarse "we wired the scanner correctly" gate, not a strict count gate.
 * As of harden-native-tests landing the codebase has roughly 103 controllers + 65 DTOs + 58 entities
 * + a few common.response classes, so the floor leaves headroom while still catching scanner config
 * regressions (wrong base package, wrong filter, classloader misconfig).
 */
class NativeTestRuntimeHintsRegistrarTest {

    @Test
    void registers_at_least_100_types_for_native_test_profile() {
        RuntimeHints hints = new RuntimeHints();

        new NativeTestRuntimeHintsRegistrar().registerHints(hints, getClass().getClassLoader());

        long count = hints.reflection().typeHints().count();
        // print to stdout for harden-native-tests baseline tracking — the value is recorded in
        // kuship-console/CLAUDE.md "Native Test 运行指南" section as the floor against scope drift.
        System.out.println("[NATIVE-TEST-HINTS] type count = " + count);
        assertThat(count)
                .as("Test runtime hints should cover controllers + DTOs + common.response + entities")
                .isGreaterThanOrEqualTo(100);
    }

    @Test
    void registers_known_controller_type() {
        RuntimeHints hints = new RuntimeHints();

        new NativeTestRuntimeHintsRegistrar().registerHints(hints, getClass().getClassLoader());

        // HealthzController is the longest-living controller in the codebase; if scanner ever fails
        // to register it, the test loudly tells us the include filter regressed.
        boolean found = hints.reflection().typeHints()
                .anyMatch(th -> th.getType()
                        .equals(TypeReference.of("cn.kuship.console.healthz.HealthzController")));
        assertThat(found).as("HealthzController must be registered").isTrue();
    }

    @Test
    void registers_common_response_types() {
        RuntimeHints hints = new RuntimeHints();

        new NativeTestRuntimeHintsRegistrar().registerHints(hints, getClass().getClassLoader());

        boolean apiResultRegistered = hints.reflection().typeHints()
                .anyMatch(th -> th.getType()
                        .equals(TypeReference.of("cn.kuship.console.common.response.ApiResult")));
        assertThat(apiResultRegistered).as("ApiResult must be registered").isTrue();
    }
}
