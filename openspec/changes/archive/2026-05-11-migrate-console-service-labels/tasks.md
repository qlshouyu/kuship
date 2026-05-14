# Tasks — migrate-console-service-labels

> **路线图**：母 [`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/) §4.4（P2 #4，4 method）

## 1. 接口与 Impl

- [x] 1.1 新建 `cn.kuship.console.modules.application.api.ServiceLabelOperations`，4 method 签名
- [x] 1.2 新建 `ServiceLabelOperationsImpl @Primary`，落地 4 method
- [x] 1.3 `listRegionLabels` 实现（GET + 解 `data.list`）
- [x] 1.4 `addServiceNodeLabel` 实现（POST + body）
- [x] 1.5 `deleteServiceNodeLabel` 实现（DELETE with body，Spring 6 RestClient 模式）
- [x] 1.6 `updateServiceStateLabel` 实现（PUT + body）

## 2. Entity 与 Repository

- [x] 2.1 新建 `TenantServiceLabel @Entity` 映射 `service_labels` 表（6 字段：ID / tenant_id / service_id / label_id / region / create_time）
- [x] 2.2 新建 `TenantServiceLabelRepository` extends `JpaRepository<TenantServiceLabel, Integer>`
  - `findByServiceId(String)`
  - `findByServiceIdAndLabelId(String, String)`
  - `deleteByServiceIdAndLabelId(String, String)`
- [x] 2.3 跑 `mvn -DskipTests package` 验证 entity schema validate 通过（不改 schema）

## 3. Controller

- [x] 3.1 新建 `AppLabelController` 类骨架，注入 4 个 bean（ops / serviceLabelRepo / tenantsRepo / serviceRepo）
- [x] 3.2 GET `/console/teams/{tn}/apps/{alias}/labels`：仅查本地，返回 label_id list
- [x] 3.3 POST `/console/teams/{tn}/apps/{alias}/labels`：`@Transactional`，先本地 INSERT 后调 region；失败回滚
- [x] 3.4 DELETE `/console/teams/{tn}/apps/{alias}/labels`：先 region DELETE 后本地删；region 404 兼容
- [x] 3.5 GET `/console/teams/{tn}/apps/{alias}/labels/available`：调 `listRegionLabels`

## 4. 单元测试

- [x] 4.1 `ServiceLabelOperationsImplTest`：4 method × 1 happy + 1 错误 = 8 用例
- [x] 4.2 测试 DELETE with body 的 URL + body 内容正确

## 5. 集成测试

- [x] 5.1 `ServiceLabelIntegrationTest @SpringBootTest`
- [x] 5.2 GET labels happy + 组件不存在 404
- [x] 5.3 POST labels happy（含本地 INSERT 验证）+ region 失败回滚验证 + label_ids 空 400
- [x] 5.4 DELETE labels happy（含本地 DELETE 验证）+ region 404 兼容仍删本地
- [x] 5.5 GET available labels happy + region 调用失败 fallback 空列表

## 6. 验证与归档

- [x] 6.1 `mvn -DskipTests package` 编译通过
- [x] 6.2 `mvn test -Dtest=ServiceLabelOperationsImplTest,ServiceLabelIntegrationTest` 全过
- [x] 6.3 既有 416+ 用例零回归
- [x] 6.4 联动验证（用户本地起 console + region + curl）：4 个 region URL 真实可调
- [x] 6.5 实施期探测结果回填到 design.md §8
- [x] 6.6 母路线图 §4.4 标 [x] + 加 4.4.3 实施落地条目
- [x] 6.7 `kuship-console/CLAUDE.md` 表格 P2 #4 状态从 ⏳ 改为 ✅
- [x] 6.8 `openspec archive migrate-console-service-labels --skip-specs`
