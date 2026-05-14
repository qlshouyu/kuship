## Why

`add-gray-release`（2026-05-04 归档）已落地灰度发布的"控制平面骨架"：`gray_release_record` 实体、`GrayReleaseStatus` 状态机、`ApisixRouteWeightUpdater`（通过 `GatewayOperations.apiGatewayProxy` 调 rainbond-go core 改 ApisixRoute 权重）、5 个 OpenAPI / console 端点。但 `GrayReleaseTemplateInstaller.installGrayServiceGroup` 至今仍是 **stub**：仅生成合成 service_id / upgrade_group_id，**不**触达 region 端的"灰度 service group 创建 / 状态推进 / 卸载"链路（rainbond Python `regionapi.py:create_app_gray_release / update_app_gray_release / operate_app_gray_release` 三段）。

`migrate-region-coverage-roadmap` 把这块归为 **P1 #5**（"等 app-install 子 change 配合"），明确：本子 change 不解决"模板 → 真实 service group"的本地写问题（依赖未落地的 `migrate-console-app-install`），只**先行落地 region 调用接口**，让 `GrayReleaseTemplateInstaller` 在 region 通信侧不再 stub —— region 端会真实进入"创建灰度 service group / 推进 ratio / operate（启停 / 回滚）"的状态机；待 `migrate-console-app-install` 子 change 接管"本地批量 INSERT tenant_service + service_group_relation"后再二次扩 stub 调真实 install service。

清桩本身工作量不大（3 region method、纯透传），但它解锁两件事：

1. UI 创建灰度后，rainbond-go core 真实进入灰度 deployment 创建链路（不再仅靠 ApisixRoute 权重切换在两个空后端间循环），勾连 add-gray-release 已落地的 `ApisixRouteWeightUpdater` + 真实 service group endpoint
2. `add-gray-release` 决策"模板实例化 stub 留作 hardening"的债项闭合一半（region 侧债清零；本地侧仍标记 stub 直到 app-install 子 change 落地）

`migrate-region-coverage-roadmap` 把这块归为 **P1 #5**（3 method，灰度发布收尾）。本 change 完整迁移 rainbond `regionapi.py:2937 create_app_gray_release` / `2945 update_app_gray_release` / `2953 operate_app_gray_release` 3 段 region API 调用；`get_app_gray_release` 是只读查询，本 change **不**新增 region method（决策 4），由 `GrayReleaseInfoController` 直接读本地 `GrayReleaseRecord` 透传应答。

## What Changes

### 新增 `GrayReleaseOperations` 接口（3 method）

按"非 14 核心 Operations"约定（路线图决策 4），新接口落到 `cn.kuship.console.modules.grayrelease.api`，与已有 `ApisixRouteWeightUpdater`（service 包下 `@Component`，承担"网关权重切换"职责）形成清晰分层：

- `GrayReleaseOperations`（**本 change**）—— 入口 region 调用，完成 "在 rainbond-go core 创建 / 更新 / operate 灰度 service group" 的命令侧
- `ApisixRouteWeightUpdater`（**add-gray-release 已落地**）—— 走 `GatewayOperations.apiGatewayProxy` 通用代理改 ApisixRoute 权重，承担"流量切换"职责

3 个 method 1:1 透传 region API：

- `createAppGrayRelease(regionName, tenantName, regionAppId, body)` → POST `/v2/tenants/{namespace}/apps/{region_app_id}/gray_release` —— rainbond 锚点 `regionapi.py:2937-2943 create_app_gray_release`
- `updateAppGrayRelease(regionName, tenantName, regionAppId, body)` → PUT `/v2/tenants/{namespace}/apps/{region_app_id}/gray_release` —— rainbond 锚点 `regionapi.py:2945-2951 update_app_gray_release`
- `operateAppGrayRelease(regionName, tenantName, regionAppId, namespace, operationMethod)` → PUT `/v2/tenants/{namespace}/apps/{region_app_id}/operate_gray_release?namespace={namespace}&app_id={region_app_id}&operation_method={operationMethod}` —— rainbond 锚点 `regionapi.py:2953-2960 operate_app_gray_release`

`tenant_name` 路径段统一替换为 `Tenants.namespace`（缺失时回退 `tenant_name`），与 `migrate-console-cluster-extras.getResources` / `migrate-console-helm-release` 同套路（rainbond Python `tenant_region.region_tenant_name`）。

### 落地 `GrayReleaseOperationsImpl @Primary @Service`

包路径 `cn.kuship.console.modules.grayrelease.api`，与接口同包；走 `RegionClientFactory.getClient(enterpriseId, regionName)` 拿到 mTLS RestClient，错误抛 `RegionApiException` 由 `GlobalExceptionHandler` 自动映射。

### 改造 `GrayReleaseTemplateInstaller` 去 region 调用 stub

**只改 region 通信部分**：

- `installGrayServiceGroup` 不再仅生成合成 id，改为：调 `GrayReleaseOperations.createAppGrayRelease` 把 template / version / 期望的灰度 service 描述发给 region；解析 region 响应的 bean 里的 `original_service_id / gray_service_id / original_upgrade_group_id / gray_upgrade_group_id` 等字段回填给 `GrayReleaseService.createGrayRelease` 流程
- `uninstallGrayServiceGroup` 改为：调 `GrayReleaseOperations.operateAppGrayRelease(... operationMethod="rollback")` 通知 region 卸载灰度 service group
- **本地 service_group / tenant_service 的批量 INSERT 仍走 stub**（依赖 `migrate-console-app-install` 子 change），WARN 日志保留：`[GrayRelease][stub] local service_group write bypassed; pending migrate-console-app-install`

