package cn.kuship.console.modules.openapi.v1.admin.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.openapi.v1.user.controller.OpenApiUserController;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** OpenAPI v1 administrator 端点：2 endpoint —— 仅 sys_admin = true 用户。 */
@RestController
public class OpenApiAdminController {

    private final UserInfoRepository userRepo;

    public OpenApiAdminController(UserInfoRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping(value = {"/openapi/v1/administrators", "/openapi/v1/administrators/"})
    public List<Map<String, Object>> list() {
        return userRepo.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getSysAdmin()))
                .map(OpenApiUserController::toBean)
                .toList();
    }

    @GetMapping(value = {"/openapi/v1/administrators/{user_id}", "/openapi/v1/administrators/{user_id}/"})
    public Map<String, Object> detail(@PathVariable("user_id") Integer userId) {
        UserInfo u = userRepo.findById(userId)
                .orElseThrow(() -> new ServiceHandleException(404, "user not found", "用户不存在"));
        if (!Boolean.TRUE.equals(u.getSysAdmin())) {
            throw new ServiceHandleException(404, "not an administrator", "用户不是管理员");
        }
        return OpenApiUserController.toBean(u);
    }
}
