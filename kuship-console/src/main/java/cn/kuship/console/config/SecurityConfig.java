package cn.kuship.console.config;

import cn.kuship.console.common.security.JwtAuthenticationFilter;
import cn.kuship.console.common.security.RestAccessDeniedHandler;
import cn.kuship.console.common.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 无状态 JWT 安全配置。放行登录/健康检查/actuator，其余需认证；
 * 401/403 走统一信封处理器；JwtAuthenticationFilter 在用户名密码过滤器之前执行。
 */
@Configuration
public class SecurityConfig {

    /** 放行的匿名端点（显式完整 /console 路径）。public 供 JwtAuthenticationFilter 跳过校验。 */
    public static final String[] PUBLIC_PATHS = {
            "/console/users/login",
            "/console/config/info",
            "/console/perms",
            "/console/custom_configs",
            "/console/monitor/query",
            "/console/healthz",
            "/actuator/health",
            "/actuator/info"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 放行 ERROR dispatch：未映射端点 404 转发 /error 时 OncePerRequestFilter 不重跑、
                        // 认证为空，若不放行会命中 entry point 误返 401/10405，导致前端对"未实现接口"误判为
                        // 需重新登录而回环。放行后未实现端点正常返回 404。
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 阻止 JwtAuthenticationFilter 被 Servlet 容器作为普通过滤器自动重复注册，
     * 它只应在 Security 过滤链中执行一次。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
