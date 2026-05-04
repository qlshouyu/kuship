package cn.kuship.console.native_;

/**
 * Helper utilities for tests that need to behave differently under a GraalVM native image.
 *
 * <p>Inspect {@code org.graalvm.nativeimage.imagecode} system property (set to {@code Runtime}
 * by GraalVM at native image bootstrap). When present, callers should prefer
 * {@code webEnvironment=DEFINED_PORT} + {@code server.port=0} over {@code RANDOM_PORT}, because
 * Spring Boot 4's RANDOM_PORT path is still flaky on native (GH-44982).
 *
 * <p>Today no test in the project actually binds a real HTTP port (all use MockMvc), so this is
 * future-proofing scaffolding. New tests that introduce {@code @LocalServerPort} or RestTemplate
 * smoke checks should consult {@link #isNativeImageRuntime()} when wiring SpringBootTest config.
 *
 * <p>Companion property file at {@code application-native-test.yaml} pins {@code server.port=0}
 * so the OS hands out a free port even under DEFINED_PORT. Activate via
 * {@code @TestPropertySource("classpath:application-native-test.yaml")} or
 * {@code @ActiveProfiles({"local","contract-test","native-test"})}.
 */
public final class NativeTestSupport {

    private NativeTestSupport() {
    }

    /**
     * @return true if the JVM is currently running inside a GraalVM native image at runtime.
     */
    public static boolean isNativeImageRuntime() {
        return "Runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
