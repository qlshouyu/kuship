package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.service.UserSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户模糊查询（对齐 rainbond UserFuzSerView，JWTAuthApiView 仅登录）。
 */
@RestController
public class UserQueryController {

    private final UserSearchService userSearchService;

    public UserQueryController(UserSearchService userSearchService) {
        this.userSearchService = userSearchService;
    }

    /** GET /console/users/query?query_key= */
    @GetMapping("/console/users/query")
    public ApiResult query(@RequestParam(value = "query_key", required = false) String queryKey) {
        if (queryKey == null || queryKey.isEmpty()) {
            return GeneralMessage.list(200, "query user success", "你没有查询任何用户", List.of());
        }
        List<Map<String, Object>> list = userSearchService.search(queryKey);
        return GeneralMessage.list(200, "query user success", "查询用户成功", list);
    }
}
