package cn.kuship.console.modules.region.controller.lang;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import cn.kuship.console.modules.application.api.LangVersionOperations;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 多语言版本管理（平台级）：
 * GET / POST / PUT / DELETE {@code /console/enterprise/{eid}/regions/{rn}/lang-version}
 * 与 GET {@code /cnb/frameworks}。仅 enterprise admin 可操作。
 */
@RestController
public class LangVersionController {

    private final LangVersionOperations langOps;

    public LangVersionController(LangVersionOperations langOps) {
        this.langOps = langOps;
    }

    @GetMapping(value = {"/console/enterprise/{enterprise_id}/regions/{region_name}/lang-version",
                          "/console/enterprise/{enterprise_id}/regions/{region_name}/lang-version/"})
    @RequireEnterpriseAdmin
    public ApiResult getLangVersion(@PathVariable("enterprise_id") String enterpriseId,
                                       @PathVariable("region_name") String regionName,
                                       @RequestParam(required = false) String lang,
                                       @RequestParam(required = false) String show,
                                       @RequestParam(name = "build_strategy", required = false) String buildStrategy) {
        return GeneralMessage.ok(langOps.getLangVersion(enterpriseId, regionName, lang, show, buildStrategy));
    }

    @PostMapping(value = {"/console/enterprise/{enterprise_id}/regions/{region_name}/lang-version",
                            "/console/enterprise/{enterprise_id}/regions/{region_name}/lang-version/"})
    @RequireEnterpriseAdmin
    public ApiResult createLangVersion(@PathVariable("enterprise_id") String enterpriseId,
                                          @PathVariable("region_name") String regionName,
                                          @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(langOps.createLangVersion(enterpriseId, regionName, body));
    }

    @PutMapping(value = {"/console/enterprise/{enterprise_id}/regions/{region_name}/lang-version",
                          "/console/enterprise/{enterprise_id}/regions/{region_name}/lang-version/"})
    @RequireEnterpriseAdmin
    public ApiResult updateLangVersion(@PathVariable("enterprise_id") String enterpriseId,
                                          @PathVariable("region_name") String regionName,
                                          @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(langOps.updateLangVersion(enterpriseId, regionName, body));
    }

    @DeleteMapping(value = {"/console/enterprise/{enterprise_id}/regions/{region_name}/lang-version",
                              "/console/enterprise/{enterprise_id}/regions/{region_name}/lang-version/"})
    @RequireEnterpriseAdmin
    public ApiResult deleteLangVersion(@PathVariable("enterprise_id") String enterpriseId,
                                          @PathVariable("region_name") String regionName,
                                          @RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(langOps.deleteLangVersion(enterpriseId, regionName, body));
    }

    @GetMapping(value = {"/console/enterprise/{enterprise_id}/regions/{region_name}/cnb/frameworks",
                          "/console/enterprise/{enterprise_id}/regions/{region_name}/cnb/frameworks/"})
    @RequireEnterpriseAdmin
    public ApiResult getCnbFrameworks(@PathVariable("enterprise_id") String enterpriseId,
                                          @PathVariable("region_name") String regionName,
                                          @RequestParam(required = false, defaultValue = "nodejs") String lang) {
        return GeneralMessage.ok(langOps.getCnbFrameworks(enterpriseId, regionName, lang));
    }
}
