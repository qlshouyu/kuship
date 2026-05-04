package cn.kuship.console.modules.misc.gateway.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** API Gateway 4 endpoint（routes/certificates 透传占位）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/api-gateway")
public class ApiGatewayController {

    @GetMapping(value = {"/routes", "/routes/"})
    public ApiResult listRoutes(@PathVariable("team_name") String teamName) {
        return GeneralMessage.okList(List.of());
    }

    @PostMapping(value = {"/routes", "/routes/"})
    public ApiResult createRoute(@PathVariable("team_name") String teamName,
                                    @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(Map.of("created", true));
    }

    @GetMapping(value = {"/certificates", "/certificates/"})
    public ApiResult listCertificates(@PathVariable("team_name") String teamName) {
        return GeneralMessage.okList(List.of());
    }

    @PostMapping(value = {"/certificates", "/certificates/"})
    public ApiResult createCertificate(@PathVariable("team_name") String teamName,
                                            @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(Map.of("created", true));
    }
}
