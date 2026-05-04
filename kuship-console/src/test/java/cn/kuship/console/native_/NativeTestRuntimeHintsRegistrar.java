package cn.kuship.console.native_;

import jakarta.persistence.Entity;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Test-only RuntimeHints contributor for the {@code native-test} Maven profile.
 *
 * <p>This registrar widens the production hint set ({@link cn.kuship.console.config.native_.KuShipConsoleRuntimeHints})
 * with reflection metadata that is needed only when running JUnit 5 tests inside a native image:
 *
 * <ul>
 *   <li>All {@code @RestController} / {@code @Controller} beans — Spring's @MockitoBean override mechanism
 *       inspects controller types via reflection at test bootstrap.</li>
 *   <li>All {@code DTO} classes under {@code cn.kuship.console.modules.**.dto.**} — Jackson serializes test
 *       request/response bodies and needs full member access.</li>
 *   <li>{@link cn.kuship.console.common.response} types ({@code ApiResult}, {@code GeneralMessage}) — written
 *       to MockMvc response body during integration tests.</li>
 *   <li>{@code RuntimeHintsRegistrar} itself + Mockito's {@code MockMethodAdvice} class so the test process
 *       can hot-load Mockito's bytecode advice.</li>
 * </ul>
 *
 * <p>This class is packaged under {@code src/test/java}, so it is <strong>never</strong> shipped in the
 * production native binary; it is only registered for the {@code native-test} profile through the
 * {@code META-INF/spring/aot.factories} entry on the test classpath.
 */
public class NativeTestRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

    static final String MODULES_BASE_PACKAGE = "cn.kuship.console.modules";
    static final String CONSOLE_ROOT_PACKAGE = "cn.kuship.console";
    static final String COMMON_RESPONSE_PACKAGE = "cn.kuship.console.common.response";
    static final Pattern DTO_CLASS_REGEX = Pattern.compile(".*\\.dto(\\..*)?\\..+");
    static final String[] MOCKITO_INTERNAL_CLASSES = {
            "org.mockito.internal.creation.bytebuddy.MockMethodAdvice",
            "org.mockito.internal.creation.bytebuddy.MockMethodInterceptor",
            "org.mockito.internal.creation.bytebuddy.ByteBuddyMockMaker",
            "org.mockito.internal.creation.bytebuddy.SubclassByteBuddyMockMaker"
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        Set<String> registered = new TreeSet<>();
        registerControllerHints(hints, classLoader, registered);
        registerDtoHints(hints, classLoader, registered);
        registerCommonResponseHints(hints, classLoader, registered);
        registerEntityHintsForTest(hints, classLoader, registered);
        registerMockitoInternalHints(hints, classLoader);
        // expose count via system property so NativeTestRuntimeHintsRegistrarTest can assert on it
        System.setProperty("kuship.native.test.hints.count", String.valueOf(registered.size()));
    }

    private void registerControllerHints(RuntimeHints hints, ClassLoader cl, Set<String> sink) {
        // Scan the whole cn.kuship.console tree to pick up controllers that live outside
        // the modules.** subtree (e.g. healthz.HealthzController, contract.* test controllers).
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        scanner.findCandidateComponents(CONSOLE_ROOT_PACKAGE).forEach(bd -> {
            String name = bd.getBeanClassName();
            if (name == null) return;
            try {
                hints.reflection().registerType(Class.forName(name, false, cl), MemberCategory.values());
                sink.add(name);
            } catch (ClassNotFoundException ignored) {
                // Compile-time stub or unloadable class; safe to skip in test bootstrap.
            }
        });
    }

    private void registerDtoHints(RuntimeHints hints, ClassLoader cl, Set<String> sink) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(DTO_CLASS_REGEX));
        scanner.findCandidateComponents(MODULES_BASE_PACKAGE).forEach(bd -> {
            String name = bd.getBeanClassName();
            if (name == null) return;
            try {
                hints.reflection().registerType(Class.forName(name, false, cl), MemberCategory.values());
                sink.add(name);
            } catch (ClassNotFoundException ignored) {
            }
        });
    }

    private void registerCommonResponseHints(RuntimeHints hints, ClassLoader cl, Set<String> sink) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        // accept any concrete class under common.response (bean override needs full member access)
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        scanner.findCandidateComponents(COMMON_RESPONSE_PACKAGE).forEach(bd -> {
            String name = bd.getBeanClassName();
            if (name == null) return;
            try {
                hints.reflection().registerType(Class.forName(name, false, cl), MemberCategory.values());
                sink.add(name);
            } catch (ClassNotFoundException ignored) {
            }
        });
    }

    private void registerEntityHintsForTest(RuntimeHints hints, ClassLoader cl, Set<String> sink) {
        // Production registrar already covers entities, but @DataJpaTest / @SpringBootTest hits
        // DECLARED_FIELDS reflection paths during transactional rollback. Re-register defensively.
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.findCandidateComponents(MODULES_BASE_PACKAGE).forEach(bd -> {
            String name = bd.getBeanClassName();
            if (name == null) return;
            try {
                hints.reflection().registerType(Class.forName(name, false, cl), MemberCategory.values());
                sink.add(name);
            } catch (ClassNotFoundException ignored) {
            }
        });
    }

    private void registerMockitoInternalHints(RuntimeHints hints, ClassLoader cl) {
        // Register by FQCN string via TypeReference. We deliberately do NOT call Class.forName here:
        // Mockito's bytecode-advice classes statically reference MockMethodDispatcher which is shipped
        // in a separate JVM agent jar that is only present when the agent attaches at runtime. Loading
        // the class would trigger linking and NoClassDefFoundError; native-image hint registration only
        // needs the class name, not a loaded class object.
        for (String fqcn : MOCKITO_INTERNAL_CLASSES) {
            hints.reflection().registerType(
                    TypeReference.of(fqcn),
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }
    }
}
