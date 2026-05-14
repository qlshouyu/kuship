# Proposal — migrate-console-maven-setting

## Why

rainbond `views/region.py:MavenSettingView + MavenSettingRUDView` 提供企业级 maven 仓库配置 CRUD（5 个 region method），用于 java 工程构建时引用私有 maven 仓库。kuship-console 完全未迁移，UI 上"集群管理 → 构建配置 → maven 设置"页打不开。

本 change 落地 5 region method + 2 个 controller endpoint（rainbond 用 GET/POST/PUT/DELETE 复用 URL 实现 5 操作）。

## What Changes

- **新增接口**：`MavenSettingOperations` 5 method（业务自治非 14 骨架；归属 `modules/region/api/`）
- **Impl 落地**：`MavenSettingOperationsImpl @Primary`
- **Controller 落地**：`MavenSettingController`，2 endpoint 内部承载 5 个 HTTP method（GET list / POST create / GET detail / PUT update / DELETE delete）
- **测试**：单测 5 + 集成测试 ~7

## Impact

- **能力**：`kuship-console-app`
- **Specs**：ADDED 5 段 Requirement + 1 段路线图位置
- **影响范围**：`cn.kuship.console.modules.region.maven.*` 新建子树；不动 14 接口骨架；不动 build-versions 已落地的 lang version 域
- **依赖**：无硬依赖；与 P1 #4 `migrate-console-build-versions` 协调（lang version 由 build-versions 持有，本 change 不重复落地）
- **不实现**：本 change SHALL NOT 落地 maven setting 与 lang version 的关联渲染逻辑（如"java 8 + 私有 maven 仓库"组合的模板渲染），由 build-versions 后续 hardening 处理

## 路线位置 + 偏差说明

- 母路线图：[`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/)
- 优先级：**P2 #3**
- 估计 method 数：8（路线图） vs 实际 5 region method ＝ **偏差 -37.5%**
- **偏差原因**：路线图 8 method 估计含了"maven setting + lang version 工具链协调"的预期，但 lang version 已被 P1 #4 build-versions 持有（不重复落地）；纯 maven setting CRUD 实际只有 5 个 region method（list / add / get / update / delete）。本 change 范围严格限定为 5 method
- rainbond 参照：`console/views/region.py:366-460`（2 view 类）+ `www/apiclient/regionapi.py:2123-2168`
- 与其它 P2 子 change 的关系：完全独立，可与 P2 #1/#2/#4/#5 并行
