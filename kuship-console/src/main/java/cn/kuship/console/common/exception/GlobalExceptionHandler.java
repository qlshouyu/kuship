package cn.kuship.console.common.exception;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.exception.ClusterAuthLackOfLicenseException;
import cn.kuship.console.infrastructure.region.exception.ClusterAuthLackOfLicenseExpireException;
import cn.kuship.console.infrastructure.region.exception.ClusterAuthLackOfMemoryException;
import cn.kuship.console.infrastructure.region.exception.ClusterAuthLackOfNodeException;
import cn.kuship.console.infrastructure.region.exception.ClusterLackOfMemoryException;
import cn.kuship.console.infrastructure.region.exception.InvalidLicenseException;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.exception.RegionApiFrequentException;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.exception.TenantLackOfCpuException;
import cn.kuship.console.infrastructure.region.exception.TenantLackOfMemoryException;
import cn.kuship.console.infrastructure.region.exception.TenantQuotaCpuLackException;
import cn.kuship.console.infrastructure.region.exception.TenantQuotaMemoryLackException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局异常映射 → general_message 形状响应。
 *
 * <p>HTTP 状态码与业务 {@code code} 对齐 rainbond-console（Django/DRF）行为：
 * <ul>
 *   <li>业务异常的 HTTP 状态码 = 业务 code（如 ServiceHandleException(404,...) → HTTP 404）</li>
 *   <li>Region 异常优先用其 {@code httpStatus}，缺失时退回 {@code code} 或 500</li>
 *   <li>响应 body 仍是 {@code general_message} 形状：{@code {code, msg, msg_show, data}}</li>
 * </ul>
 *
 * <p>这样前端 axios 默认按 HTTP 状态码进 catch 路径，不需为 kuship 额外改造 request.js，
 * 与 rainbond-ui 共享同一份请求 utility。
 *
 * <p>兜底分支会把 MDC 中的 traceId 写入 {@code data.bean.trace_id}，方便用户复制后报障。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceHandleException.class)
    public ResponseEntity<ApiResult> onServiceHandle(ServiceHandleException ex) {
        ApiResult body = GeneralMessage.error(ex.getCode(), ex.getMsg(), ex.getMsgShow());
        return ResponseEntity.status(toHttpStatus(ex.getCode(), 0)).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult> onValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("field", fe.getField());
            err.put("message", fe.getDefaultMessage());
            errors.add(err);
        });
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("errors", errors);
        ApiResult body = new ApiResult(400, summarize(errors), "参数校验失败", wrapData(bean));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult> onConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("field", v.getPropertyPath().toString());
            err.put("message", v.getMessage());
            errors.add(err);
        }
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("errors", errors);
        ApiResult body = new ApiResult(400, summarize(errors), "参数校验失败", wrapData(bean));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult> onUnreadableBody(HttpMessageNotReadableException ex) {
        ApiResult body = GeneralMessage.error(400, ex.getMostSpecificCause().getMessage(), "请求体解析失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResult> onBadRequest(Exception ex) {
        ApiResult body = GeneralMessage.error(400, ex.getMessage(), "请求参数不正确");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult> onIllegalArgument(IllegalArgumentException ex) {
        // 来自 PageRequestAdapter 等工具类的参数校验
        ApiResult body = GeneralMessage.error(400, ex.getMessage(), "参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ---- Region API exception family（migrate-console-region-client 追加） ----

    @ExceptionHandler(RegionApiFrequentException.class)
    public ResponseEntity<ApiResult> onRegionFrequent(RegionApiFrequentException ex) {
        return regionResponse(ex);
    }

    @ExceptionHandler(RegionApiSocketException.class)
    public ResponseEntity<ApiResult> onRegionSocket(RegionApiSocketException ex) {
        log.warn("region api socket error: api={} url={} method={} : {}",
                ex.getApiType(), ex.getUrl(), ex.getMethod(), ex.getMessage());
        return regionResponse(ex);
    }

    @ExceptionHandler(InvalidLicenseException.class)
    public ResponseEntity<ApiResult> onInvalidLicense(InvalidLicenseException ex) {
        return regionResponse(ex);
    }

    @ExceptionHandler({
            ClusterLackOfMemoryException.class,
            TenantLackOfMemoryException.class,
            TenantLackOfCpuException.class,
            TenantQuotaCpuLackException.class,
            TenantQuotaMemoryLackException.class,
            ClusterAuthLackOfMemoryException.class,
            ClusterAuthLackOfNodeException.class,
            ClusterAuthLackOfLicenseException.class,
            ClusterAuthLackOfLicenseExpireException.class
    })
    public ResponseEntity<ApiResult> onResourceShortage(RegionApiException ex) {
        return regionResponse(ex);
    }

    /** 通用 region 异常 —— 必须放在所有专门 region 异常 handler 之后。 */
    @ExceptionHandler(RegionApiException.class)
    public ResponseEntity<ApiResult> onRegionApi(RegionApiException ex) {
        return regionResponse(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult> onUncaught(Exception ex) {
        String traceId = MDC.get("traceId");
        log.error("uncaught exception traceId={} : {}", traceId, ex.getMessage(), ex);
        Map<String, Object> bean = new LinkedHashMap<>();
        if (traceId != null) {
            bean.put("trace_id", traceId);
        }
        ApiResult body = new ApiResult(500, ex.getMessage(), "系统异常", wrapData(bean));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Region 异常优先用 {@code httpStatus}（来自上游 region API 真实响应），
     * 缺失（socket 错误等场景为 0）时退回 {@code code}，再退回 500。
     */
    private static ResponseEntity<ApiResult> regionResponse(RegionApiException ex) {
        ApiResult body = GeneralMessage.error(ex.getCode(), ex.getMsg(), ex.getMsgShow());
        return ResponseEntity.status(toHttpStatus(ex.getCode(), ex.getHttpStatus())).body(body);
    }

    /**
     * 把业务码映射成合法 HTTP 状态码：
     * <ul>
     *   <li>{@code httpStatus} 在 [400, 599] 内：直接使用</li>
     *   <li>否则若 {@code code} 在 [400, 599] 内：使用 code（ServiceHandleException 的常见情况）</li>
     *   <li>否则（业务子码如 10400 / 20800）：退回 500</li>
     * </ul>
     */
    private static HttpStatus toHttpStatus(int code, int httpStatus) {
        if (httpStatus >= 400 && httpStatus <= 599) {
            HttpStatus s = HttpStatus.resolve(httpStatus);
            if (s != null) return s;
        }
        if (code >= 400 && code <= 599) {
            HttpStatus s = HttpStatus.resolve(code);
            if (s != null) return s;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String summarize(List<Map<String, Object>> errors) {
        if (errors.isEmpty()) {
            return "validation failed";
        }
        Map<String, Object> first = errors.get(0);
        return first.get("field") + ": " + first.get("message");
    }

    private static Map<String, Object> wrapData(Map<String, Object> bean) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bean", bean);
        data.put("list", List.of());
        return data;
    }
}
