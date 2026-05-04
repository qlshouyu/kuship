package cn.kuship.console.modules.misc.mcp.auth;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.entity.UserAccessKey;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserAccessKeyRepository;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.Optional;

/**
 * MCP authentication filter — only matches /console/mcp/query/**.
 *
 * <p>Accepts:
 * <ul>
 *   <li>{@code Authorization: Bearer <PAT>} header (preferred)</li>
 *   <li>{@code ?access_token=<PAT>} query parameter — only on the SSE endpoint, since browser
 *       EventSource API cannot set custom headers. POST endpoints reject query mode.</li>
 * </ul>
 *
 * <p>Failure → 401 + {@code {"detail": "...", "code": 401}}.
 */
@Component
public class McpAuthFilter extends OncePerRequestFilter {

    private static final String MCP_PREFIX = "/console/mcp/query";
    private static final String SSE_PATH = "/console/mcp/query/sse";

    private final UserAccessKeyRepository accessKeyRepo;
    private final UserInfoRepository userInfoRepo;
    private final ObjectProvider<RequestContext> requestContextProvider;

    public McpAuthFilter(UserAccessKeyRepository accessKeyRepo,
                            UserInfoRepository userInfoRepo,
                            ObjectProvider<RequestContext> requestContextProvider) {
        this.accessKeyRepo = accessKeyRepo;
        this.userInfoRepo = userInfoRepo;
        this.requestContextProvider = requestContextProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                       FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(MCP_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        // Strip trailing slash for SSE matching
        boolean isSse = uri.equals(SSE_PATH) || uri.equals(SSE_PATH + "/");
        String token = readToken(request, isSse);
        if (token == null || token.isBlank()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return;
        }
        Optional<UserAccessKey> keyOpt = accessKeyRepo.findByAccessKey(token);
        if (keyOpt.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid access token");
            return;
        }
        UserAccessKey key = keyOpt.get();
        Optional<UserInfo> userOpt = key.getUserId() == null
                ? Optional.empty() : userInfoRepo.findById(key.getUserId());
        if (userOpt.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Token user not found");
            return;
        }
        UserInfo user = userOpt.get();
        if (!Boolean.TRUE.equals(user.getActive())) {
            writeError(response, HttpStatus.UNAUTHORIZED, "User inactive");
            return;
        }
        injectUser(user);
        filterChain.doFilter(request, response);
    }

    private String readToken(HttpServletRequest request, boolean allowQuery) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && !header.isBlank()) {
            return header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        }
        if (allowQuery) {
            String q = request.getParameter("access_token");
            if (q != null && !q.isBlank()) return q.trim();
        }
        return null;
    }

    private void injectUser(UserInfo user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getNickName() != null ? user.getNickName() : String.valueOf(user.getUserId()),
                null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        RequestContext ctx = requestContextProvider.getIfAvailable();
        if (ctx != null) {
            ctx.setUserId(user.getUserId());
            ctx.setUsername(user.getNickName());
            ctx.setEmail(user.getEmail());
            ctx.setEnterpriseId(user.getEnterpriseId());
            ctx.setSysAdmin(Boolean.TRUE.equals(user.getSysAdmin()));
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
