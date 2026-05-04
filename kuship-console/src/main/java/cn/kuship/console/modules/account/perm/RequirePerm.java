package cn.kuship.console.modules.account.perm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 controller 方法所需的团队级权限码（由 {@code PermAspect} 拦截校验）。
 *
 * <p>多个权限码 = OR 关系（任一通过即放行）。需要 AND 时拆成多个独立方法或用业务判断。
 *
 * <p>校验逻辑：从 {@code RequestContext.tenantName}（path variable {@code team_name}）+
 * {@code RequestContext.userId} 查 {@code user_role + role_perms} → 是否拥有指定权限码。
 * {@code RequestContext.sysAdmin=true} 直接放行。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {
    String[] value();
}