### Controller 接线（仅 1 处变化）

无新建 controller 类。`GrayReleaseInfoController.grayReleaseInfo` / `listAppGrayReleases` 已存在且只读本地，不调 region 端 `get_app_gray_release`（决策 4 —— region 侧 GET 只是 K8s 实时状态查询，本 change 不暴露 console URL）。`updateGrayRatio` 在 ratio 变更后追加调 `GrayReleaseOperations.updateAppGrayRelease` 把新 ratio 同步给 region 端（与 ApisixRoute 权重切换互补；rainbond Python 端 ratio 更新会同时更新 region 灰度对象的 desired_replicas / strategy 字段）。

`add-grayrelease-promote-endpoint`（已记入路线图 hardening）后续才暴露 `operateAppGrayRelease(... operation_method="promote")` 给 controller；本 change 仅为 `rollback` 一种 action 准备 region 调用通路。

### 不在本 change 内（明确推迟）

- 本地 service_group / tenant_service / service_group_relation 批量 INSERT（依赖未迁移的 `AppInstallService`）→ `migrate-console-app-install` 子 change 接管后再扩 `GrayReleaseTemplateInstaller`
- operate 的 `start` / `stop` / `promote` 三个子动作 → 本 change 仅打通 `rollback` 通路；其余子动作的 controller endpoint 由独立 `add-grayrelease-promote-endpoint` / `add-grayrelease-lifecycle-endpoints` 提案承载
- region GET `/v2/tenants/{ns}/apps/{aid}/gray_release` 真实状态查询 → 决策 4 推迟（UI 当前仅按本地 record.status 判定按钮态，足够覆盖 P1 场景）
- ApisixRoute 权重切换 → 已在 `add-gray-release` 落地，本 change 不重复
- 跨实例并发 active 创建去重（Redis 锁）→ `add-distributed-grayrelease-coordination` 独立 hardening
- DB 唯一索引 `(tenant_id, app_id, status)` → `enforce-grayrelease-uniqueness` 独立 hardening

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "灰度发布 region 通信收尾"。覆盖 3 个 region method 的 URL 路径与响应透传约束、`GrayReleaseTemplateInstaller` region 调用 stub 移除范围、与 `add-gray-release` ApisixRouteWeightUpdater 协作的职责边界、与未来 `migrate-console-app-install` 子 change 的本地写边界（仍 stub 部分明确列出）。

## Impact

- **代码新增**：
  - region API 接口：1 个（`modules/grayrelease/api/GrayReleaseOperations.java`，3 method）
  - region API 实现：1 个（`modules/grayrelease/api/GrayReleaseOperationsImpl.java`，`@Primary @Service`）
  - 单测：`GrayReleaseOperationsImplTest`（3 method × 1 happy + 1 region 异常透传 + namespace fallback）
  - 集成测试：`GrayReleaseFinalizeIntegrationTest`（≥ 4 用例：create happy / update ratio happy / rollback happy / region 异常透传，全部 `@MockitoBean GrayReleaseOperations`）
- **代码修改**：
  - `GrayReleaseTemplateInstaller` —— 注入 `GrayReleaseOperations` + `TenantsRepository`；`installGrayServiceGroup` / `uninstallGrayServiceGroup` 接 region 调用，保留本地写 stub
  - `GrayReleaseService.updateGrayRatio` —— ratio 变更后追加 `GrayReleaseOperations.updateAppGrayRelease` 调用（事务内，region 失败回滚）
- **数据库**：无变更（复用 `gray_release_record` 表，schema 由 rainbond migration 0002 拥有）
- **依赖**：不引入新 maven 依赖
- **跨 change 衔接**：
  - 与 `add-gray-release`（已归档）：本 change 不动 `ApisixRouteWeightUpdater` / `GrayReleaseService` 状态机 / `GrayReleaseRecord` entity；只补 region 通信 + 改写 `GrayReleaseTemplateInstaller` 的 region 侧 stub
  - 与未来 `migrate-console-app-install`：本地 service_group / tenant_service 批量 INSERT 仍由后者承担；本 change 落地后，`GrayReleaseTemplateInstaller` 留下的 `[GrayRelease][stub] local service_group write bypassed` WARN 日志会随 `migrate-console-app-install` 接管而消失
  - 与未来 `add-grayrelease-promote-endpoint` / `add-grayrelease-lifecycle-endpoints`：本 change 落地后，新增端点直接复用 `operateAppGrayRelease(... operationMethod=...)`，URL 拼装零重复
- **不影响**：rainbond-console（仍可独立跑 7070）、其他已迁移 change、kuship-ui 既有 OpenAPI / console 端点契约
- **路径变量**：路径中 `{tenant_name}`（实际渲染为 namespace）/ `{region_app_id}`（与 rainbond 保持下划线命名，路径段为整数）/ query 参数 `namespace` / `app_id` / `operation_method` 全部 snake_case
- **路线图反向同步**：归档时把 `migrate-region-coverage-roadmap` 的 Requirement 表中 "migrate-console-grayrelease-finalize" 行标注为已完成；同步路线图 design.md 决策 5 "推进顺序" 图中的 P1 #5 标记
