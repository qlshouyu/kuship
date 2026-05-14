package cn.kuship.console.modules.application.integration;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.ServiceDependencyOperations;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.application.entity.TenantServiceRelation;
import cn.kuship.console.modules.application.repository.TenantServiceRelationRepository;
import cn.kuship.console.modules.application.service.AppDependencyBatchService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AppDependencyBatchService 集成测试。
 *
 * <p>测试场景：
 * <ul>
 *   <li>3 dep 全部新 → INSERT 3 行 + region 调用 1 次</li>
 *   <li>1 dep 已存在 → INSERT 2 行（去重），region 仍调用 1 次</li>
 *   <li>含循环依赖 → 抛 ServiceHandleException，本地 0 行 INSERT</li>
 *   <li>region 5xx 抛异常 → 事务回滚，本地 0 行</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppDependencyBatchIntegrationTest {

    // 固定 seed 数据，避免与其他测试冲突（所有 ID 严格 32 字符）
    private static final int USER_ID = 908801;
    private static final String ENT_ID  = "depbatchent088012345678901234567";  // 32 chars
    private static final String TEAM_ID = "depbatchteam08801234567890a12345";  // 32 chars
    private static final String TEAM_NAME = "dep-batch-team-0880";
    private static final String NAMESPACE = "dep-batch-ns-0880";
    private static final String SVC_ID  = "depbatchsvc0880abcdef12345678901";  // 32 chars
    private static final String SVC_ALIAS = "dep-batch-svc-0880";
    private static final String DEP1 = "depbatchdep108801234567890123456";     // 32 chars
    private static final String DEP2 = "depbatchdep208801234567890123456";     // 32 chars
    private static final String DEP3 = "depbatchdep308801234567890123456";     // 32 chars

    @Autowired JdbcTemplate jdbc;
    @Autowired AppDependencyBatchService batchService;
    @Autowired TenantServiceRelationRepository relationRepo;
    @Autowired LegacyPasswordEncoder encoder;

    // Mock region，不需要真实 region 服务器
    @MockitoBean ServiceDependencyOperations serviceDependencyOps;

    @BeforeAll
    void seed() {
        // 企业
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'DepBatch Ent', 'DepBatchEnt', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT_ID);
        // 用户
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "depbatch@kuship.local", "DepBatchAdmin",
                encoder.encode("depbatch@kuship.localpwd12345"), ENT_ID);
        // 团队（tenant_info）
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'DepBatch') ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM_NAME, USER_ID, NAMESPACE, ENT_ID);
        // 组件
        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, container_gpu, "
                + "min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, creater, protocol, "
                + "total_memory, is_service, namespace, volume_type, port_type, service_origin, tenant_service_group_id, "
                + "open_webhooks, server_type, is_upgrate, build_upgrade, service_name, k8s_component_name, update_time) "
                + "VALUES (?, ?, 'app', ?, 'DepBatchSvc', 'r1', 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, "
                + "256, 'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'assistant', 0, "
                + "0, 'tcp', 0, 0, ?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE service_region=VALUES(service_region)",
                SVC_ID, TEAM_ID, SVC_ALIAS, USER_ID, NAMESPACE, SVC_ALIAS, SVC_ALIAS);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM tenant_service_relation WHERE service_id = ? OR dep_service_id IN (?, ?, ?)",
                SVC_ID, DEP1, DEP2, DEP3);
        jdbc.update("DELETE FROM tenant_service WHERE service_id = ?", SVC_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT_ID);
    }

    private void cleanRelations() {
        jdbc.update("DELETE FROM tenant_service_relation WHERE service_id = ? OR dep_service_id IN (?, ?, ?)",
                SVC_ID, DEP1, DEP2, DEP3);
    }

    // ===== 场景 1：3 dep 全部新 =====

    @Test
    void addBatch_allNew_inserts3RowsAndCallsRegionOnce() {
        cleanRelations();

        when(serviceDependencyOps.addDependencies(anyString(), anyString(), anyString(), any()))
                .thenReturn(Map.of("result", "ok"));

        Map<String, Object> body = Map.of("dep_service_ids", List.of(DEP1, DEP2, DEP3));
        batchService.addBatch(TEAM_NAME, SVC_ALIAS, body);

        // 验证本地 INSERT 3 行
        List<TenantServiceRelation> rels = relationRepo.findByServiceId(SVC_ID);
        long count = rels.stream().filter(r -> List.of(DEP1, DEP2, DEP3).contains(r.getDepServiceId())).count();
        assertEquals(3, count);

        // 验证 region 调用仅 1 次
        verify(serviceDependencyOps, times(1)).addDependencies(anyString(), anyString(), anyString(), any());

        cleanRelations();
    }

    // ===== 场景 2：1 dep 已存在 → 跳过去重，INSERT 2 行 =====

    @Test
    void addBatch_oneAlreadyExists_inserts2RowsSkipsDup() {
        cleanRelations();

        // 预插入 DEP1（已存在）
        jdbc.update("INSERT INTO tenant_service_relation (tenant_id, service_id, dep_service_id, dep_order) VALUES (?, ?, ?, 0)",
                TEAM_ID, SVC_ID, DEP1);

        when(serviceDependencyOps.addDependencies(anyString(), anyString(), anyString(), any()))
                .thenReturn(Map.of("result", "ok"));

        Map<String, Object> body = Map.of("dep_service_ids", List.of(DEP1, DEP2, DEP3));
        batchService.addBatch(TEAM_NAME, SVC_ALIAS, body);

        // DEP1 已存在跳过，只新增 DEP2 + DEP3 共 2 行（加上预插入总共 3 行）
        List<TenantServiceRelation> rels = relationRepo.findByServiceId(SVC_ID);
        long count = rels.stream().filter(r -> List.of(DEP1, DEP2, DEP3).contains(r.getDepServiceId())).count();
        assertEquals(3, count); // DEP1（预插入）+ DEP2 + DEP3

        // region 仍调用 1 次
        verify(serviceDependencyOps, atLeast(1)).addDependencies(anyString(), anyString(), anyString(), any());

        cleanRelations();
    }

    // ===== 场景 3：含循环依赖 → 抛异常，0 行 INSERT =====

    @Test
    void addBatch_circularDependency_throwsAndNoInsert() {
        cleanRelations();

        // 预建 SVC_ID → DEP1 关系
        jdbc.update("INSERT INTO tenant_service_relation (tenant_id, service_id, dep_service_id, dep_order) VALUES (?, ?, ?, 0)",
                TEAM_ID, DEP1, SVC_ID);

        // 尝试让 SVC_ID 依赖 DEP1（会形成循环：SVC_ID→DEP1→SVC_ID）
        Map<String, Object> body = Map.of("dep_service_ids", List.of(DEP1));

        ServiceHandleException ex = assertThrows(ServiceHandleException.class,
                () -> batchService.addBatch(TEAM_NAME, SVC_ALIAS, body));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("circular"));

        // 本地无新增（循环检测在 INSERT 之前，事务也回滚了）
        List<TenantServiceRelation> rels = relationRepo.findByServiceId(SVC_ID);
        assertEquals(0, rels.size());

        // region 不应该被调用
        verify(serviceDependencyOps, never()).addDependencies(anyString(), anyString(), anyString(), any());

        cleanRelations();
        // 清理预建关系
        jdbc.update("DELETE FROM tenant_service_relation WHERE service_id = ? AND dep_service_id = ?", DEP1, SVC_ID);
    }

    // ===== 场景 4：region 5xx → 事务回滚，本地 0 行 =====

    @Test
    void addBatch_region5xx_rollsBackLocalInserts() {
        cleanRelations();

        // region 抛异常模拟 5xx
        when(serviceDependencyOps.addDependencies(anyString(), anyString(), anyString(), any()))
                .thenThrow(new cn.kuship.console.infrastructure.region.exception.RegionApiException(
                        "service_dependency",
                        "/v2/tenants/dep-batch-ns-0880/services/dep-batch-svc-0880/dependencys",
                        "POST", 500, 500, "internal server error", "集群内部错误", Map.of(), null));

        Map<String, Object> body = Map.of("dep_service_ids", List.of(DEP1, DEP2));

        assertThrows(cn.kuship.console.infrastructure.region.exception.RegionApiException.class,
                () -> batchService.addBatch(TEAM_NAME, SVC_ALIAS, body));

        // 事务回滚，本地 0 行
        List<TenantServiceRelation> rels = relationRepo.findByServiceId(SVC_ID);
        long count = rels.stream().filter(r -> List.of(DEP1, DEP2).contains(r.getDepServiceId())).count();
        assertEquals(0, count);

        cleanRelations();
    }
}
