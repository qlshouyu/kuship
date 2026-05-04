package cn.kuship.console.modules.openapi.auth;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.entity.UserAccessKey;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserAccessKeyRepository;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * OpenAPI v1 认证 filter。
 *
 * <p>仅匹配 {@code /openapi/**} 路径；console 路径走 {@code JwtAuthenticationFilter}。
 *
 * <p>认证两种模式（满足任一即通过）：
 * <ul>
 *   <li>{@code X-Internal-Token} 头与环境变量 {@code INTERNAL_API_TOKEN} 比对 → 注入虚拟管理员（user_id=0）</li>
 *   <li>{@code Authorization} 头作为 PAT 在 {@code user_access_key} 表查询 → 加载 UserInfo（要求 sys_admin = true）</li>
 * </ul>
 *
 * <p>失败一律 401 + JSON {@code {"detail": "...", "code": 401}}（OpenAPI 风格，不走 general_message）。
 */
@Component
public class OpenApiAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OpenApiAuthFilter.class);
    private static final String OPENAPI_PREFIX = "/openapi/";

    /**
     * Path prefixes that bypass authentication entirely. Swagger UI bootstrap and the OpenAPI 3
     * JSON document are intentionally anonymous so unauthenticated users can browse the API
     * surface (Try-It-Out still hits real endpoints, which require real credentials).
     * Centralised here (instead of inline in {@code doFilterInternal}) so future additions stay
     * obvious to reviewers — e.g. {@code /openapi/v3/api-docs/swagger-config} introduced by
     * Springdoc's UI bootstrap.
     */
    static final List<String> SKIP_PATH_PREFIXES = List.of(
            "/openapi/v3/api-docs",
            "/openapi/swagger-ui",
            "/openapi/swagger-config");

    private final String internalToken;
    private final UserAccessKeyRepository accessKeyRepo;
    private final UserInfoRepository userInfoRepo;
    private final ObjectProvider<RequestContext> requestContextProvider;

    public OpenApiAuthFilter(
            @Value("${kuship.openapi.internal-token:${INTERNAL_API_TOKEN:}}") String internalToken,
            UserAccessKeyRepository accessKeyRepo,
            UserInfoRepository userInfoRepo,
            ObjectProvider<RequestContext> requestContextProvider) {
        this.internalToken = internalToken == null ? "" : internalToken;
        this.accessKeyRepo = accessKeyRepo;
        this.userInfoRepo = userInfoRepo;
        this.requestContextProvider = requestContextProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                       HttpServletResponse response,
                                       FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(OPENAPI_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 跳过 Swagger UI / API docs（add-openapi-swagger-ui）：dev profile 下匿名访问。
        for (String skip : SKIP_PATH_PREFIXES) {
            if (uri.startsWith(skip)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 1. X-Internal-Token
        String headerInternal = request.getHeader("X-Internal-Token");
        if (headerInternal != null && !headerInternal.isBlank()) {
            if (!internalToken.isBlank() && internalToken.equals(headerInternal)) {
                injectVirtualAdmin();
                filterChain.doFilter(request, response);
                return;
            }
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid internal token");
            return;
        }

        // 2. Authorization PAT
        String headerAuth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (headerAuth == null || headerAuth.isBlank()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return;
        }
        String token = headerAuth.startsWith("Bearer ") ? headerAuth.substring(7).trim() : headerAuth.trim();
        Optional<UserAccessKey> keyOpt = accessKeyRepo.findByAccessKey(token);
        if (keyOpt.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid access token");
            return;
        }
        UserAccessKey key = keyOpt.get();
        Optional<UserInfo> userOpt = key.getUserId() == null
                ? Optional.empty()
                : userInfoRepo.findById(key.getUserId());
        if (userOpt.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Token user not found");
            return;
        }
        UserInfo user = userOpt.get();
        if (!Boolean.TRUE.equals(user.getSysAdmin())) {
            writeError(response, HttpStatus.FORBIDDEN, "Permission denied: requires sys_admin");
            return;
        }
        injectUser(user);
        filterChain.doFilter(request, response);
    }

    private void injectVirtualAdmin() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "InternalAPI", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        RequestContext ctx = requestContextProvider.getIfAvailable();
        if (ctx != null) {
            ctx.setUserId(0);
            ctx.setUsername("InternalAPI");
            ctx.setSysAdmin(true);
        }
    }

    private void injectUser(UserInfo user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getNickName() != null ? user.getNickName() : String.valueOf(user.getUserId()),
                null,
                Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        RequestContext ctx = requestContextProvider.getIfAvailable();
        if (ctx != null) {
            ctx.setUserId(user.getUserId());
            ctx.setUsername(user.getNickName());
            ctx.setEmail(user.getEmail());
            ctx.setEnterpriseId(user.getEnterpriseId());
            ctx.setSysAdmin(true);
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"detail\":\"" + detail.replace("\"", "\\\"") + "\",\"code\":" + status.value() + "}";
        response.getWriter().write(body);
    }
}
