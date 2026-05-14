package cn.kuship.console.modules.application.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.ServiceDependencyOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.entity.TenantServiceRelation;
import cn.kuship.console.modules.application.repository.TenantServiceRelationRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量组件依赖服务 —— 迁移自 rainbond
 * {@code console/services/app_config/app_relation_service.py:add_service_dependencies}。
 *
 * <p>实现两阶段写策略：
 * <ol>
 *   <li>本地循环去重 + 循环依赖检测</li>
 *   <li>本地批量 INSERT {@code tenant_service_relation}</li>
 *   <li>调 region {@code addDependencies}（路径含 {@code dependencys} 历史拼写）</li>
 *   <li>region 失败 → {@code @Transactional} 自动回滚 step 2 的本地 INSERT</li>
 * </ol>
 */
@Service
public class AppDependencyBatchService {

    private final TenantServiceRepository serviceRepo;
    private final TenantServiceRelationRepository relationRepo;
    private final TenantsRepository tenantsRepo;
    private final ServiceDependencyOperations serviceDependencyOps;

    public AppDependencyBatchService(TenantServiceRepository serviceRepo,
                                     TenantServiceRelationRepository relationRepo,
                                     TenantsRepository tenantsRepo,
                                     ServiceDependencyOperations serviceDependencyOps) {
        this.serviceRepo = serviceRepo;
        this.relationRepo = relationRepo;
        this.tenantsRepo = tenantsRepo;
        this.serviceDependencyOps = serviceDependencyOps;
    }

    /**
     * 批量添加依赖。
     *
     * <p>rainbond 锚点：{@code app_relation_service.py:add_service_dependencies}
     *
     * @param teamName     团队名
     * @param serviceAlias 组件别名
     * @param body         请求体，含 {@code dep_service_ids}（List&lt;String&gt;）
     * @return region 响应 body（透传）
     */
    @Transactional
    public Map<String, Object> addBatch(String teamName, String serviceAlias, Map<String, Object> body) {
        // 1. 查找组件
        TenantService svc = requireService(teamName, serviceAlias);
        // 2. 查找团队（取 namespace 作为 tenant_id 注入 region body）
        Tenants tenant = requireTenant(teamName);

        // 3. 解析 dep_service_ids
        @SuppressWarnings("unchecked")
        List<String> depServiceIds = (List<String>) body.get("dep_service_ids");
        if (depServiceIds == null || depServiceIds.isEmpty()) {
            throw new ServiceHandleException(400, "dep_service_ids is required", "dep_service_ids 不能为空");
        }

        // 4. 循环去重 + 循环依赖检测，收集待 INSERT 的列表
        List<String> toInsert = new ArrayList<>();
        for (String depId : depServiceIds) {
            // 4.1 已存在跳过（与 rainbond 行为一致：不报错）
            if (relationRepo.findByServiceIdAndDepServiceId(svc.getServiceId(), depId).isPresent()) {
                continue;
            }
            // 4.2 循环依赖检测（A→B→A）
            checkCyclicDependency(svc.getServiceId(), depId, svc.getTenantId());
            toInsert.add(depId);
        }

        // 5. 本地批量 INSERT
        for (String depId : toInsert) {
            TenantServiceRelation rel = new TenantServiceRelation();
            rel.setTenantId(svc.getTenantId());
            rel.setServiceId(svc.getServiceId());
            rel.setDepServiceId(depId);
            rel.setDepOrder(0);
            relationRepo.save(rel);
        }

        // 6. 调 region（注入 tenant_id = tenant.namespace）
        Map<String, Object> regionBody = new LinkedHashMap<>(body);
        regionBody.put("dep_service_ids", depServiceIds); // 全量传给 region，region 端可能也去重
        regionBody.put("tenant_id", tenant.getNamespace());

        return serviceDependencyOps.addDependencies(
                svc.getServiceRegion(), teamName, serviceAlias, regionBody);
    }

    /**
     * 循环依赖检测（BFS）—— 检查 depId 是否已经（直接或间接）依赖 serviceId。
     *
     * <p>如果 depId→...→serviceId 路径存在，说明添加 serviceId→depId 会形成环。
     *
     * <p>rainbond 锚点：{@code app_relation_service.py:check_service_relation}
     */
    private void checkCyclicDependency(String serviceId, String depId, String tenantId) {
        // BFS：从 depId 出发，看能否到达 serviceId
        List<String> visited = new ArrayList<>();
        List<String> queue = new ArrayList<>();
        queue.add(depId);

        while (!queue.isEmpty()) {
            String current = queue.remove(0);
            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            // 找到 current 的所有依赖
            List<TenantServiceRelation> deps = relationRepo.findByServiceId(current);
            for (TenantServiceRelation rel : deps) {
                if (rel.getDepServiceId().equals(serviceId)) {
                    // depId 的依赖链最终指向 serviceId → 形成循环
                    throw new ServiceHandleException(400, "circular dependency",
                            "依赖关系不能形成循环");
                }
                queue.add(rel.getDepServiceId());
            }
        }
    }

    private TenantService requireService(String teamName, String serviceAlias) {
        Tenants tenant = requireTenant(teamName);
        return serviceRepo.findByTenantIdAndServiceAlias(tenant.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    private Tenants requireTenant(String teamName) {
        return tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }
}
