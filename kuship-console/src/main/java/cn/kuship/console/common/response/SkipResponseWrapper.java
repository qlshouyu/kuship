package cn.kuship.console.common.response;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在方法或类上，跳过 {@link GeneralMessageResponseBodyAdvice} 的信封自动包装。
 * 用于 SSE、文件下载等不应被包装的端点。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipResponseWrapper {
}
