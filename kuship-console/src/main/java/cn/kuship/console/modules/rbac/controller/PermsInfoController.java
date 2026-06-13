package cn.kuship.console.modules.rbac.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.entity.ServiceGroup;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import cn.kuship.console.modules.rbac.perms.PermsCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限元数据树（对齐 rainbond {@code PermsInfoLView}，免团队鉴权）。
 * {@code tenant_id} 用于展开团队应用（team_app_manage）的 app 子模型（对齐 get_perms_structure）。
 */
@RestController
public class PermsInfoController {

    private final ServiceGroupRepository serviceGroupRepository;

    public PermsInfoController(ServiceGroupRepository serviceGroupRepository) {
        this.serviceGroupRepository = serviceGroupRepository;
    }

    /** GET /console/perms?tenant_id=... ：返回权限元数据树（name/desc/code），含该租户应用的 app 子模型。 */
    @GetMapping("/console/perms")
    public ApiResult perms(@RequestParam(value = "tenant_id", required = false) String tenantId) {
        List<Integer> appIds = (tenantId == null || tenantId.isBlank())
                ? List.of()
                : serviceGroupRepository.findByTenantId(tenantId).stream().map(ServiceGroup::getId).toList();
        return GeneralMessage.bean(200, null, null, PermsCatalog.getPermsStructure(appIds));
    }
}
