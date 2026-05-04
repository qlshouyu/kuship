package cn.kuship.console.modules.account.perm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 controller 方法要求 enterprise admin 身份。
 *
 * <p>校验逻辑：从 {@code RequestContext.userId} 与 {@code RequestContext.enterpriseId} 查
 * {@code enterprise_user_perm.identity='admin'}。{@code sysAdmin=true} 直接放行。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireEnterpriseAdmin {
}
