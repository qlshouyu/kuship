package cn.kuship.console.modules.openapi.exception;

import cn.kuship.console.common.exception.ServiceHandleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** OpenAPI 风格异常映射：返回 {@code {detail, code}} JSON + HTTP 状态码与业务码一致。 */
@RestControllerAdvice(basePackages = "cn.kuship.console.modules.openapi")
public class OpenApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OpenApiExceptionHandler.class);

    @ExceptionHandler(ServiceHandleException.class)
    public ResponseEntity<Map<String, Object>> handleServiceException(ServiceHandleException ex) {
        int code = ex.getCode();
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(payload(code, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload(400, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.error("openapi unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(payload(500, "Internal server error: " + ex.getClass().getSimpleName()));
    }

    private static Map<String, Object> payload(int code, String detail) {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("detail", detail == null ? "" : detail);
        bean.put("code", code);
        return bean;
    }
}
