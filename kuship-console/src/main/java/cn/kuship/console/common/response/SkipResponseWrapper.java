package cn.kuship.console.common.response;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注后{@link GeneralMessageResponseBodyAdvice} 跳过对应的方法或类的自动响应包装，让返回值原样写入 response body。
 *
 * <p>典型用例：SSE / 文件下载 / 代理透传等不应被包成 {@code general_message} 的端点。
 *
 * <p>方法级注解优先于类级注解：方法上若有 {@code @SkipResponseWrapper}，无论类上是否标注，均跳过；
 * 类上若有，未在方法上标注的方法也跳过；都没有则走自动包装。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipResponseWrapper {
}
