package cn.kuship.console.common.security;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * JWT 认证过滤器：解析 GRJWT/jwt/Bearer（大小写不敏感）前缀的 token，验签 → 查 Redis 黑名单 →
 * 校验 user_id 真实存在于 user_info → 写入 SecurityContext 与 RequestContext。
 *
 * <p>无 token：放行为匿名（受保护端点稍后由授权拒绝）。token 非法/吊销/用户不存在：直接 401 并附具体原因。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProperties properties;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklist;
    private final UserInfoRepository userRepository;
    private final RequestContext requestContext;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtProperties properties, JwtService jwtService,
                                   TokenBlacklistService blacklist, UserInfoRepository userRepository,
                                   RequestContext requestContext, ObjectMapper objectMapper) {
        this.properties = properties;
        this.jwtService = jwtService;
        this.blacklist = blacklist;
        this.userRepository = userRepository;
        this.requestContext = requestContext;
        this.objectMapper = objectMapper;
    }

    /**
     * 公开路径（login/config/info/healthz 等）跳过 token 校验：
     * 浏览器可能携带过期/无效的 cookie token 访问这些匿名端点，若仍校验会误返 401（对齐 rainbond AllowAny）。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String p : cn.kuship.console.config.SecurityConfig.PUBLIC_PATHS) {
            if (uri.equals(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        JwtClaims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtVerifyException e) {
            write401(response, "invalid token: " + e.getMessage(), "登录已失效，请重新登录");
            return;
        }

        if (blacklist.isBlacklisted(token)) {
            write401(response, "token revoked", "登录已失效，请重新登录");
            return;
        }

        if (claims.userId() == null) {
            write401(response, "user_id missing in token", "登录已失效，请重新登录");
            return;
        }
        Optional<UserInfo> user = userRepository.findById(claims.userId());
        if (user.isEmpty()) {
            write401(response, "user not found", "用户不存在，请重新登录");
            return;
        }

        requestContext.setCurrentUser(user.get());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.get(), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    /** 从 Authorization 头（前缀大小写不敏感）解析 token，回退到 cookie。 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && !header.isBlank()) {
            String[] parts = header.trim().split("\\s+", 2);
            if (parts.length == 2) {
                for (String prefix : properties.getHeaderPrefixes()) {
                    if (prefix.equalsIgnoreCase(parts[0])) {
                        return parts[1].trim();
                    }
                }
            }
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (properties.getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void write401(HttpServletResponse response, String msg, String msgShow) throws IOException {
        // body code 用 10405（对齐 rainbond「需要登录」契约：JWTAuthApiView 认证失败均返 10405），
        // 前端 request.js 据此触发 showNeedLogin → 跳转登录；HTTP 状态仍 401。
        ApiResult body = GeneralMessage.message(10405, msg, msgShow);
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
