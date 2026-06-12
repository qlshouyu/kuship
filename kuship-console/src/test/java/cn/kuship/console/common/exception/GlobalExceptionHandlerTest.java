package cn.kuship.console.common.exception;

import cn.kuship.console.common.response.ApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP 状态码 = 业务 code；兜底 500 携带 trace_id。 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void service_exception_propagates_status_and_msg_show() {
        ResponseEntity<ApiResult> resp =
                handler.handleService(ServiceHandleException.badRequest("authorization fail ", "密码不正确"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getMsgShow()).isEqualTo("密码不正确");
    }

    @Test
    void no_permissions_maps_to_403_with_error_code_10402() {
        ResponseEntity<ApiResult> resp = handler.handleService(new NoPermissionsException());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN); // HTTP 403
        assertThat(resp.getBody().getCode()).isEqualTo(10402);            // 信封 code = errorCode
        assertThat(resp.getBody().getMsgShow()).isEqualTo("没有操作权限");
    }

    @Test
    void validation_maps_to_400() {
        ResponseEntity<ApiResult> resp = handler.handleValidation(new IllegalArgumentException("bad arg"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getCode()).isEqualTo(400);
    }

    @Test
    void fallback_maps_to_500_with_trace_id_bean() {
        ResponseEntity<ApiResult> resp = handler.handleAny(new RuntimeException("boom"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getCode()).isEqualTo(500);
        assertThat(resp.getBody().getData().get("bean")).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        var bean = (java.util.Map<String, Object>) resp.getBody().getData().get("bean");
        assertThat(bean).containsKey("trace_id");
    }
}
