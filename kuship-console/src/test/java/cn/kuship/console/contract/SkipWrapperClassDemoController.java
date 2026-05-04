package cn.kuship.console.contract;

import cn.kuship.console.common.response.SkipResponseWrapper;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 类级 {@link SkipResponseWrapper} 测试用 controller —— 验证类级注解生效，类下所有方法都跳过包装。
 */
@RestController
@RequestMapping("/console/_contract/skip-class")
@Profile("contract-test")
@SkipResponseWrapper
public class SkipWrapperClassDemoController {

    @GetMapping("/raw")
    public Map<String, Object> raw() {
        return Map.of("class_level_skip", true);
    }
}
