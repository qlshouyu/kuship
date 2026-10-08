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

    /**
     * region API 调用失败（对齐 rainbond base.py CallApiError 分支，裸信封无 data 键）：
     * region 404 → HTTP 404 固定文案；其余 → HTTP 400，msg 为结构化 dict
     * {apitype,url,method,httpcode,body}，msg_show=「数据中心操作故障 <core>」。
     */
    @ExceptionHandler(RegionCallException.class)
    @cn.kuship.console.common.response.SkipResponseWrapper
    public ResponseEntity<Object> handleRegionCall(RegionCallException ex) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (ex.getHttpcode() == 404) {
            out.put("code", 404);
            out.put("msg", "region no found this resource");
            out.put("msg_show", "数据中心资源不存在");
            return ResponseEntity.status(404).body(out);
        }
        java.util.Map<String, Object> message = new java.util.LinkedHashMap<>();
        message.put("apitype", "Not specified");
        message.put("url", ex.getUrl());
        message.put("method", ex.getMethod());
        message.put("httpcode", ex.getHttpcode());
        message.put("body", ex.getBody());
        String coreError = String.valueOf(message);
        if (ex.getBody() instanceof java.util.Map<?, ?> bm && bm.get("msg") != null) {
            coreError = String.valueOf(bm.get("msg"));
        }
        out.put("code", 400);
        out.put("msg", message);
        out.put("msg_show", "数据中心操作故障 " + coreError);
        return ResponseEntity.status(400).body(out);
    }

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
