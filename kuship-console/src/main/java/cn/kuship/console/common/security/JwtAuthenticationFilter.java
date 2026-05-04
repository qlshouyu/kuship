package cn.kuship.console.common.security;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 解析 {@code Authorization} 头中的 JWT token。
 *
 * <p>解析合法 token 后，从 {@link UserInfoRepository#findById(Object)} 加载真实用户，
 * 写入 SecurityContext 与 {@link RequestContext}（包括 enterpriseId / sysAdmin）。
 *
 * <p>token 中的 user_id 在数据库中不存在 → 写入 {@link #ATTR_AUTH_FAILURE_REASON} = {@code "user not found"}，
 * 后续 EntryPoint 兜底产 401。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_AUTH_FAILURE_REASON = "kuship.auth.failure_reason";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenService tokenService;
    private final JwtProperties properties;
    private final ObjectProvider<RequestContext> requestContextProvider;
    private final UserInfoRepository userInfoRepository;

    public JwtAuthenticationFilter(JwtTokenService tokenService,
                                   JwtProperties properties,
                                   ObjectProvider<RequestContext> requestContextProvider,
                                   UserInfoRepository userInfoRepository) {
        this.tokenService = tokenService;
        this.properties = properties;
        this.requestContextProvider = requestContextProvider;
        this.userInfoRepository = userInfoRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = extractToken(header, properties.authHeaderPrefixes());
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            JwtClaims claims = tokenService.decode(token);
            Optional<UserInfo> userOpt = claims.userId() == null
                    ? Optional.empty()
                    : userInfoRepository.findById(claims.userId().intValue());
            if (userOpt.isEmpty()) {
                request.setAttribute(ATTR_AUTH_FAILURE_REASON, "user not found");
                filterChain.doFilter(request, response);
                return;
            }
            UserInfo user = userOpt.get();
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    user.getNickName() != null ? user.getNickName() : String.valueOf(user.getUserId()),
                    null,
                    Collections.emptyList());
            auth.setDetails(claims);
            SecurityContextHolder.getContext().setAuthentication(auth);

            RequestContext ctx = requestContextProvider.getIfAvailable();
            if (ctx != null) {
                ctx.setUserId(user.getUserId());
                ctx.setUsername(user.getNickName());
                ctx.setEmail(user.getEmail());
                ctx.setEnterpriseId(user.getEnterpriseId());
                ctx.setSysAdmin(Boolean.TRUE.equals(user.getSysAdmin()));
            }
        } catch (JwtException ex) {
            String reason = describeFailure(ex);
            log.debug("jwt parse failed: {}", reason);
            request.setAttribute(ATTR_AUTH_FAILURE_REASON, reason);
        }
        filterChain.doFilter(request, response);
    }

    static String extractToken(String headerValue, List<String> prefixes) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        int spaceIdx = headerValue.indexOf(' ');
        if (spaceIdx <= 0) {
            return null;
        }
        String prefix = headerValue.substring(0, spaceIdx);
        String value = headerValue.substring(spaceIdx + 1).trim();
        if (value.isEmpty()) {
            return null;
        }
        for (String accepted : prefixes) {
            if (accepted.equalsIgnoreCase(prefix)) {
                return value;
            }
        }
        return null;
    }

    private static String describeFailure(JwtException ex) {
        String type = ex.getClass().getSimpleName();
        return switch (type) {
            case "ExpiredJwtException" -> "token expired";
            case "SignatureException" -> "invalid signature";
            case "MalformedJwtException" -> "malformed token";
            case "UnsupportedJwtException" -> "unsupported token";
            case "PrematureJwtException" -> "token not yet valid";
            default -> ex.getMessage() != null ? ex.getMessage() : "invalid token";
        };
    }
}
