package cn.kuship.console.contract;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.response.SkipResponseWrapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 测试用 controller，仅在 {@code contract-test} profile 启用，覆盖契约层各种返回类型与异常分支。
 *
 * <p>挂在 {@code /console/_contract/**} 路径下，避免与未来业务路径冲突。
 */
@RestController
@RequestMapping("/console/_contract")
@Profile("contract-test")
public class ContractDemoController {

    public record User(Long id, String name) {
    }

    public record CreateUserReq(@NotBlank String name, @Min(1) int age) {
    }

    @GetMapping("/pojo")
    public User pojo() {
        return new User(42L, "alice");
    }

    @GetMapping("/list")
    public List<User> list() {
        return List.of(new User(1L, "a"), new User(2L, "b"));
    }

    @GetMapping("/page")
    public Page<User> page(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(name = "page_size", defaultValue = "2") int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("invalid page params");
        }
        List<User> all = List.of(
                new User(1L, "a"), new User(2L, "b"), new User(3L, "c"),
                new User(4L, "d"), new User(5L, "e"));
        int from = Math.min((page - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page - 1, pageSize), all.size());
    }

    @GetMapping("/api-result")
    public ApiResult apiResult() {
        return GeneralMessage.ok(Map.of("custom", "yes"));
    }

    // String 返回值不被自动包装（advice 已显式排除 String 类型，业务需自行包装）。
    // 此用例保留是为了证明 String 不会触发包装错误。
    @GetMapping(value = "/string", produces = MediaType.TEXT_PLAIN_VALUE)
    public String stringValue() {
        return "hello";
    }

    @GetMapping("/skip")
    @SkipResponseWrapper
    public Map<String, Object> skip() {
        return Map.of("raw", true);
    }

    @GetMapping("/throw-service")
    public User throwService() {
        throw new ServiceHandleException(404, "team not found", "团队不存在");
    }

    @GetMapping("/throw-runtime")
    public User throwRuntime() {
        throw new RuntimeException("boom");
    }

    @PostMapping("/validate")
    public User validate(@Valid @RequestBody CreateUserReq req) {
        return new User(1L, req.name());
    }

    @GetMapping("/teams/{team_name}/regions/{region_name}/echo")
    public Map<String, Object> echoTenant(@PathVariable("team_name") String teamName,
                                          @PathVariable("region_name") String regionName) {
        return Map.of("team_name", teamName, "region_name", regionName);
    }
}
