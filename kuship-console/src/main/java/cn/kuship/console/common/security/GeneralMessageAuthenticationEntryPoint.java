package cn.kuship.console.common.security;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 兜底：未认证或 token 失效时输出 general_message 形状响应。
 *
 * <p>{@code msg} 字段始终包含具体原因（来自 {@link JwtAuthenticationFilter#ATTR_AUTH_FAILURE_REASON}），
 * {@code msg_show} 始终为统一文案 {@code "未认证或 token 失效"}（不向最终用户泄露内部细节）。
 */
@Component
public class GeneralMessageAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MSG_SHOW = "未认证或 token 失效";

    private final ObjectMapper objectMapper;

    public GeneralMessageAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.ATTR_AUTH_FAILURE_REASON);
        String msg;
        if (reason != null) {
            msg = reason.toString();
        } else {
            String authHeader = request.getHeader("Authorization");
            msg = (authHeader == null || authHeader.isBlank()) ? "missing token" : "invalid token";
        }
        ApiResult body = GeneralMessage.error(401, msg, MSG_SHOW);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
