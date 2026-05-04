package cn.kuship.console.modules.appmarket.market.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.appmarket.market.entity.RainbondCenterApp;
import cn.kuship.console.modules.appmarket.market.entity.RainbondCenterAppVersion;
import cn.kuship.console.modules.appmarket.market.repository.RainbondCenterAppRepository;
import cn.kuship.console.modules.appmarket.market.repository.RainbondCenterAppVersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 应用模板 CRUD：列表 / 创建 / 详情 / 更新 / 删除 + 版本详情 GET。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}")
public class CenterAppController {

    private final RainbondCenterAppRepository appRepo;
    private final RainbondCenterAppVersionRepository versionRepo;

    public CenterAppController(RainbondCenterAppRepository appRepo,
                                  RainbondCenterAppVersionRepository versionRepo) {
        this.appRepo = appRepo;
        this.versionRepo = versionRepo;
    }

    @GetMapping(value = {"/app-models", "/app-models/"})
    public ApiResult list(@PathVariable("enterprise_id") String enterpriseId,
                            @RequestParam(value = "page", defaultValue = "1") int page,
                            @RequestParam(value = "page_size", defaultValue = "10") int pageSize,
                            @RequestParam(value = "scope", required = false) String scope,
                            @RequestParam(value = "tag_id", required = false) Integer tagId) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 200) pageSize = 10;
        var pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "updateTime"));
        Page<RainbondCenterApp> result;
        if (tagId != null) {
            result = appRepo.findByEnterpriseIdAndTagId(enterpriseId, tagId, pageable);
        } else if (scope != null && !scope.isEmpty()) {
            result = appRepo.findByEnterpriseIdAndScope(enterpriseId, scope, pageable);
        } else {
            result = appRepo.findByEnterpriseId(enterpriseId, pageable);
        }
        return GeneralMessage.ok(Map.of(
                "list", result.getContent().stream().map(CenterAppController::toBean).toList(),
                "total", result.getTotalElements()));
    }

    @PostMapping(value = {"/app-models", "/app-models/"})
    @Transactional
    public ApiResult create(@PathVariable("enterprise_id") String enterpriseId,
                              @RequestBody Map<String, Object> body) {
        String appName = (String) body.get("app_name");
        if (appName == null || appName.isBlank()) {
            throw new ServiceHandleException(400, "missing app_name", "缺少 app_name");
        }
        RainbondCenterApp app = new RainbondCenterApp();
        app.setAppId(UuidGenerator.makeUuid());
        app.setAppName(appName);
        app.setEnterpriseId(enterpriseId);
        app.setScope(body.getOrDefault("scope", "enterprise").toString());
        app.setDescribe((String) body.getOrDefault("describe", ""));
        app.setPic((String) body.get("pic"));
        app.setSource((String) body.getOrDefault("source", "local"));
        app.setIsIngerit(false);
        app.setIsOfficial(false);
        app.setIsVersion(false);
        app.setInstallNumber(0);
        app.setArch("amd64");
        app.setCreateTime(LocalDateTime.now());
        app.setUpdateTime(LocalDateTime.now());
        appRepo.save(app);
        return GeneralMessage.ok(toBean(app));
    }

    @GetMapping(value = {"/app-model/{app_id}", "/app-model/{app_id}/"})
    public ApiResult detail(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("app_id") String appId) {
        RainbondCenterApp app = appRepo.findByAppId(appId)
                .orElseThrow(() -> new ServiceHandleException(404, "app not found", "应用模板不存在"));
        Map<String, Object> bean = toBean(app);
        bean.put("versions", versionRepo.findByAppId(appId).stream().map(CenterAppController::versionToBean).toList());
        return GeneralMessage.ok(bean);
    }

    @PutMapping(value = {"/app-model/{app_id}", "/app-model/{app_id}/"})
    @Transactional
    public ApiResult update(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("app_id") String appId,
                              @RequestBody Map<String, Object> body) {
        RainbondCenterApp app = appRepo.findByAppId(appId)
                .orElseThrow(() -> new ServiceHandleException(404, "app not found", "应用模板不存在"));
        if (body.get("app_name") instanceof String n) app.setAppName(n);
        if (body.get("scope") instanceof String s) app.setScope(s);
        if (body.get("describe") instanceof String d) app.setDescribe(d);
        if (body.get("pic") instanceof String p) app.setPic(p);
        app.setUpdateTime(LocalDateTime.now());
        appRepo.save(app);
        return GeneralMessage.ok(toBean(app));
    }

    @DeleteMapping(value = {"/app-model/{app_id}", "/app-model/{app_id}/"})
    @Transactional
    public ApiResult delete(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("app_id") String appId) {
        appRepo.findByAppId(appId).ifPresent(appRepo::delete);
        versionRepo.deleteByAppId(appId);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/app-model/{app_id}/version/{version}", "/app-model/{app_id}/version/{version}/"})
    public ApiResult versionDetail(@PathVariable("enterprise_id") String enterpriseId,
                                      @PathVariable("app_id") String appId,
                                      @PathVariable("version") String version) {
        RainbondCenterAppVersion v = versionRepo.findByAppIdAndVersion(appId, version)
                .orElseThrow(() -> new ServiceHandleException(404, "version not found", "版本不存在"));
        return GeneralMessage.ok(versionToBean(v));
    }

    static Map<String, Object> toBean(RainbondCenterApp a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app_id", a.getAppId());
        m.put("app_name", a.getAppName());
        m.put("pic", a.getPic());
        m.put("scope", a.getScope());
        m.put("describe", a.getDescribe());
        m.put("dev_status", a.getDevStatus());
        m.put("is_official", a.getIsOfficial());
        m.put("install_number", a.getInstallNumber());
        m.put("source", a.getSource());
        m.put("arch", a.getArch());
        m.put("update_time", a.getUpdateTime());
        return m;
    }

    static Map<String, Object> versionToBean(RainbondCenterAppVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", v.getVersion());
        m.put("version_alias", v.getVersionAlias());
        m.put("app_version_info", v.getAppVersionInfo());
        m.put("scope", v.getScope());
        m.put("template_version", v.getTemplateVersion());
        m.put("is_complete", v.getIsComplete());
        m.put("template_type", v.getTemplateType());
        m.put("update_time", v.getUpdateTime());
        return m;
    }
}
