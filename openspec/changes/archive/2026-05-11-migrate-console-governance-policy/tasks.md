# Tasks — migrate-console-governance-policy

> **路线图**：母 [`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/) §4.2（P2 #2，9 method 实际，路线图估计 12）

## 1. Governance Mode 域

### 1.1 接口与 Impl

- [x] 1.1.1 新建 `cn.kuship.console.modules.application.governance.api.GovernanceModeOperations`，5 method
- [x] 1.1.2 新建 `GovernanceModeOperationsImpl @Primary`
- [x] 1.1.3 实现 5 method（list / check / create-cr / update-cr / delete-cr）

### 1.2 Controller

- [x] 1.2.1 新建 `AppGovernanceModeController`
- [x] 1.2.2 6 个 endpoint：list / put-mode / post-cr / put-cr / delete-cr / check
- [x] 1.2.3 PUT mode 业务流程：先 check 后切换；本地写 `app.governance_mode`
- [x] 1.2.4 加 `@RequirePerm(PermCode.APP_OVERVIEW)`

## 2. K8s Attribute 域

### 2.1 接口与 Impl

- [x] 2.1.1 新建 `cn.kuship.console.modules.application.k8sattr.api.K8sAttributeOperations`，4 method
- [x] 2.1.2 新建 `K8sAttributeOperationsImpl @Primary`
- [x] 2.1.3 实现 4 method（含 GET with body / DELETE with body 模式）

### 2.2 Entity 与 Repository

- [x] 2.2.1 新建 `ComponentK8sAttribute @Entity` 映射 `component_k8s_attributes` 表（apply 期用 `docker exec mysql DESC` 验真实 schema 后落字段集）
- [x] 2.2.2 新建 `ComponentK8sAttributeRepository`：`findByComponentId(String)` / `findByComponentIdAndName(String, String)` / `deleteByComponentIdAndName(String, String)`

### 2.3 Controller

- [x] 2.3.1 新建 `ComponentK8sAttributeController`
- [x] 2.3.2 5 endpoint：list / post / get-by-name / put / delete
- [x] 2.3.3 写入路径 `@Transactional`：本地写 → region 写；region 失败回滚
- [x] 2.3.4 加 `@RequirePerm(PermCode.APP_OVERVIEW_OTHER_SETTING)`

## 3. 单元测试

- [x] 3.1 `GovernanceModeOperationsImplTest`：5 × 2 = 10 用例
- [x] 3.2 `K8sAttributeOperationsImplTest`：4 × 2 = 8 用例（含 GET with body 验证）

## 4. 集成测试

- [x] 4.1 `GovernancePolicyIntegrationTest @SpringBootTest`
- [x] 4.2 governance：list / put mode 含 check 失败分支 / cr CRUD = ~10 用例
- [x] 4.3 k8s_attribute：CRUD × happy/error + 本地写 + region 失败回滚 = ~6 用例

## 5. 验证与归档

- [x] 5.1 `mvn -DskipTests package` 编译通过
- [x] 5.2 `mvn test -Dtest=GovernanceModeOperationsImplTest,K8sAttributeOperationsImplTest,GovernancePolicyIntegrationTest` 全过
- [x] 5.3 既有 416+ 用例零回归
- [x] 5.4 联动验证：9 个 region URL 真实可调；governance check 412 / k8s-attribute GET with body 真实行为
- [x] 5.5 实施期探测结果回填到 design.md §9
- [x] 5.6 母路线图 §4.2 标 [x] + 加 4.2.3 实施落地条目 + 偏差原因摘要
- [x] 5.7 `kuship-console/CLAUDE.md` 表格 P2 #2 状态从 ⏳ 改为 ✅
- [x] 5.8 `openspec archive migrate-console-governance-policy --skip-specs`
