package cn.kuship.console.healthz;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 业务健康检查端点：{@code GET /console/healthz}。
 *
 * <p>用途：
 * <ul>
 *   <li>验证 Spring 应用 + JPA validate + Security permitAll + 响应包装契约打通</li>
 *   <li>区别于 Actuator 的 {@code /actuator/health}（用于探活/运维）</li>
 *   <li>为后续 change 引入业务 controller 提供"最小可运行示例"</li>
 * </ul>
 *
 * <p>同时支持 {@code /console/healthz} 和 {@code /console/healthz/} 两种路径形式
 * （Spring 6 已不支持全局 trailing slash 匹配，本 change 选择"在 controller 注解里显式列出"）。
 */
@RestController
public class HealthzController {

    @GetMapping({"/console/healthz", "/console/healthz/"})
    public ApiResult healthz() {
        return GeneralMessage.ok(GeneralMessage.MSG_SHOW_OK, Map.of());
    }
}
