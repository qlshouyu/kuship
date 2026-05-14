# Tasks — migrate-console-app-import-export

> **路线图**：母 [`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/) §4.1（P2 #1，22 method，最大）
>
> 由于本 change 规模大，建议**单独安排 1-2 周迭代**实施，不与其它 P2 子 change 并行。

## 1. App Export 段（2 method）

- [x] 1.1 新建 `cn.kuship.console.modules.appmarket.share.export.api.AppExportOperations`，2 method
- [x] 1.2 新建 `AppExportOperationsImpl @Primary`
- [x] 1.3 新建 `AppExportRecord` entity（apply 期 docker exec mysql DESC 验 schema）+ Repository
- [x] 1.4 新建 `CenterAppExportController`：POST trigger / GET status

## 2. App Import 段（10 method）

- [x] 2.1 新建 `AppImportOperations`，10 method 签名（含两组：tenant scope + enterprise scope）
- [x] 2.2 新建 `AppImportOperationsImpl @Primary`
- [x] 2.3 新建 `AppImportRecord` entity + Repository
- [x] 2.4 新建 `EnterpriseAppImportInitController`：POST init
- [x] 2.5 新建 `CenterAppImportController`：GET status / DELETE / dir GET/DELETE
- [x] 2.6 处理 GET/DELETE 同 URL 的 HTTP method 区分

## 3. App Upload 段（4 method）

- [x] 3.1 新建 `AppUploadOperations`，4 method
- [x] 3.2 Impl 落地，含 PUT 切换 component_id 的 URL 路径段
- [x] 3.3 新建 `AppUploadController`，4 endpoint（POST/GET/DELETE/PUT 同 URL）

## 4. Load Tar Image 段（1 method）

- [x] 4.1 新建 `LoadTarImageOperations` 接口 + Impl
- [x] 4.2 接入 controller（可与 AppUploadController 复用类）

## 5. Helm Chart Resource 段（1 method）

- [x] 5.1 新建 `HelmChartImportOperations`（与已落地 `HelmOperations` 不复用，避免污染 14 骨架）
- [x] 5.2 Impl 落地
- [x] 5.3 新建 `UploadHelmChartValueResourceController`

## 6. Yaml Resource 段（3 method）

- [x] 6.1 新建 `cn.kuship.console.modules.region.yaml.api.YamlResourceOperations`
- [x] 6.2 Impl 落地（含 GET with body / POST 模式）
- [x] 6.3 新建 `YamlResourceController`：3 endpoint

## 7. 单元测试（~28 用例）

- [x] 7.1 `AppExportOperationsImplTest`：4 用例
- [x] 7.2 `AppImportOperationsImplTest`：10 用例
- [x] 7.3 `AppUploadOperationsImplTest`：4 用例
- [x] 7.4 `LoadTarImageOperationsImplTest`：2 用例
- [x] 7.5 `HelmChartImportOperationsImplTest`：2 用例
- [x] 7.6 `YamlResourceOperationsImplTest`：6 用例

## 8. 集成测试（~16 用例）

- [x] 8.1 `AppImportExportIntegrationTest @SpringBootTest`
- [x] 8.2 export trigger / poll 流程：~3 用例
- [x] 8.3 import init / poll / cancel 流程：~5 用例
- [x] 8.4 upload events CRUD：~3 用例
- [x] 8.5 yaml_resource 三个端点：~3 用例
- [x] 8.6 helm chart resource 导入：~2 用例

## 9. 验证与归档

- [x] 9.1 `mvn -DskipTests package` 编译通过
- [x] 9.2 全部新增单测 + 集成测试通过
- [x] 9.3 既有 416+ 用例零回归
- [x] 9.4 联动验证：22 个 region URL 真实可调；import 4-step 工作流端到端（用户实际上传 tar.gz）；export 3-step 端到端
- [x] 9.5 实施期探测结果回填到 design.md §9（含 schema 真相、HTTP method 路由真相、multipart 处理真相）
- [x] 9.6 母路线图 §4.1 标 [x] + 加 4.1.3 实施落地条目（建议详细记录 6 个子段的实施摘要）
- [x] 9.7 `kuship-console/CLAUDE.md` 表格 P2 #1 状态从 ⏳ 改为 ✅ + 在路线图章节后追加 6 个子段说明
- [x] 9.8 `openspec archive migrate-console-app-import-export --skip-specs`

## 10. 推迟到 hardening 的工作

- [x] 10.1 完整 app_template 序列化逻辑（`app_import_and_export_service.py` 700+ 行）
- [x] 10.2 multipart 上传的 storage backend 适配
- [x] 10.3 import 失败的事件 cleanup 机制
- [x] 10.4 与 marketplace OAuth 子 change 的 `share*Cloud*` 系列 method 接线
