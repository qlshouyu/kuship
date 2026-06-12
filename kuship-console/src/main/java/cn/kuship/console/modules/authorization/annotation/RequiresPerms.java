package cn.kuship.console.modules.authorization.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注受保护接口处理方法所需的整型权限码（对齐 rainbond 路由的 {@code __message[method].perms}）。
 * 一个 {@code @GetMapping/@PostMapping/...} 方法对应单一 HTTP 方法，故注解直接承载该方法的所需码。
 * {@code codes} 为空表示对已认证用户放行（不做码校验）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPerms {

    /** 作用域：决定用户权限码按企业级还是团队级计算。 */
    PermScope kind();

    /** 该接口该方法所需的整型权限码；空数组 = 放行。 */
    int[] codes() default {};
}
