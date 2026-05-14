## Why

`migrate-console-application-core` 落地了 `ServiceDependencyOperations.addDependency` / `deleteDependency` 单依赖管理，但 3 个 default unsupported 仍未实现：

- `addDependencies(rn, tn, alias, body)` —— **批量**添加依赖（rainbond `add_service_dependencys`，`s` 结尾），UI 上 helm 模板批量安装、应用市场一键安装、组件迁移导入时会调用
- `addVolumeDependency(rn, tn, alias, body)` —— 旧版（5.0 之前）持久化挂载依赖 region API
- `deleteVolumeDependency(rn, tn, alias, body)` —— 旧版删除

UI 表现：
- "组件添加依赖"批量勾选时只能一个一个加（POST `/dependency-list` 实际 405）
- 老版本 helm chart / 应用市场模板里仍引用旧版 volume-dependency API，安装链路在某些条件下走旧 region method 时直接抛 unsupported 异常

`migrate-region-coverage-roadmap` 把这块归为 **P0 #7**（3 method）。本 change 完整迁移 rainbond `console/views/app_config/app_dependency.py:AppDependencyViewList POST` + `console/services/app_config/app_relation_service.py:add_service_dependencies` + `regionapi.py:242-265,811-832` 共 3 个 region API。

## What Changes

### 实现 ServiceDependencyOperations 3 个 default override

落地 `modules/application/api/ServiceDependencyOperationsImpl.java`（已 `@Primary`）的 3 method：

| method                                          | HTTP   | 路径                                                                 |
|-------------------------------------------------|--------|----------------------------------------------------------------------|
| addDependencies(rn, tn, alias, body)            | POST   | `/v2/tenants/{namespace}/services/{alias}/dependencys`               |
| addVolumeDependency(rn, tn, alias, body)        | POST   | `/v2/tenants/{namespace}/services/{alias}/volume-dependency`         |
| deleteVolumeDependency(rn, tn, alias, body)     | DELETE | `/v2/tenants/{namespace}/services/{alias}/volume-dependency`         |

注意 region 端 batch 路径是 `dependencys`（拼写保留 rainbond 历史，**不是 `dependencies`**）。

### 扩展 AppDependencyController（已存在）

按 rainbond `console/urls/__init__.py:614` 行号锚点：

- `AppDependencyController`（已存在，本 change 追加）：
  - `POST /console/teams/{team_name}/apps/{service_alias}/dependency-list` — 批量添加依赖（body 含 `dep_service_ids` 列表）

### `volume-dependency` 旧版无 controller

rainbond 5.0+ 已用 `depvolumes`（migrate-console-volume-extras 落地），旧版 `volume-dependency` API 在 console 端**无对应 controller URL**。本 change 仅实现 region 调用 method，留给 service 层（如 helm install / app market import 子 change）按需调用。

### 业务规则迁移

按 `console/services/app_config/app_relation_service.py:add_service_dependencies` 移植：

- **批量加依赖两阶段写**：
  1. 本地循环 INSERT `tenant_service_relation`（每个 dep 一行）
  2. 调 region `add_service_dependencys` 一次性下发批量
  3. region 失败 → 事务回滚本地行
- **去重**：插入前检查每个 `(service_id, dep_service_id)` 是否已存在，已存在的跳过（与 rainbond 一致）
- **循环依赖检测**：rainbond service 层会调 `check_service_relation` 防 A→B→A，本 change 沿用相同检测

### 不在本 change 内（明确推迟 / 切出）

- 单依赖加 / 删（已在 application-core 实现）
- 反向依赖查询（已在 application-core 实现）
- 新版 depvolumes（在 `migrate-console-volume-extras`）
- helm install / app market import 流程对 `addVolumeDependency` 的具体调用接线 → 留给 helm-install / app-market 各自子 change

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "批量组件依赖与旧版卷依赖"。覆盖 1 个新 endpoint（`POST /dependency-list`）+ 3 个 region method 实现，业务规则含批量两阶段写、去重、循环依赖检测。

## Impact

- **代码新增**：
  - region API：扩 `ServiceDependencyOperationsImpl` 3 method
  - controller：`AppDependencyController` 追加 1 endpoint（POST `/dependency-list`）
  - service：`AppDependencyBatchService`（批量两阶段写 + 去重 + 循环检测）
  - 单测 + 集成测试：3 region method × 2 + 1 controller 集成
- **数据库**：复用 `tenant_service_relation` 表，无新增
- **跨 change 衔接**：
  - `volume-extras` 落地新版 depvolumes，本 change 落地旧版 volume-dependency（共存）
  - 本 change 落地后 `helm-install` / `app-market import` 子 change 可直接调用 `addVolumeDependency`
- **路径变量**：`{team_name}` / `{service_alias}` snake_case
