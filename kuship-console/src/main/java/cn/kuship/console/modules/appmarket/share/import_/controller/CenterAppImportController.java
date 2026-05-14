package cn.kuship.console.modules.appmarket.share.import_.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import cn.kuship.console.modules.appmarket.share.import_.api.AppImportOperations;
import cn.kuship.console.modules.appmarket.share.import_.entity.AppImportRecord;
import cn.kuship.console.modules.appmarket.share.import_.repository.AppImportRecordRepository;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class CenterAppImportController {

    private final AppImportOperations importOps;
    private final AppImportRecordRepository recordRepo;

    public CenterAppImportController(AppImportOperations importOps, AppImportRecordRepository recordRepo) {
        this.importOps = importOps;
        this.recordRepo = recordRepo;
    }

    @PostMapping(value = {"/import", "/import/"})
    @RequireEnterpriseAdmin
    public ApiResult initImport(@PathVariable("enterprise_id") String enterpriseId,
                                  @RequestParam(value = "region_name", required = false) String regionName,
                                  @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        Map<String, Object> resp = importOps.importApp2Enterprise(regionName, enterpriseId, safe);
        String eventId = resp != null && resp.get("event_id") != null ? resp.get("event_id").toString() : null;

        AppImportRecord r = new AppImportRecord();
        r.setEventId(eventId);
        r.setStatus(resp != null && resp.get("status") != null ? resp.get("status").toString() : "init");
        r.setScope("enterprise");
        r.setEnterpriseId(enterpriseId);
        r.setRegion(regionName);
        if (safe.get("source_dir") != null) r.setSourceDir(safe.get("source_dir").toString());
        if (safe.get("format") != null) r.setFormat(safe.get("format").toString());
        recordRepo.save(r);

        Map<String, Object> bean = new LinkedHashMap<>(resp == null ? Map.of() : resp);
        bean.put("record_id", r.getId());
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/import/{event_id}", "/import/{event_id}/"})
    @RequireEnterpriseAdmin
    public ApiResult getStatus(@PathVariable("enterprise_id") String enterpriseId,
                                 @PathVariable("event_id") String eventId,
                                 @RequestParam(value = "region_name", required = false) String regionName) {
        AppImportRecord local = recordRepo.findByEventId(eventId)
                .orElseThrow(() -> new ServiceHandleException(404, "import event not found", "导入事件不存在"));
        Map<String, Object> resp = importOps.getEnterpriseImportStatus(regionName, enterpriseId, eventId);
        if (resp != null && resp.get("status") != null) {
            local.setStatus(resp.get("status").toString());
            recordRepo.save(local);
        }
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @DeleteMapping(value = {"/import/{event_id}", "/import/{event_id}/"})
    @RequireEnterpriseAdmin
    public ApiResult cancelImport(@PathVariable("enterprise_id") String enterpriseId,
                                    @PathVariable("event_id") String eventId,
                                    @RequestParam(value = "region_name", required = false) String regionName) {
        try {
            importOps.deleteEnterpriseImport(regionName, enterpriseId, eventId);
        } catch (RegionApiException e) {
            if (e.getHttpStatus() != 404) throw e;
        }
        recordRepo.findByEventId(eventId).ifPresent(recordRepo::delete);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/import/{event_id}/dir", "/import/{event_id}/dir/"})
    @RequireEnterpriseAdmin
    public ApiResult getFileDir(@PathVariable("enterprise_id") String enterpriseId,
                                  @PathVariable("event_id") String eventId,
                                  @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.ok(importOps.getEnterpriseImportFileDir(regionName, enterpriseId, eventId));
    }

    @DeleteMapping(value = {"/import/{event_id}/dir", "/import/{event_id}/dir/"})
    @RequireEnterpriseAdmin
    public ApiResult deleteFileDir(@PathVariable("enterprise_id") String enterpriseId,
                                     @PathVariable("event_id") String eventId,
                                     @RequestParam(value = "region_name", required = false) String regionName) {
        try {
            importOps.deleteImportFileDir(regionName, enterpriseId, eventId, true);
        } catch (RegionApiException e) {
            if (e.getHttpStatus() != 404) throw e;
        }
        return GeneralMessage.ok();
    }
}
