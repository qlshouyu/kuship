package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceLifecycleOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 4 个扩缩容端点：vertical / horizontal / scaling / extend_method。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class AppScalingController {

    private static final Set<String> EXTEND_METHODS = Set.of(
            "stateless_multiple", "stateful_singleton", "stateful_multiple", "job", "cronjob");

    private final ServiceLifecycleOperations lifecycle;
    private final TenantServiceRepository serviceRepo;
    private final RuntimeContextLoader loader;

    public AppScalingController(ServiceLifecycleOperations lifecycle,
                                  TenantServiceRepository serviceRepo,
                                  RuntimeContextLoader loader) {
        this.lifecycle = lifecycle;
        this.serviceRepo = serviceRepo;
        this.loader = loader;
    }

    @PostMapping(value = {"/vertical", "/vertical/"})
    @RequirePerm(PermCode.APP_OVERVIEW_TELESCOPIC)
    @Transactional
    public ApiResult vertical(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias,
                                @RequestBody Map<String, Object> body) {
        TenantService s = loader.requireService(teamName, alias);
        Integer cpu = parseInt(body.get("new_cpu"));
        Integer memory = parseInt(body.get("new_memory"));
        Integer gpu = parseInt(body.get("new_gpu"));
        if (cpu != null) s.setMinCpu(cpu);
        if (memory != null) s.setMinMemory(memory);
        if (gpu != null) s.setContainerGpu(gpu);
        s.setUpdateTime(LocalDateTime.now());
        serviceRepo.save(s);
        Map<String, Object> resp = lifecycle.verticalUpgrade(s.getServiceRegion(), teamName, alias, body);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @PostMapping(value = {"/horizontal", "/horizontal/"})
    @RequirePerm(PermCode.APP_OVERVIEW_TELESCOPIC)
    @Transactional
    public ApiResult horizontal(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias,
                                  @RequestBody Map<String, Object> body) {
        TenantService s = loader.requireService(teamName, alias);
        Integer node = parseInt(body.get("new_node"));
        if (node == null) {
            throw new ServiceHandleException(400, "missing new_node", "缺少 new_node 参数");
        }
        s.setMinNode(node);
        s.setUpdateTime(LocalDateTime.now());
        serviceRepo.save(s);
        Map<String, Object> resp = lifecycle.horizontalUpgrade(s.getServiceRegion(), teamName, alias, body);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @PostMapping(value = {"/scaling", "/scaling/"})
    @RequirePerm(PermCode.APP_OVERVIEW_TELESCOPIC)
    @Transactional
    public ApiResult scaling(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias,
                               @RequestBody Map<String, Object> body) {
        TenantService s = loader.requireService(teamName, alias);
        Integer cpu = parseInt(body.get("new_cpu"));
        Integer memory = parseInt(body.get("new_memory"));
        Integer gpu = parseInt(body.get("new_gpu"));
        Integer node = parseInt(body.get("new_node"));
        if (cpu != null) s.setMinCpu(cpu);
        if (memory != null) s.setMinMemory(memory);
        if (gpu != null) s.setContainerGpu(gpu);
        if (node != null) s.setMinNode(node);
        s.setUpdateTime(LocalDateTime.now());
        serviceRepo.save(s);
        // 复用 region vertical/horizontal 各一次（grctl 没有专属 scaling 子路径，此处先 vertical 后 horizontal）
        Map<String, Object> respV = lifecycle.verticalUpgrade(s.getServiceRegion(), teamName, alias, body);
        Map<String, Object> respH = lifecycle.horizontalUpgrade(s.getServiceRegion(), teamName, alias, body);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("vertical", respV);
        bean.put("horizontal", respH);
        return GeneralMessage.ok(bean);
    }

    @PutMapping(value = {"/extend_method", "/extend_method/"})
    @RequirePerm(PermCode.APP_OVERVIEW_TELESCOPIC)
    @Transactional
    public ApiResult extendMethod(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias,
                                    @RequestBody Map<String, Object> body) {
        TenantService s = loader.requireService(teamName, alias);
        Object v = body.get("extend_method");
        if (!(v instanceof String em) || !EXTEND_METHODS.contains(em)) {
            throw new ServiceHandleException(400, "invalid extend_method", "extend_method 必须为以下之一：" + EXTEND_METHODS);
        }
        s.setExtendMethod(em);
        s.setUpdateTime(LocalDateTime.now());
        serviceRepo.save(s);
        return GeneralMessage.ok(Map.of("extend_method", em));
    }

    private static Integer parseInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}
