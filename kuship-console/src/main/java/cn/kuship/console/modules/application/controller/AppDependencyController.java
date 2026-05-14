package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceDependencyOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.dto.DependencyReq;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.entity.TenantServiceRelation;
import cn.kuship.console.modules.application.repository.TenantServiceRelationRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.application.service.AppDependencyBatchService;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /console/teams/{team_name}/apps/{service_alias}/dependency*}：依赖管理。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class AppDependencyController {

    private final TenantServiceRelationRepository relationRepo;
    private final TenantServiceRepository serviceRepo;
    private final ServiceDependencyOperations dependencyOperations;
    private final AppDependencyBatchService batchService;

    public AppDependencyController(TenantServiceRelationRepository relationRepo,
                                     TenantServiceRepository serviceRepo,
                                     ServiceDependencyOperations dependencyOperations,
                                     AppDependencyBatchService batchService) {
        this.relationRepo = relationRepo;
        this.serviceRepo = serviceRepo;
        this.dependencyOperations = dependencyOperations;
        this.batchService = batchService;
    }

    /**
     * 批量添加依赖 —— rainbond 锚点：{@code urls.py:614} {@code AppDependencyViewList POST}。
     *
     * <p>路径：{@code POST /console/teams/{team_name}/apps/{service_alias}/dependency-list}
     *
     * <p>body 格式：{@code {"dep_service_ids": ["id1", "id2", ...]}}
     */
    @PostMapping(value = {"/dependency-list", "/dependency-list/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult addBatch(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @RequestBody Map<String, Object> body) {
        Map<String, Object> result = batchService.addBatch(teamName, serviceAlias, body);
        return GeneralMessage.ok(result);
    }

    @GetMapping(value = {"/dependency-list", "/dependency-list/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult listDeps(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(serviceAlias);
        List<TenantServiceRelation> rels = relationRepo.findByServiceId(s.getServiceId());
        return GeneralMessage.okList(rels.stream().map(r -> serializeRel(r, true)).toList());
    }

    @GetMapping(value = {"/dependency-reverse", "/dependency-reverse/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult listReverseDeps(@PathVariable("team_name") String teamName,
                                        @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(serviceAlias);
        List<TenantServiceRelation> rels = relationRepo.findByDepServiceId(s.getServiceId());
        return GeneralMessage.okList(rels.stream().map(r -> serializeRel(r, false)).toList());
    }

    @PostMapping(value = {"/dependency", "/dependency/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult add(@PathVariable("team_name") String teamName,
                           @PathVariable("service_alias") String serviceAlias,
                           @RequestBody @Valid DependencyReq req) {
        TenantService s = requireService(serviceAlias);
        if (relationRepo.findByServiceIdAndDepServiceId(s.getServiceId(), req.depServiceId()).isPresent()) {
            throw new ServiceHandleException(400, "dependency already exists", "依赖已存在");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dep_service_id", req.depServiceId());
        body.put("dep_order", req.depOrder() != null ? req.depOrder() : 0);
        dependencyOperations.addDependency(s.getServiceRegion(), teamName, serviceAlias, body);
        TenantServiceRelation r = new TenantServiceRelation();
        r.setTenantId(s.getTenantId());
        r.setServiceId(s.getServiceId());
        r.setDepServiceId(req.depServiceId());
        r.setDepOrder(req.depOrder() != null ? req.depOrder() : 0);
        return GeneralMessage.ok(serializeRel(relationRepo.save(r), true));
    }

    @DeleteMapping(value = {"/dependency/{dep_service_id}", "/dependency/{dep_service_id}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult delete(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @PathVariable("dep_service_id") String depServiceId) {
        TenantService s = requireService(serviceAlias);
        if (relationRepo.findByServiceIdAndDepServiceId(s.getServiceId(), depServiceId).isEmpty()) {
            throw new ServiceHandleException(404, "dependency not found", "依赖不存在");
        }
        Map<String, Object> body = Map.of("dep_service_id", depServiceId);
        dependencyOperations.deleteDependency(s.getServiceRegion(), teamName, serviceAlias, body);
        relationRepo.deleteByServiceIdAndDepServiceId(s.getServiceId(), depServiceId);
        return GeneralMessage.ok();
    }

    private TenantService requireService(String serviceAlias) {
        return serviceRepo.findAll().stream()
                .filter(s -> serviceAlias.equals(s.getServiceAlias()))
                .findFirst()
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    private Map<String, Object> serializeRel(TenantServiceRelation r, boolean forward) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("relation_id", r.getId());
        m.put("service_id", r.getServiceId());
        m.put("dep_service_id", r.getDepServiceId());
        m.put("dep_order", r.getDepOrder());
        m.put("dep_service_type", r.getDepServiceType());
        m.put("direction", forward ? "depends_on" : "depended_by");
        return m;
    }
}
