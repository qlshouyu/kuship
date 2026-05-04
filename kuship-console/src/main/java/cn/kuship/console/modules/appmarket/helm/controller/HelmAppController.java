package cn.kuship.console.modules.appmarket.helm.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.HelmOperations;
import cn.kuship.console.modules.appmarket.helm.repository.HelmRepoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** {@code /teams/{team}/helm_*} 5 endpoint。 */
@RestController
@RequestMapping("/console/teams/{team_name}")
public class HelmAppController {

    private final HelmOperations helmOps;
    private final HelmRepoRepository repoRepo;

    public HelmAppController(HelmOperations helmOps, HelmRepoRepository repoRepo) {
        this.helmOps = helmOps;
        this.repoRepo = repoRepo;
    }

    @PostMapping(value = {"/helm_app", "/helm_app/"})
    public ApiResult installHelm(@PathVariable("team_name") String teamName,
                                    @RequestBody Map<String, Object> body) {
        String region = String.valueOf(body.getOrDefault("region_name", ""));
        Map<String, Object> resp = helmOps.checkHelmApp(region, teamName, body);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @PostMapping(value = {"/helm_command", "/helm_command/"})
    public ApiResult helmCommand(@PathVariable("team_name") String teamName,
                                    @RequestBody Map<String, Object> body) {
        String region = String.valueOf(body.getOrDefault("region_name", ""));
        Map<String, Object> resp = helmOps.getYamlByChart(region, body);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/helm_list", "/helm_list/"})
    public ApiResult helmList(@PathVariable("team_name") String teamName) {
        return GeneralMessage.okList(repoRepo.findAll().stream()
                .map(HelmRepoController::toMaskedBean).toList());
    }

    @PostMapping(value = {"/helm_cmd_add", "/helm_cmd_add/"})
    public ApiResult helmCmdAdd(@PathVariable("team_name") String teamName,
                                    @RequestBody Map<String, Object> body) {
        String region = String.valueOf(body.getOrDefault("region_name", ""));
        Map<String, Object> resp = helmOps.getChartInformation(region, body);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @PostMapping(value = {"/helm_center_app", "/helm_center_app/"})
    public ApiResult helmCenterApp(@PathVariable("team_name") String teamName,
                                       @RequestBody Map<String, Object> body) {
        String region = String.valueOf(body.getOrDefault("region_name", ""));
        Map<String, Object> resp = helmOps.importUploadChartResource(region, body);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }
}
