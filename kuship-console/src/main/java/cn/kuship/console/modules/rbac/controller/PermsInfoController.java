package cn.kuship.console.modules.rbac.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.rbac.perms.PermsCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限元数据树（对齐 rainbond {@code PermsInfoLView}，免团队鉴权）。
 * {@code tenant_id} 用于展开团队应用子模型——kuship 无应用域，忽略该参数。
 */
@RestController
public class PermsInfoController {

    /** GET /console/perms?tenant_id=... ：返回权限元数据树（name/desc/code）。 */
    @GetMapping("/console/perms")
    public ApiResult perms(@RequestParam(value = "tenant_id", required = false) String tenantId) {
        return GeneralMessage.bean(200, null, null, PermsCatalog.getPermsStructure());
    }
}
