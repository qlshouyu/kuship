# Proposal — migrate-console-governance-policy

## Why

rainbond `views/group.py` + `views/k8s_attribute.py` 提供应用级治理（governance）+ 组件级 k8s 属性（k8s-attributes）两类配置能力，共 9 个 region method，覆盖：

- **应用治理模式**（5 method）：list / check / governance-cr CRUD —— 用于切换应用的 service mesh 治理（istio / kuma / 无）
- **组件 k8s 属性**（4 method）：CRUD —— 用于设置组件 k8s yaml 自定义字段（如 nodeSelector / annotations / sidecar）

kuship-console 完全未迁移这两域，UI 上"应用 → 治理模式"+"组件 → 高级设置 → k8s 属性"页打不开。

## What Changes

- **新增接口**：
  - `GovernanceModeOperations` 5 method（业务自治；归属 `modules/application/governance/api/`）
  - `K8sAttributeOperations` 4 method（业务自治；归属 `modules/application/k8sattr/api/`）
- **Impl 落地**：两个 `@Primary` 实现
- **Entity 落地**：`ComponentK8sAttribute`（`component_k8s_attributes` 表，存组件级 k8s 属性）+ `K8sResource`（应用级 governance CR 持久化，rainbond 用 `k8s_resources` 表）
- **Controller 落地**：
  - `AppGovernanceModeController`（应用治理：GET list / PUT mode / POST/PUT/DELETE governance-cr / GET check）
  - `ComponentK8sAttributeController`（组件属性：GET list / POST / GET by name / PUT / DELETE）
- **测试**：单测 9 + 集成测试 ~10

## Impact

- **能力**：`kuship-console-app`
- **Specs**：ADDED 9 段 Requirement（每个 region method 一段）+ 1 段路线图位置
- **影响范围**：`cn.kuship.console.modules.application.governance.*` + `cn.kuship.console.modules.application.k8sattr.*` 新建子树；不动 14 接口骨架
- **依赖**：无硬依赖；与已落地的 `migrate-console-application-core`（组件管理）+ `migrate-console-app-runtime`（运行时）解耦
- **不实现**：本 change SHALL NOT 落地 governance CR 的 yaml 校验逻辑（rainbond `k8s_resource_service` 内部用 jsonschema 校验，复杂度高，留 hardening）；SHALL NOT 落地 k8s_attribute 的语义检查（如 nodeSelector key 合法性）

## 路线位置 + 偏差说明

- 母路线图：[`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/)
- 优先级：**P2 #2**
- 估计 method 数：12（路线图） vs 实际 9 region method ＝ **偏差 -25%**（在 30% 阈值内）
- **范围确认**：5 个 governance + 4 个 k8s_attribute = 9 method；剔除路线图原始估计中可能含的 console 端 k8s_resource_service 内部逻辑（不是 region 调用，不计入）
- rainbond 参照：
  - `console/views/group.py:376-450`（4 view 类：AppGovernanceMode + AppGovernanceModeCR + AppGovernanceModeCheck）
  - `console/views/k8s_attribute.py`（2 view 类：ComponentK8sAttributeView + ListView）
  - `www/apiclient/regionapi.py:2319-2353`（governance 5）+ `:2572-2598`（k8s_attribute 4）
- 与其它 P2 子 change 的关系：完全独立，可与 P2 #1/#3/#4/#5 并行
