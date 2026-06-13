package cn.kuship.console.modules.app.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** status_map 策略表移植校验（关键档 + 表外）。 */
class StatusTranslateTest {

    @Test
    @SuppressWarnings("unchecked")
    void running_policy() {
        Map<String, Object> m = StatusTranslate.getStatusInfoMap("running");
        assertThat(m.get("status")).isEqualTo("running");
        assertThat(m.get("status_cn")).isEqualTo("运行中");
        assertThat((List<String>) m.get("disabledAction")).containsExactly("restart");
        assertThat((List<String>) m.get("activeAction"))
                .containsExactly("stop", "deploy", "visit", "manage_container", "reboot");
        assertThat(m.keySet()).containsExactly("status", "status_cn", "disabledAction", "activeAction");
    }

    @Test
    @SuppressWarnings("unchecked")
    void undeploy_policy() {
        Map<String, Object> m = StatusTranslate.getStatusInfoMap("undeploy");
        assertThat(m.get("status_cn")).isEqualTo("未部署");
        assertThat((List<String>) m.get("activeAction")).containsExactly("deploy");
        assertThat((List<String>) m.get("disabledAction"))
                .containsExactly("restart", "stop", "visit", "manage_container", "reboot");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknown_status_falls_to_default() {
        Map<String, Object> m = StatusTranslate.getStatusInfoMap("deployed");
        assertThat(m.get("status")).isEqualTo("deployed");
        assertThat(m.get("status_cn")).isEqualTo("未知");
        assertThat((List<String>) m.get("disabledAction")).isEmpty();
        assertThat((List<String>) m.get("activeAction")).isEmpty();
    }
}
