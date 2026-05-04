package cn.kuship.console.common.response;

import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局 ResponseBodyAdvice：把所有 @RestController 返回值自动包装为 {@link ApiResult}（rainbond-console
 * {@code general_message} 形状）。
 *
 * <p>包装规则：
 * <ul>
 *   <li>{@link ApiResult} → 原样返回（幂等）</li>
 *   <li>{@link Page} → {@code data.list = page.content}, {@code data.bean.total = page.totalElements}（不输出顶层 page/page_size）</li>
 *   <li>{@link List} → {@code data.list = list}</li>
 *   <li>{@link String} → {@code data.bean.value = str}（避免 Jackson 把字符串当 JSON 字符串）</li>
 *   <li>其他 POJO / Map → {@code data.bean = pojo}</li>
 * </ul>
 *
 * <p>跳过包装的场景（{@link #supports} 判定）：
 * <ul>
 *   <li>方法或类标注 {@link SkipResponseWrapper}</li>
 *   <li>方法所在类位于 {@code org.springframework.boot.actuate.*} 包（actuator 端点）</li>
 *   <li>返回类型是 {@link Resource} 或其它流式输出（由 Spring 用专门 converter 直接写）</li>
 * </ul>
 */
@ControllerAdvice
public class GeneralMessageResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final String ACTUATOR_PACKAGE_PREFIX = "org.springframework.boot.actuate.";
    private static final String OPENAPI_PACKAGE_PREFIX = "cn.kuship.console.modules.openapi";
    /**
     * Springdoc's own webmvc controllers (OpenApiResource, OpenApiWebMvcResource, etc.) live under
     * {@code org.springdoc.webmvc.api}. They write the OpenAPI 3 JSON / Swagger UI bootstrap
     * directly and must NOT be wrapped in our general_message envelope.
     */
    private static final String SPRINGDOC_PACKAGE_PREFIX = "org.springdoc.";

    private final ObjectMapper objectMapper;

    public GeneralMessageResponseBodyAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (returnType.getMethod() == null) {
            return false;
        }
        // 方法级注解优先：方法标注则跳过；否则看类级注解
        if (returnType.getMethodAnnotation(SkipResponseWrapper.class) != null) {
            return false;
        }
        Class<?> declaringClass = returnType.getContainingClass();
        if (declaringClass.isAnnotationPresent(SkipResponseWrapper.class)) {
            return false;
        }
        // 排除 Spring Boot Actuator 自身的端点
        String pkg = declaringClass.getName();
        if (pkg.startsWith(ACTUATOR_PACKAGE_PREFIX)) {
            return false;
        }
        // 排除 OpenAPI v1 模块：使用独立的 detail/code 错误格式，不走 general_message 包装
        if (pkg.startsWith(OPENAPI_PACKAGE_PREFIX)) {
            return false;
        }
        // 排除 Springdoc 自身的 controller (add-openapi-swagger-ui)：直接输出 OpenAPI JSON / Swagger UI HTML
        if (pkg.startsWith(SPRINGDOC_PACKAGE_PREFIX)) {
            return false;
        }
        Class<?> rt = returnType.getParameterType();
        // 排除文件下载 / 二进制资源
        if (Resource.class.isAssignableFrom(rt)) {
            return false;
        }
        // 排除 String 返回类型：Spring 默认强制 cast 到 String，且 advice 包装后类型不匹配。
        // 业务需要返回 string-like 数据时显式 return GeneralMessage.ok(Map.of("value", str)) 或返回 ApiResult。
        if (String.class.equals(rt)) {
            return false;
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // 排除非业务路径（actuator 路径名以 /actuator 开头会被框架直接 handle，但保险起见再排一次）
        if (request instanceof ServletServerHttpRequest servletReq) {
            String path = servletReq.getServletRequest().getRequestURI();
            if (path != null && path.startsWith("/actuator")) {
                return body;
            }
        }

        // 已经是 ApiResult → 幂等
        if (body instanceof ApiResult) {
            return body;
        }

        // Page<T> → bean={total:N}, list=content
        if (body instanceof Page<?> page) {
            Map<String, Object> bean = new LinkedHashMap<>();
            bean.put("total", page.getTotalElements());
            return GeneralMessage.okWithExtras(bean, page.getContent(), null);
        }

        // List<T> → list=...
        if (body instanceof List<?> list) {
            return GeneralMessage.okList(list);
        }

        // null → 空响应
        if (body == null) {
            return GeneralMessage.ok();
        }

        // 其他 POJO / Map → bean=POJO
        Map<String, Object> bean = toMap(body);
        return GeneralMessage.ok(bean);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object body) {
        if (body instanceof Map<?, ?> raw) {
            // 直接接受 Map<String,Object>；其他键类型先转字符串
            Map<String, Object> result = new LinkedHashMap<>(raw.size());
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        try {
            // POJO → Map：经 Jackson 序列化以应用所有 @JsonProperty/@JsonInclude 等注解
            @SuppressWarnings("unchecked")
            Map<String, Object> converted = objectMapper.convertValue(body, Map.class);
            return converted;
        } catch (RuntimeException ex) {
            // 极端情况下转换失败，把 toString() 当 value 兜底
            Map<String, Object> bean = new LinkedHashMap<>();
            bean.put("value", body.toString());
            return bean;
        }
    }
}
