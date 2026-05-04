package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.PermsInfo;
import cn.kuship.console.modules.account.repository.PermsInfoRepository;
import cn.kuship.console.modules.account.service.PermsInitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** {@code /console/perms}（公开） / {@code /console/init/perms}（默认要求认证）。 */
@RestController
@RequestMapping("/console")
public class PermsController {

    private final PermsInfoRepository permsRepo;
    private final PermsInitService permsInitService;

    public PermsController(PermsInfoRepository permsRepo, PermsInitService permsInitService) {
        this.permsRepo = permsRepo;
        this.permsInitService = permsInitService;
    }

    @GetMapping(value = {"/perms", "/perms/"})
    public ApiResult listPerms() {
        // 按 kind 分组：team / app / enterprise / ...，与 rainbond 输出形状一致
        Map<String, Map<String, Object>> grouped = new TreeMap<>();
        for (PermsInfo info : permsRepo.findAll()) {
            String kind = info.getKind() != null ? info.getKind() : "other";
            Map<String, Object> kindMap = grouped.computeIfAbsent(kind, k -> new LinkedHashMap<>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> perms =
                    (List<Map<String, Object>>) kindMap.computeIfAbsent("perms", k -> new java.util.ArrayList<>());
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", info.getName());
            p.put("desc", info.getDescription());
            p.put("code", info.getCode());
            perms.add(p);
        }
        Map<String, Object> bean = new LinkedHashMap<>(grouped);
        return GeneralMessage.ok(bean);
    }

    @PostMapping(value = {"/init/perms", "/init/perms/"})
    public ApiResult init() {
        int upserted = permsInitService.runInit();
        return GeneralMessage.ok(Map.of("upserted", upserted));
    }
}
