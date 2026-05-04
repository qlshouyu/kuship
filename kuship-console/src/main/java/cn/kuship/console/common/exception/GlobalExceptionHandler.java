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
 * <p>HTTP 状态码与业务 {@code code} 解耦（rainbond-console 祖传约定）：
 * <ul>
 *   <li>除 401/403 由 Spring Security 的 EntryPoint/Handler 写出对应 HTTP 状态码外，其他异常一律 HTTP 200</li>
 *   <li>业务码走响应体 {@code code} 字段（如 400、404、500）</li>
 * </ul>
 *
 * <p>兜底分支会把 MDC 中的 traceId 写入 {@code data.bean.trace_id}，方便用户复制后报障。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceHandleException.class)
    public ApiResult onServiceHandle(ServiceHandleException ex) {
        return GeneralMessage.error(ex.getCode(), ex.getMsg(), ex.getMsgShow());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult onValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("field", fe.getField());
            err.put("message", fe.getDefaultMessage());
            errors.add(err);
        });
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("errors", errors);
        return new ApiResult(400, summarize(errors), "参数校验失败",
                wrapData(bean));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult onConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("field", v.getPropertyPath().toString());
            err.put("message", v.getMessage());
            errors.add(err);
        }
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("errors", errors);
        return new ApiResult(400, summarize(errors), "参数校验失败",
                wrapData(bean));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResult onUnreadableBody(HttpMessageNotReadableException ex) {
        return GeneralMessage.error(400, ex.getMostSpecificCause().getMessage(), "请求体解析失败");
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    public ApiResult onBadRequest(Exception ex) {
        return GeneralMessage.error(400, ex.getMessage(), "请求参数不正确");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult onIllegalArgument(IllegalArgumentException ex) {
        // 来自 PageRequestAdapter 等工具类的参数校验
        return GeneralMessage.error(400, ex.getMessage(), "参数校验失败");
    }

    // ---- Region API exception family（migrate-console-region-client 追加） ----

    @ExceptionHandler(RegionApiFrequentException.class)
    public ApiResult onRegionFrequent(RegionApiFrequentException ex) {
        return GeneralMessage.error(ex.getCode(), ex.getMsg(), ex.getMsgShow());
    }

    @ExceptionHandler(RegionApiSocketException.class)
    public ApiResult onRegionSocket(RegionApiSocketException ex) {
        log.warn("region api socket error: api={} url={} method={} : {}",
                ex.getApiType(), ex.getUrl(), ex.getMethod(), ex.getMessage());
        return GeneralMessage.error(ex.getCode(), ex.getMsg(), ex.getMsgShow());
    }

    @ExceptionHandler(InvalidLicenseException.class)
    public ApiResult onInvalidLicense(InvalidLicenseException ex) {
        return GeneralMessage.error(ex.getCode(), ex.getMsg(), ex.getMsgShow());
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
    public ApiResult onResourceShortage(RegionApiException ex) {
        return GeneralMessage.error(ex.getCode(), ex.getMsg(), ex.getMsgShow());
    }

    /** 通用 region 异常 —— 必须放在所有专门 region 异常 handler 之后。 */
    @ExceptionHandler(RegionApiException.class)
    public ApiResult onRegionApi(RegionApiException ex) {
        return GeneralMessage.error(ex.getCode(), ex.getMsg(), ex.getMsgShow());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult onUncaught(Exception ex) {
        String traceId = MDC.get("traceId");
        log.error("uncaught exception traceId={} : {}", traceId, ex.getMessage(), ex);
        Map<String, Object> bean = new LinkedHashMap<>();
        if (traceId != null) {
            bean.put("trace_id", traceId);
        }
        return new ApiResult(500, ex.getMessage(), "系统异常", wrapData(bean));
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
