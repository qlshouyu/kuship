package cn.kuship.console.modules.openapi.docs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Removes Springdoc's QueryDSL operation customizer before Spring tries to instantiate it.
 *
 * <p>Springdoc 2.x ships a {@code QuerydslPredicateOperationCustomizer} (registered as the
 * {@code queryDslQuerydslPredicateOperationCustomizer} bean by
 * {@code SpringDocConfiguration$QuerydslProvider}) that statically references
 * {@code org.springframework.data.util.TypeInformation}. Spring Data 4 (shipped with Spring
 * Boot 4) relocated that class, so any attempt to introspect the customizer's methods raises
 * {@code NoClassDefFoundError} during bean post-processing.
 *
 * <p>kuship-console does not expose any {@code QuerydslPredicate} parameters from
 * {@code /openapi/v1/**}, so the customizer is dead code. Removing the bean definition (rather
 * than excluding the entire {@code SpringDocConfiguration} auto-config) keeps every other
 * Springdoc bean functional.
 *
 * <p>The class is a {@link BeanDefinitionRegistryPostProcessor} (not a plain
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor}) so it runs
 * during the registry-population phase, before any bean post-processing kicks in.
 *
 * <p>It is also a top-level {@code @Component} so the bean is registered unconditionally —
 * gating it behind {@code kuship.openapi.docs.enabled} would let the broken bean load when the
 * docs flag is off but Springdoc auto-config still runs.
 *
 * <p>Once Springdoc publishes a Spring Boot 4 / Spring Data 4 compatible release, delete this
 * file and replace with a regular {@code spring.autoconfigure.exclude} entry.
 */
@Component
public class SpringdocQuerydslIncompatibilityShim implements BeanDefinitionRegistryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SpringdocQuerydslIncompatibilityShim.class);
    private static final String BROKEN_BEAN_NAME = "queryDslQuerydslPredicateOperationCustomizer";

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!registry.containsBeanDefinition(BROKEN_BEAN_NAME)) {
            return;
        }
        BeanDefinition def = registry.getBeanDefinition(BROKEN_BEAN_NAME);
        String factoryBeanName = def.getFactoryBeanName();
        if (factoryBeanName == null || factoryBeanName.contains("SpringDocConfiguration")
                || factoryBeanName.contains("QuerydslProvider")) {
            registry.removeBeanDefinition(BROKEN_BEAN_NAME);
            log.info("Removed Springdoc QueryDSL operation customizer bean "
                    + "(Spring Boot 4 / Spring Data 4 incompatibility shim).");
        }
    }
}
