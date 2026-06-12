package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.service.AccountProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 当前用户详情，对齐 rainbond UserDetailsView。 */
@RestController
public class UserProfileController {

    private final AccountProfileService accountProfileService;

    public UserProfileController(AccountProfileService accountProfileService) {
        this.accountProfileService = accountProfileService;
    }

    /** GET /console/users/details */
    @GetMapping("/console/users/details")
    public ApiResult details() {
        Map<String, Object> bean = accountProfileService.currentUserDetails();
        return GeneralMessage.bean(200, "Obtain my details to be successful.", "获取我的详情成功", bean);
    }
}
