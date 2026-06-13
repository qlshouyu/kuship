package cn.kuship.console.modules.enterprise.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.enterprise.service.EnterpriseUserReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 企业用户列表读（对齐 rainbond EnterPriseUsersCLView，JWTAuthApiView——仅登录鉴权）。
 */
@RestController
public class EnterpriseUserController {

    private final EnterpriseUserReadService service;

    public EnterpriseUserController(EnterpriseUserReadService service) {
        this.service = service;
    }

    /** GET /console/enterprise/{enterprise_id}/users?page=&page_size=&query= */
    @GetMapping("/console/enterprise/{enterprise_id}/users")
    public ApiResult listUsers(@PathVariable("enterprise_id") String enterpriseId,
                               @RequestParam(value = "page", defaultValue = "1") int page,
                               @RequestParam(value = "page_size", defaultValue = "10") int pageSize,
                               @RequestParam(value = "query", required = false) String query) {
        Map<String, Object> r = service.listUsers(enterpriseId, query, page, pageSize);
        ApiResult res = GeneralMessage.list(200, "success", null, (List<?>) r.get("list"));
        res.putExtra("page_size", r.get("page_size"));
        res.putExtra("page", r.get("page"));
        res.putExtra("total", r.get("total"));
        return res;
    }
}
