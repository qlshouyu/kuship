package cn.kuship.console.modules.appmarket.share.upload.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.appmarket.share.upload.api.AppUploadOperations;
import cn.kuship.console.modules.appmarket.share.upload.api.LoadTarImageOperations;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** {@code /console/teams/{team_name}/app-upload/events/{event_id}}：上传事件 CRUD + tar 镜像加载。 */
@RestController
@RequestMapping("/console/teams/{team_name}")
public class AppUploadController {

    private final AppUploadOperations uploadOps;
    private final LoadTarImageOperations loadTarOps;

    public AppUploadController(AppUploadOperations uploadOps, LoadTarImageOperations loadTarOps) {
        this.uploadOps = uploadOps;
        this.loadTarOps = loadTarOps;
    }

    @PostMapping(value = {"/app-upload/events/{event_id}", "/app-upload/events/{event_id}/"})
    public ApiResult create(@PathVariable("team_name") String teamName,
                             @PathVariable("event_id") String eventId,
                             @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.ok(uploadOps.createUploadDir(regionName, teamName, eventId));
    }

    @GetMapping(value = {"/app-upload/events/{event_id}", "/app-upload/events/{event_id}/"})
    public ApiResult get(@PathVariable("team_name") String teamName,
                          @PathVariable("event_id") String eventId,
                          @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.ok(uploadOps.getUploadDir(regionName, teamName, eventId));
    }

    @DeleteMapping(value = {"/app-upload/events/{event_id}", "/app-upload/events/{event_id}/"})
    public ApiResult delete(@PathVariable("team_name") String teamName,
                             @PathVariable("event_id") String eventId,
                             @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.ok(uploadOps.deleteUploadDir(regionName, teamName, eventId));
    }

    @PutMapping(value = {"/app-upload/events/{event_id}/component_id/{component_id}",
                          "/app-upload/events/{event_id}/component_id/{component_id}/"})
    public ApiResult updateComponent(@PathVariable("team_name") String teamName,
                                       @PathVariable("event_id") String eventId,
                                       @PathVariable("component_id") String componentId,
                                       @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.ok(uploadOps.updateUploadDir(regionName, teamName, eventId, componentId));
    }

    @PostMapping(value = {"/app/load_tar_image", "/app/load_tar_image/"})
    public ApiResult loadTarImage(@PathVariable("team_name") String teamName,
                                    @RequestParam(value = "region_name", required = false) String regionName,
                                    @RequestBody Map<String, Object> body) {
        return GeneralMessage.ok(loadTarOps.loadTarImage(regionName, teamName, body));
    }
}
