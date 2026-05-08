package cn.kuship.console.config;

import cn.kuship.console.common.security.GeneralMessageAccessDeniedHandler;
import cn.kuship.console.common.security.GeneralMessageAuthenticationEntryPoint;
import cn.kuship.console.common.security.JwtAuthenticationFilter;
import cn.kuship.console.modules.misc.mcp.auth.McpAuthFilter;
import cn.kuship.console.modules.openapi.auth.OpenApiAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security 配置：JWT 认证生效。
 *
 * <p>permitAll 白名单：
 * <ol>
 *   <li>{@code /actuator/**}、{@code /error}（运维与错误页）</li>
 *   <li>{@code /console/healthz} / {@code /console/healthz/}（健康检查）</li>
 *   <li>{@code POST /console/users/login}、{@code POST /console/users/register}（登录/注册）</li>
 *   <li>{@code POST /console/users/logout}（登出，rainbond 仅返回占位响应）</li>
 *   <li>{@code GET /console/enterprise/info}（登录页平台信息）</li>
 *   <li>{@code GET /console/perms}（权限元数据，登录前权限树渲染）</li>
 *   <li>{@code /console/oauth/**}（OAuth provider 路由由 oauth change 接管，本 change 暂占位放行）</li>
 *   <li>{@code POST /console/init/perms}（仅 {@code kuship.security.allow-public-init=true} 时；默认关闭）</li>
 * </ol>
 *
 * <p>其他 {@code /console/**}、{@code /openapi/**} 要求 JWT。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtAuthenticationFilter,
                                                    OpenApiAuthFilter openApiAuthFilter,
                                                    McpAuthFilter mcpAuthFilter,
                                                    GeneralMessageAuthenticationEntryPoint entryPoint,
                                                    GeneralMessageAccessDeniedHandler accessDeniedHandler,
                                                    @Value("${kuship.security.allow-public-init:false}") boolean allowPublicInit) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth
                            .requestMatchers(
                                    "/actuator/**",
                                    "/error",
                                    "/console/healthz",
                                    "/console/healthz/",
                                    "/console/oauth/**",
                                    // /app-server/** 反代外部应用市场（hub.grapps.cn），与 rainbond 一致公开匿名；
                                    // controller 透传 method/path/headers/body，登录前后均可使用。
                                    "/app-server/**",
                                    "/openapi/**",
                                    // add-mcp-sse — MCP endpoints authenticate via PAT (Bearer header
                                    // or ?access_token= for SSE EventSource compat) inside McpAuthFilter,
                                    // not via JWT. Spring Security must let unauthenticated requests
                                    // reach the controller chain so the filter can apply its own auth.
                                    "/console/mcp/query",
                                    "/console/mcp/query/",
                                    "/console/mcp/query/**",
                                    // add-openapi-swagger-ui — explicit docs paths so the intent stays
                                    // visible if the broader /openapi/** matcher is ever tightened.
                                    "/openapi/v3/api-docs",
                                    "/openapi/v3/api-docs/**",
                                    "/openapi/swagger-ui",
                                    "/openapi/swagger-ui/**"
                            ).permitAll()
                            .requestMatchers(HttpMethod.POST, "/console/users/login", "/console/users/login/",
                                    "/console/users/register", "/console/users/register/",
                                    "/console/users/logout", "/console/users/logout/",
                                    // add-aliyun-sms — phone-based register/login + send-code happen before
                                    // the user has any JWT, so they must be reachable anonymously. The SMS
                                    // verification flow itself protects against abuse via 60s rate limit
                                    // (per-phone) + 5-min/5-fail brute-force lockout.
                                    "/console/sms/send-code", "/console/sms/send-code/",
                                    "/console/users/register-by-phone", "/console/users/register-by-phone/",
                                    "/console/users/login-by-phone", "/console/users/login-by-phone/").permitAll()
                            // harden-webhook-hmac — webhook trigger endpoints authenticate via HMAC
                            // signature / secret query inside WebhookTriggerController, not via JWT.
                            // Spring Security must let GitHub / GitLab / Harbor callers reach the
                            // controller (which then enforces signature validation itself).
                            .requestMatchers(HttpMethod.POST,
                                    "/console/webhooks/*", "/console/webhooks/*/",
                                    "/console/image/webhooks/*", "/console/image/webhooks/*/",
                                    "/console/custom/deploy/*", "/console/custom/deploy/*/")
                            .permitAll()
                            .requestMatchers(HttpMethod.GET, "/console/enterprise/info", "/console/enterprise/info/",
                                    "/console/perms", "/console/perms/",
                                    // migrate-console-platform-config — 站点元数据匿名公开
                                    "/console/config/info", "/console/config/info/").permitAll()
                            // 全局自定义配置：对齐 rainbond CustomConfigsCLView (BaseApiView, 无需 JWT)
                            .requestMatchers("/console/custom_configs", "/console/custom_configs/").permitAll();
                    if (allowPublicInit) {
                        auth.requestMatchers(HttpMethod.POST, "/console/init/perms", "/console/init/perms/").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(openApiAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(mcpAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
