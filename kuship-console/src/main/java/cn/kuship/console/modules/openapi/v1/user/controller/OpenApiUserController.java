package cn.kuship.console.modules.openapi.v1.user.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAPI v1 user 端点：7 endpoint。 */
@RestController
public class OpenApiUserController {

    private final UserInfoRepository userRepo;
    private final RequestContext requestContext;
    private final LegacyPasswordEncoder encoder;

    public OpenApiUserController(UserInfoRepository userRepo, RequestContext requestContext,
                                    LegacyPasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.requestContext = requestContext;
        this.encoder = encoder;
    }

    @GetMapping(value = {"/openapi/v1/users", "/openapi/v1/users/"})
    public List<Map<String, Object>> list() {
        String eid = requestContext.getEnterpriseId();
        List<UserInfo> users = eid != null && !eid.isBlank()
                ? userRepo.findByEnterpriseId(eid)
                : userRepo.findAll();
        return users.stream().map(OpenApiUserController::toBean).toList();
    }

    @GetMapping(value = {"/openapi/v1/currentuser", "/openapi/v1/currentuser/"})
    public Map<String, Object> current() {
        Integer userId = requestContext.getUserId();
        if (userId == null || userId == 0) {
            // 内部调用 / 未注入：返回虚拟 admin 占位
            return Map.of(
                    "user_id", 0,
                    "nick_name", requestContext.getUsername() != null ? requestContext.getUsername() : "InternalAPI",
                    "sys_admin", true);
        }
        return userRepo.findById(userId)
                .map(OpenApiUserController::toBean)
                .orElseThrow(() -> new ServiceHandleException(404, "user not found", "用户不存在"));
    }

    @GetMapping(value = {"/openapi/v1/users/{user_id}", "/openapi/v1/users/{user_id}/"})
    public Map<String, Object> detail(@PathVariable("user_id") Integer userId) {
        return userRepo.findById(userId)
                .map(OpenApiUserController::toBean)
                .orElseThrow(() -> new ServiceHandleException(404, "user not found", "用户不存在"));
    }

    @PostMapping(value = {"/openapi/v1/changepwd", "/openapi/v1/changepwd/"})
    @Transactional
    public Map<String, Object> changeOwnPassword(@RequestBody Map<String, Object> body) {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证");
        }
        return doChangePassword(userId, body);
    }

    @PostMapping(value = {"/openapi/v1/users/{user_id}/changepwd", "/openapi/v1/users/{user_id}/changepwd/"})
    @Transactional
    public Map<String, Object> changeOtherPassword(@PathVariable("user_id") Integer userId,
                                                          @RequestBody Map<String, Object> body) {
        return doChangePassword(userId, body);
    }

    @PostMapping(value = {"/openapi/v1/users/{user_id}/close", "/openapi/v1/users/{user_id}/close/"})
    @Transactional
    public Map<String, Object> closeUser(@PathVariable("user_id") Integer userId) {
        UserInfo u = userRepo.findById(userId)
                .orElseThrow(() -> new ServiceHandleException(404, "user not found", "用户不存在"));
        u.setActive(false);
        userRepo.save(u);
        return Map.of("user_id", userId, "is_active", false);
    }

    @PostMapping(value = {"/openapi/v1/users/{user_id}/delete", "/openapi/v1/users/{user_id}/delete/"})
    @Transactional
    public Map<String, Object> deleteUser(@PathVariable("user_id") Integer userId) {
        userRepo.findById(userId).ifPresent(userRepo::delete);
        return Map.of("user_id", userId, "deleted", true);
    }

    private Map<String, Object> doChangePassword(Integer userId, Map<String, Object> body) {
        String newPwd = String.valueOf(body.getOrDefault("new_password", ""));
        if (newPwd.length() < 6) {
            throw new ServiceHandleException(400, "password too short", "密码至少 6 字符");
        }
        UserInfo u = userRepo.findById(userId)
                .orElseThrow(() -> new ServiceHandleException(404, "user not found", "用户不存在"));
        u.setPassword(encoder.encode((u.getEmail() != null ? u.getEmail() : "") + newPwd));
        userRepo.save(u);
        return Map.of("user_id", userId, "password_changed", true);
    }

    public static Map<String, Object> toBean(UserInfo u) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("user_id", u.getUserId());
        b.put("nick_name", u.getNickName());
        b.put("email", u.getEmail());
        b.put("phone", u.getPhone());
        b.put("enterprise_id", u.getEnterpriseId());
        b.put("is_active", u.getActive());
        b.put("sys_admin", u.getSysAdmin());
        b.put("create_time", u.getCreateTime());
        return b;
    }
}
