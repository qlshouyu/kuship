package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.account.dto.AccessTokenDto;
import cn.kuship.console.modules.account.dto.AccessTokenReq;
import cn.kuship.console.modules.account.entity.UserAccessKey;
import cn.kuship.console.modules.account.repository.UserAccessKeyRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /console/users/access-token}。 */
@RestController
@RequestMapping("/console/users/access-token")
public class UserAccessTokenController {

    private final RequestContext requestContext;
    private final UserAccessKeyRepository repo;

    public UserAccessTokenController(RequestContext requestContext, UserAccessKeyRepository repo) {
        this.requestContext = requestContext;
        this.repo = repo;
    }

    @GetMapping(value = {"", "/"})
    public ApiResult list() {
        Integer userId = requireUser();
        List<Map<String, Object>> tokens = repo.findByUserId(userId).stream()
                .map(AccessTokenDto::fromMasked)
                .map(this::asMap)
                .toList();
        return GeneralMessage.okList(tokens);
    }

    @PostMapping(value = {"", "/"})
    public ApiResult create(@RequestBody @Valid AccessTokenReq req) {
        Integer userId = requireUser();
        if (repo.findByUserIdAndNote(userId, req.note()).isPresent()) {
            throw new ServiceHandleException(400, "note already exists", "该名称的 token 已存在");
        }
        UserAccessKey k = new UserAccessKey();
        k.setNote(req.note());
        k.setUserId(userId);
        k.setAccessKey(UuidGenerator.makeUuid());
        k.setExpireTime(parseExpire(req.expire()));
        UserAccessKey saved = repo.save(k);
        return GeneralMessage.ok(asMap(AccessTokenDto.fromPlain(saved)));
    }

    @DeleteMapping(value = {"/{id}", "/{id}/"})
    public ApiResult delete(@PathVariable("id") Integer id) {
        Integer userId = requireUser();
        UserAccessKey k = repo.findById(id)
                .orElseThrow(() -> new ServiceHandleException(404, "token not found", "凭证不存在"));
        if (!userId.equals(k.getUserId())) {
            throw new ServiceHandleException(403, "not your token", "无权删除该凭证");
        }
        repo.delete(k);
        return GeneralMessage.ok();
    }

    private Integer requireUser() {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        return userId;
    }

    /** "30d" / "12h" / "" / null → epoch seconds（null 表示永不过期）。 */
    private static Integer parseExpire(String expr) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        long seconds;
        char unit = expr.charAt(expr.length() - 1);
        try {
            long n = Long.parseLong(expr.substring(0, expr.length() - 1));
            seconds = switch (unit) {
                case 'd', 'D' -> n * 86400L;
                case 'h', 'H' -> n * 3600L;
                case 'm', 'M' -> n * 60L;
                default -> Long.parseLong(expr);
            };
        } catch (NumberFormatException ex) {
            throw new ServiceHandleException(400, "invalid expire format", "过期时间格式错误");
        }
        long now = System.currentTimeMillis() / 1000L;
        long target = now + seconds;
        if (target > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) target;
    }

    private Map<String, Object> asMap(AccessTokenDto dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dto.id());
        m.put("note", dto.note());
        m.put("user_id", dto.userId());
        m.put("access_key", dto.accessKey());
        m.put("expire_time", dto.expireTime());
        return m;
    }
}
