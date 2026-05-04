package cn.kuship.console.modules.misc.config.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 企业激活：绑定云端市场 access token（占位）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/enterprise/active")
public class EnterpriseActiveController {

    @PostMapping(value = {"", "/"})
    public ApiResult bind(@PathVariable("team_name") String teamName,
                            @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(Map.of("bound", true, "team_name", teamName));
    }

    @PostMapping(value = {"/optimiz", "/optimiz/"})
    public ApiResult bindOptimiz(@PathVariable("team_name") String teamName,
                                     @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(Map.of("bound", true, "team_name", teamName, "mode", "optimiz"));
    }
}
