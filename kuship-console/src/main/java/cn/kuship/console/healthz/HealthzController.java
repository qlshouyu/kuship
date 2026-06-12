package cn.kuship.console.healthz;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** GET /console/healthz —— 存活探针，返回值经信封自动包装为 data.bean。 */
@RestController
public class HealthzController {

    @GetMapping("/console/healthz")
    public Map<String, Object> healthz() {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("status", "ok");
        bean.put("service", "kuship-console");
        return bean;
    }
}
