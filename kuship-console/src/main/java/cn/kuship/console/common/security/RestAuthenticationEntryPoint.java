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
import java.nio.charset.StandardCharsets;

/** 未认证访问受保护端点 → 401 统一信封。 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // body code 用 10405 对齐 rainbond（base.py:740：未认证统一 code 10405），HTTP 仍 401；
        // 前端 request.js 据 code 10405 触发 showNeedLogin → 跳转登录页（否则落根路由会一直 spin）。
        // rainbond 该信封为裸三字段（无 data 键），用 bare 保持逐字节一致。
        ApiResult body = GeneralMessage.bare(10405, "Signature has expired.", "身份认证信息失败，请登录");
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
