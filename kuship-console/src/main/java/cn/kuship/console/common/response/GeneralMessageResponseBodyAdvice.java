package cn.kuship.console.common.response;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Collection;
import java.util.List;

/**
 * 自动把 controller 返回值包装成 {@link ApiResult} 信封，对齐 rainbond-console 的 general_message：
 * <ul>
 *   <li>POJO / Map → data.bean</li>
 *   <li>Collection → data.list</li>
 *   <li>Page → data.list = content，data.total = totalElements</li>
 *   <li>已是 ApiResult → 幂等不重复包装</li>
 *   <li>String 返回 → 不包装（见 {@link #supports}，避免 StringHttpMessageConverter 类型冲突）</li>
 *   <li>标注 {@link SkipResponseWrapper} → 不包装（SSE/文件下载）</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "cn.kuship.console")
public class GeneralMessageResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        // String 返回交给 StringHttpMessageConverter，包装会引发 ClassCastException —— 跳过
        if (String.class.equals(returnType.getParameterType())) {
            return false;
        }
        // 显式跳过标注
        if (returnType.hasMethodAnnotation(SkipResponseWrapper.class)) {
            return false;
        }
        Class<?> declaringClass = returnType.getContainingClass();
        return !declaringClass.isAnnotationPresent(SkipResponseWrapper.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        // 已是信封，幂等
        if (body instanceof ApiResult) {
            return body;
        }
        if (body instanceof Page<?> page) {
            return GeneralMessage.page(200, "success", "", page.getContent(), page.getTotalElements());
        }
        if (body instanceof Collection<?> collection) {
            return GeneralMessage.list(200, "success", "", List.copyOf(collection));
        }
        // null 或任意 POJO/Map → bean
        return GeneralMessage.bean(200, "success", "", body);
    }
}
