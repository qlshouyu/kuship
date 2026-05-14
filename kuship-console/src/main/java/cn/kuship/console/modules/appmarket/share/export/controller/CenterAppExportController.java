package cn.kuship.console.modules.appmarket.share.export.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import cn.kuship.console.modules.appmarket.share.export.api.AppExportOperations;
import cn.kuship.console.modules.appmarket.share.export.entity.AppExportRecord;
import cn.kuship.console.modules.appmarket.share.export.repository.AppExportRecordRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/console/enterprise/{enterprise_id}/app-models")
public class CenterAppExportController {

    private final AppExportOperations exportOps;
    private final AppExportRecordRepository recordRepo;

    public CenterAppExportController(AppExportOperations exportOps, AppExportRecordRepository recordRepo) {
        this.exportOps = exportOps;
        this.recordRepo = recordRepo;
    }

    @PostMapping(value = {"/export", "/export/"})
    @RequireEnterpriseAdmin
    public ApiResult exportTrigger(@PathVariable("enterprise_id") String enterpriseId,
                                    @RequestParam(value = "region_name", required = false) String regionName,
                                    @RequestBody Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        String groupKey = stringValue(safe, "app_key");
        String version = stringValue(safe, "app_versions");
        String format = stringValue(safe, "format");
        if (groupKey == null || groupKey.isBlank()) {
            throw new ServiceHandleException(400, "app_key required", "app_key 不能为空");
        }

        Map<String, Object> resp = exportOps.exportApp(regionName, enterpriseId, safe);
        String eventId = resp == null ? null : (resp.get("event_id") == null ? null : resp.get("event_id").toString());

        AppExportRecord r = new AppExportRecord();
        r.setGroupKey(groupKey);
        r.setVersion(version == null ? "" : version);
        r.setFormat(format == null ? "rainbond-app" : format);
        r.setEventId(eventId);
        r.setStatus(resp != null && resp.get("status") != null ? resp.get("status").toString() : "init");
        r.setEnterpriseId(enterpriseId);
        r.setRegionName(regionName);
        recordRepo.save(r);

        Map<String, Object> bean = new LinkedHashMap<>(resp == null ? Map.of() : resp);
        bean.put("record_id", r.getId());
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/export/{event_id}/status", "/export/{event_id}/status/"})
    @RequireEnterpriseAdmin
    public ApiResult exportStatus(@PathVariable("enterprise_id") String enterpriseId,
                                   @PathVariable("event_id") String eventId,
                                   @RequestParam(value = "region_name", required = false) String regionName) {
        Map<String, Object> resp = exportOps.getExportStatus(regionName, enterpriseId, eventId);
        recordRepo.findByEventId(eventId).ifPresent(r -> {
            if (resp != null && resp.get("status") != null) {
                r.setStatus(resp.get("status").toString());
                if (resp.get("file_path") != null) {
                    r.setFilePath(resp.get("file_path").toString());
                }
                recordRepo.save(r);
            }
        });
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    private static String stringValue(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}
