package cn.kuship.console.infrastructure.region.api;

import org.springframework.stereotype.Component;

/**
 * {@link ResourceCenterOperations} 空占位实现。
 *
 * <p>仅作为 Spring bean 占位，保证注入不报 NoSuchBeanDefinitionException。
 * 真实实现由 {@code ResourceCenterOperationsImpl}（@Primary）覆盖。
 */
@Component
public class ResourceCenterOperationsDefaultImpl implements ResourceCenterOperations {
    // 全部 method 继承 interface default（抛 UnsupportedOperationException）
}
