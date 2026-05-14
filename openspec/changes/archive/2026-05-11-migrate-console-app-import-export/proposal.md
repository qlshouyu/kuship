# Proposal — migrate-console-app-import-export

## Why

rainbond `views/center_pool/app_import.py` + `app_export.py` + `views/yaml_resource.py` 提供应用模板（rainbond-app-yaml）和 k8s yaml 资源的 import/export 能力，共 **22 个 region method**，是 P2 段最大的子 change。kuship-console 完全未迁移，UI 上"应用市场 → 导入应用"+"应用 → 导出 yaml"+"k8s yaml 直接导入"页打不开。

本 change 落地 22 个 region method + 对应 controller 端点，覆盖三类工作流：

1. **App import/export**（19 method）：rainbond app_template tar.gz 包的导出 / 导入 / 上传文件 / 异步事件管理
2. **Yaml resource**（3 method）：原生 k8s yaml 资源的解析 / 详情 / 导入

## What Changes

### 接口分层（业务自治非 14 骨架）

- `AppExportOperations`（2 method）：归属 `modules/appmarket/share/export/api/`
- `AppImportOperations`（10 method）：归属 `modules/appmarket/share/import/api/`
- `AppUploadOperations`（4 method）：归属 `modules/appmarket/share/upload/api/`
- `LoadTarImageOperations`（1 method）：归属 `modules/appmarket/share/upload/api/`
- `HelmChartImportOperations`（2 method）：归属 `modules/appmarket/helm/api/`（与已落地 helm 段对齐）
- `YamlResourceOperations`（3 method）：归属 `modules/region/yaml/api/`

### Impl 落地

每个接口对应 `@Primary` 实现类。

### Entity 落地

- `AppImportRecord`（`app_import_record` 表）：导入异步事件追踪
- `AppExportRecord`（`app_export_record` 表）：导出异步事件追踪

### Controller 落地

- `CenterAppExportController`（POST 触发 / GET 状态）
- `EnterpriseAppImportController`（POST init / 整个 import 工作流）
- `CenterAppImportController`（GET 状态 / DELETE / file dir CRUD）
- `AppUploadController`（upload events CRUD）
- `YamlResourceController`（resource-name / resource-detailed / resource import）
- `UploadHelmChartValueResourceController`（chart resource 导入）

### 测试

- 6 个单测类，每类 ~3-5 用例 = ~25 用例
- 4 个集成测试类，每类 ~5-7 用例 = ~25 用例
- 共 ~50 用例

## Impact

- **能力**：`kuship-console-app`
- **Specs**：ADDED ~6 段 Requirement（按子域分组）+ 1 段路线图位置
- **影响范围**：`cn.kuship.console.modules.appmarket.share.{export,import,upload}.*` + `modules/region/yaml/*` + `modules/appmarket/helm/*`（部分扩展）；不动 14 接口骨架
- **依赖**：无硬依赖；与 P1 #2 `migrate-console-app-share` 独立（**share 是发布到 marketplace；本 change 是导入导出 yaml 包**，rainbond 决策已确认两者解耦）
- **不实现**：
  - SHALL NOT 落地完整的 app_template 序列化逻辑（rainbond `services/app_import_and_export_service.py` 700+ 行复杂转换，留 hardening）
  - SHALL NOT 落地多文件 multipart 上传的 storage backend 适配（仅透传 region；具体存储由 region 后端决定）
  - SHALL NOT 落地导入失败的回滚（rainbond 也是事件重试模型，不做事务回滚）
  - SHALL NOT 落地 backup_upload（已被 backup 域 P2 #5 处理）

## 路线位置

- 母路线图：[`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/)
- 优先级：**P2 #1**（最大子 change）
- 估计 method 数：22（与路线图一致）
- rainbond 参照：
  - `console/views/center_pool/app_export.py`（export 流程）
  - `console/views/center_pool/app_import.py`（import 流程，~600 行）
  - `console/views/yaml_resource.py`（k8s yaml 资源）
  - `console/services/app_import_and_export_service.py`（业务编排）
  - `www/apiclient/regionapi.py:1538-1670`（19 method）+ `:2039-2065`（3 method）+ `:704`（chart resource 1 method，归 helm）
- 与其它 P2 子 change 的关系：完全独立，可与 P2 #2/#3/#4/#5 并行；规模最大建议**最后实施**或**单独安排迭代**
