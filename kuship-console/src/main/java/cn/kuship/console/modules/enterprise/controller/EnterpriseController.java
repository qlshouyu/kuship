package cn.kuship.console.modules.enterprise.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.enterprise.service.EnterpriseContextResolver;
import cn.kuship.console.modules.enterprise.service.EnterpriseReadService;
import cn.kuship.console.modules.rbac.perms.PermsCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 企业读路径，路径显式 /console/... 前缀，变量保持 snake_case。 */
@RestController
public class EnterpriseController {

    private final EnterpriseReadService enterpriseReadService;
    private final EnterpriseContextResolver enterpriseContextResolver;
    private final cn.kuship.console.modules.enterprise.service.EnterpriseInfoService enterpriseInfoService;

    public EnterpriseController(EnterpriseReadService enterpriseReadService,
                               EnterpriseContextResolver enterpriseContextResolver,
                               cn.kuship.console.modules.enterprise.service.EnterpriseInfoService enterpriseInfoService) {
        this.enterpriseReadService = enterpriseReadService;
        this.enterpriseContextResolver = enterpriseContextResolver;
        this.enterpriseInfoService = enterpriseInfoService;
    }

    /** GET /console/enterprises */
    @GetMapping("/console/enterprises")
    public ApiResult enterprises() {
        List<Map<String, Object>> list = enterpriseReadService.listEnterprisesForCurrentUser();
        return GeneralMessage.list(200, "success", "查询成功", list);
    }

    /** GET /console/enterprise/{enterprise_id}/overview */
    @GetMapping("/console/enterprise/{enterprise_id}/overview")
    public ApiResult overview(@PathVariable("enterprise_id") String enterpriseId) {
        enterpriseContextResolver.requireEnterprise(enterpriseId);
        Map<String, Object> bean = enterpriseReadService.overview(enterpriseId);
        return GeneralMessage.bean(200, "success", null, bean);
    }

    /** GET /console/enterprise/{enterprise_id}/admin/roles —— 企业角色名列表（对齐 AdminRolesView，纯计算）。 */
    @GetMapping("/console/enterprise/{enterprise_id}/admin/roles")
    public ApiResult adminRoles(@PathVariable("enterprise_id") String enterpriseId) {
        return GeneralMessage.list(200, "success", null, List.copyOf(PermsCatalog.ENTERPRISE.keySet()));
    }

    /** GET /console/enterprise/{enterprise_id}/info —— 企业信息（对齐 EnterpriseRUDView.get）。 */
    @GetMapping("/console/enterprise/{enterprise_id}/info")
    public ApiResult info(@PathVariable("enterprise_id") String enterpriseId) {
        return GeneralMessage.bean(200, "success", "查询成功", enterpriseInfoService.info(enterpriseId));
    }
}
