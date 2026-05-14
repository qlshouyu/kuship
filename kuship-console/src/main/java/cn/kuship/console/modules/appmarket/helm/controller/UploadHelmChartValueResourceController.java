package cn.kuship.console.modules.appmarket.helm.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.appmarket.helm.api.HelmChartImportOperations;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UploadHelmChartValueResourceController {

    private final HelmChartImportOperations chartImportOps;

    public UploadHelmChartValueResourceController(HelmChartImportOperations chartImportOps) {
        this.chartImportOps = chartImportOps;
    }

    @PostMapping(value = {"/console/teams/{team_name}/import_upload_chart_resource",
                           "/console/teams/{team_name}/import_upload_chart_resource/"})
    public ApiResult importChartResource(@PathVariable("team_name") String teamName,
                                           @RequestParam(value = "region_name", required = false) String regionName,
                                           @RequestBody Map<String, Object> body) {
        return GeneralMessage.ok(chartImportOps.importUploadChartResource(regionName, body));
    }
}
