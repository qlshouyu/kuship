package cn.kuship.console.modules.enterprise.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.enterprise.service.LicenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业 license 查询（对齐 rainbond LicenseLView.get）。
 * authz_code 或 license 为空 → HTTP 400 + code 400「invalid authz code」，bean={authz_code}；否则 200 bean=license。
 */
@RestController
public class LicenseController {

    private final LicenseService service;

    public LicenseController(LicenseService service) {
        this.service = service;
    }

    /** GET /console/enterprise/{enterprise_id}/licenses */
    @GetMapping("/console/enterprise/{enterprise_id}/licenses")
    public ResponseEntity<ApiResult> licenses(@PathVariable("enterprise_id") String enterpriseId) {
        LicenseService.LicenseResult r = service.getLicenses(enterpriseId);
        if (r.authzCode() == null || r.authzCode().isEmpty() || r.license() == null) {
            Map<String, Object> bean = new LinkedHashMap<>();
            bean.put("authz_code", r.authzCode() == null ? "" : r.authzCode());
            return ResponseEntity.status(400)
                    .body(GeneralMessage.bean(400, "invalid authz code", "无效授权码", bean));
        }
        return ResponseEntity.ok(GeneralMessage.bean(200, "success", "查询成功", r.license()));
    }
}
