package cn.kuship.console.modules.misc.upgrade.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Console 自身升级查询（MVP 占位）。 */
@RestController
@RequestMapping("/console/enterprise/upgrade")
public class ConsoleUpgradeController {

    private static final String CURRENT_VERSION = "0.1.0-SNAPSHOT";

    @GetMapping(value = {"", "/"})
    public ApiResult current() {
        return GeneralMessage.ok(Map.of(
                "current_version", CURRENT_VERSION,
                "build_time", "unknown"));
    }

    @GetMapping(value = {"/version", "/version/"})
    public ApiResult versions() {
        return GeneralMessage.okList(List.of(Map.of(
                "version", CURRENT_VERSION,
                "is_current", true)));
    }

    @GetMapping(value = {"/version/{version}", "/version/{version}/"})
    public ApiResult versionDetail(@PathVariable("version") String version) {
        return GeneralMessage.ok(Map.of(
                "version", version,
                "is_current", CURRENT_VERSION.equals(version)));
    }

    @GetMapping(value = {"/version/{version}/images", "/version/{version}/images/"})
    public ApiResult versionImages(@PathVariable("version") String version) {
        return GeneralMessage.okList(List.of());
    }
}
