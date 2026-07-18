package cn.kuship.console.common.exception;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常映射，对齐 DRF：HTTP 状态码 = 业务 code。
 * <ul>
 *   <li>{@link ServiceHandleException} → 透传其 status / msg / msg_show</li>
 *   <li>参数校验类异常 → 400</li>
 *   <li>兜底 Exception → 500，body 带 {@code data.bean.trace_id}</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "cn.kuship.console")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceHandleException.class)
    public ResponseEntity<ApiResult> handleService(ServiceHandleException ex) {
        // 信封 code 取业务 errorCode（默认回落 status），HTTP 取 status；bare 则裸信封（无 data）
        ApiResult body = ex.isBare()
                ? GeneralMessage.bare(ex.getErrorCode(), ex.getMsg(), ex.getMsgShow())
                : GeneralMessage.message(ex.getErrorCode(), ex.getMsg(), ex.getMsgShow());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResult> handleValidation(Exception ex) {
        String msgShow = "请求参数有误";
        ApiResult body = GeneralMessage.message(400, ex.getMessage(), msgShow);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult> handleAny(Exception ex) {
        log.error("unhandled exception, traceId={}", TraceContext.get(), ex);
        ApiResult body = GeneralMessage.error("system error");
        // 兜底 500 在 data.bean.trace_id 回填，便于排障
        java.util.Map<String, Object> bean = new java.util.LinkedHashMap<>();
        bean.put("trace_id", TraceContext.get());
        body.getData().put("bean", bean);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
