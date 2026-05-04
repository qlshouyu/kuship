package cn.kuship.console.config.native_;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * 关联 GraalVM Native Image hint 注册器到 Spring 上下文。
 *
 * <p>在 fat jar 启动模式下此 @Configuration 仍然加载（无副作用，hint 仅在 AOT/native build 时生效）。
 */
@Configuration
@ImportRuntimeHints(KuShipConsoleRuntimeHints.class)
public class NativeConfig {
}
