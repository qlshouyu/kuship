package cn.kuship.console.common.response;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 信封自动包装的四类映射：POJO/Map→bean、Collection→list、Page→list+total、ApiResult 幂等。 */
class GeneralMessageResponseBodyAdviceTest {

    private final GeneralMessageResponseBodyAdvice advice = new GeneralMessageResponseBodyAdvice();

    private ApiResult wrap(Object body) {
        return (ApiResult) advice.beforeBodyWrite(body, null, null, null, null, null);
    }

    @Test
    void map_goes_into_bean() {
        ApiResult r = wrap(Map.of("token", "abc"));
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).containsKey("bean").containsKey("list");
        assertThat(r.getData().get("bean")).isEqualTo(Map.of("token", "abc"));
        assertThat(r.getData().get("list")).isEqualTo(List.of());
    }

    @Test
    void collection_goes_into_list() {
        ApiResult r = wrap(List.of("a", "b"));
        assertThat(r.getData().get("list")).isEqualTo(List.of("a", "b"));
        assertThat(r.getData().get("bean")).isEqualTo(Map.of());
    }

    @Test
    void page_splits_into_list_and_top_level_total() {
        var page = new PageImpl<>(List.of("x", "y"), PageRequest.of(0, 2), 7);
        ApiResult r = wrap(page);
        assertThat(r.getData().get("list")).isEqualTo(List.of("x", "y"));
        assertThat(r.getData().get("total")).isEqualTo(7L); // total 在 data 顶层，对齐 general_message
    }

    @Test
    void api_result_is_idempotent() {
        ApiResult original = GeneralMessage.bean(200, "login success", "登录成功", Map.of("token", "t"));
        Object out = advice.beforeBodyWrite(original, null, null, null, null, null);
        assertThat(out).isSameAs(original);
    }
}
