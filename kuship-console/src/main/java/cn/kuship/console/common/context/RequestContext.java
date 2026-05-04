package cn.kuship.console.common.context;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * 请求级上下文。@RequestScope 保证在 web 请求线程内有效，自动随请求结束销毁。
 *
 * <p>填充时机：
 * <ul>
 *   <li>{@code JwtAuthenticationFilter} 解析合法 token 后通过 {@code UserInfoRepository} 加载真实用户，
 *       写入 {@code userId/username/email/enterpriseId/sysAdmin}</li>
 *   <li>{@code TenantContextInterceptor#preHandle} 从 path variable 提取 {@code team_name}/{@code region_name}</li>
 * </ul>
 *
 * <p>异步任务（@Async / 虚拟线程子任务）必须显式传递这些字段，不能依赖 RequestScope 跨线程透传。
 */
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
public class RequestContext {

    private Integer userId;
    private String username;
    private String email;
    private String teamName;
    private String regionName;
    /** 与 rainbond {@code user_info.enterprise_id} 一致，char(32) UUID。 */
    private String enterpriseId;
    private boolean sysAdmin;
}
