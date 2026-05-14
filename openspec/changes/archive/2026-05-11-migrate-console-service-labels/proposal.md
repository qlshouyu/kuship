# Proposal — migrate-console-service-labels

## Why

rainbond `services/app_config/label_service.py` + `views/app_config/app_label.py` 提供组件级的 node label 绑定与 state label 配置（含可用 label 列表查询），共 4 个 region method。kuship-console 当前**完全未迁移**该域，UI 上"组件 → 高级设置 → 节点标签"页打不开。

本 change 落地 4 region method + 4 个 controller endpoint，让组件可以选择 region 节点 label 做调度约束。

## What Changes

- **新增接口**：`ServiceLabelOperations` 4 method（业务自治非 14 骨架；归属 `modules/application/api/`）
- **Impl 落地**：`ServiceLabelOperationsImpl @Primary`
- **Entity 落地**：`TenantServiceLabel`（`service_labels` 表，已存在于 console 库，rainbond schema：tenant_id / service_id / label_id / region / create_time）+ `Labels`（`labels` 表）
- **Controller 落地**：`AppLabelController`，4 endpoint（列组件 label / 添加 / 删除 / 列可用 label）
- **测试**：单测 4 + 集成测试 ~6

## Impact

- **能力**：`kuship-console-app`
- **Specs**：ADDED 4 段 Requirement（每段一个 region method 端到端契约）
- **影响范围**：`cn.kuship.console.modules.application.label.*` 新建子树；不动 14 接口骨架
- **依赖**：硬依赖 P0 #4 `migrate-console-cluster-nodes`（已落地）—— node label 数据由 cluster-nodes 通过 `/v2/cluster/nodes/{node}/labels` 提供；本 change 的 `addServiceNodeLabel` 引用的 label_id 即来自该处
- **不实现**：本 change SHALL NOT 落地 OS label / arch label 的特殊化展示逻辑（rainbond 中 `set_service_os_label` 是 console 内部状态，由 ServiceVolume change 处理）

## 路线位置

- 母路线图：[`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/)
- 优先级：**P2 #4**
- 估计 method 数：4（与路线图一致）
- rainbond 参照：`console/services/app_config/label_service.py`（150 行）+ `console/views/app_config/app_label.py`（160 行）+ `www/apiclient/regionapi.py:337-388`
- 与其它 P2 子 change 的关系：完全独立，可与 P2 #1/#2/#3/#5 并行；硬依赖 P0 #4 cluster-nodes 已落地
