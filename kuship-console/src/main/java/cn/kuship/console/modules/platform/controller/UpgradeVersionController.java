package cn.kuship.console.modules.platform.controller;

import cn.kuship.console.common.response.SkipResponseWrapper;
import cn.kuship.console.modules.platform.service.UpgradeVersionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** GET /console/update/versions/{version} —— 裸对象：版本 detail，未命中/无清单 {}。 */
    @GetMapping("/console/update/versions/{version}")
    @SkipResponseWrapper
    public Object versionDetail(@PathVariable("version") String version) {
        return service.versionDetail(version);
    }

    /** GET /console/update/versions/{version}/images —— 裸对象：版本镜像清单，未命中/无清单 {}。 */
    @GetMapping("/console/update/versions/{version}/images")
    @SkipResponseWrapper
    public Object versionImages(@PathVariable("version") String version) {
        return service.versionImages(version);
    }
}
