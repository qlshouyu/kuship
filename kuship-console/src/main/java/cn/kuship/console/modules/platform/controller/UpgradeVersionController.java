package cn.kuship.console.modules.platform.controller;

import cn.kuship.console.common.response.SkipResponseWrapper;
import cn.kuship.console.modules.platform.service.UpgradeVersionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台可升级版本（对齐 rainbond UpgradeVersionLView）。
 * 注意：rainbond 此接口直接返回裸 JSON 数组（不走 general_message 信封），故标注 {@link SkipResponseWrapper}。
 */
@RestController
public class UpgradeVersionController {

    private final UpgradeVersionService service;

    public UpgradeVersionController(UpgradeVersionService service) {
        this.service = service;
    }

    /** GET /console/update/versions —— 返回裸数组 ["v6.9.0", ...]，无数据时 []。 */
    @GetMapping("/console/update/versions")
    @SkipResponseWrapper
    public List<String> versions() {
        return service.listVersions();
    }
}
