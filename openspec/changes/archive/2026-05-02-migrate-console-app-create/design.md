## Context

- 已落地：账户/团队/RBAC、集群管理、Region API 客户端基础设施、应用与组件管理（"看 / 改 / 配置"）。
- 关键缺口：用户登录后**无法新建组件**——前端"创建组件"向导调的 3 类端点（source_code / docker_run / third_party）全部 404。
- rainbond 端约束：
  - 创建组件需要生成 `service_id`（32 char UUID）+ `service_alias`（32 char）+ `service_key` + `tenant_service.service_alias` 唯一。
  - 创建后需要立刻调 region API `POST /v2/tenants/{}/services` 在 K8s 端建 deployment / service / configmap，region 失败需要回滚本地。
  - rainbond 把"组件创建参数"（git_url / image / build_strategy / dockerfile / cmd）独立存到 `service_source` 表，不直接放 tenant_service —— 是因为 tenant_service 在 6 次重新部署后会丢失原始创建意图（lang_version / dockerfile 等会被运行时改写）。
  - "删除组件"是 rainbond 选择的"软删除"——把 tenant_service 行复制到 `tenant_service_delete` 表（保留 service_id / 创建参数）→ 再删原表 + 调 region 释放 K8s 资源。

## Goals / Non-Goals

**Goals:**

1. 用户可通过 image / source_code / third_party 3 种来源完整创建一个能跑的组件
2. check / get_check_uuid / check_update 完整链路（rainbond 前端"代码识别"步骤所必需）
3. build / compile_env / code/branch 端点（构建配置编辑）
4. 删除组件：先 region 后本地软删除（写 tenant_service_delete 归档行）
5. ServiceOperations 接口本 change 完整实现 createService / updateService / deleteService / buildService / codeCheck / getServiceLanguage 6 method，使后续 change 都能直接复用

**Non-Goals:**

1. docker_compose（独立 yaml 解析 + 多组件编排，独立 change）
2. VM / KubeBlocks（与容器路径完全不同，独立 change）
3. package_build（jar/war/tar 离线包上传，~500 LOC，独立 change）
4. multi-app（批量克隆/迁移）
5. 完整 image_repositories / image_tags（与 hub registry 重叠，留给 misc）
6. 3rd-party 高级管理（updatekey / 详细 endpoint 管理 / pod 列表）—— 仅落创建路径
7. service_share（→ app-market）

## Decisions

### 决策 1：service_id / service_alias 生成策略

**问题**：rainbond 创建组件时需要 `service_id`（char(32)）+ `service_alias`（char(100)）+ `service_key`（char(32)）；前端可能不传 alias 让 console 生成。

**选择**：
- `service_id` —— 用 `UuidGenerator.makeUuid()`（32 字符）
- `service_alias` —— 默认用 `"gr" + service_id[:6]`（rainbond 历史选择，前缀 "gr" 表示 Goodrain）
- `service_key` —— 等于 service_alias（rainbond 实际未使用，保持一致即可）
- `k8s_component_name` —— 用 `nick_name + "-" + service_alias` 默认值，前端可覆盖
- 用户可在请求体显式传 `service_cname`（中文名）和 `k8s_component_name`，console 不强校验

**Rationale**：与 rainbond 已有的 5000+ tenant_service 行历史命名一致；新组件的 service_alias 仍是 "gr" 开头便于人眼识别

### 决策 2：service_source 表的填充时机

`service_source` 表存 git / image / dockerfile / build_strategy 等"创建参数"，rainbond 端在创建后再次写入这张表（不在 tenant_service.cmd / image 字段保留）。

**选择**：
- 创建组件时**同步**写入 `service_source` 表（事务包裹）：保存原始 image / git_url / code_version / dockerfile / build_strategy 等
- 后续如果用户更新构建参数（PUT compile_env / 切换 git 分支），也会写回 service_source（保持单一来源）
- tenant_service 表的 image / cmd / build_strategy 仍然填，但被 region API 在每次构建后自动覆盖，不依赖
- 删除组件时同步删除 service_source 行（除非要保留历史，rainbond 端确实保留——本 change 选择删除以避免孤儿行）

### 决策 3：写两阶段策略 — 先 console 还是先 region

**问题**：创建组件需要写 console DB（`tenant_service` + `service_source` + `service_group_relation`）+ 调 region API。哪个先？

**选择**：**先 console 后 region**（与 application-core change 的 port/volume 写策略相反）。
- 创建顺序：service_id 生成 → 写 tenant_service → 写 service_source → 写 service_group_relation → 调 region createService → 失败时回滚本地（事务包裹）
- 删除顺序：调 region deleteService → 写 tenant_service_delete 归档 → 删 tenant_service / service_source / service_group_relation 行（事务包裹）

**Rationale**：
- 创建时 region API 调用需要传入 service_id，此 ID 必须先在 console DB 生成
- 删除时 region 失败本地不删 → 用户能看到"删除中"状态、可重试；rainbond 历史选择
- 与 port/volume 不同：port/volume 增量修改时 region 写优先（避免 region/console 不一致），但创建/删除整体是 console 主导

### 决策 4：check / build 端点是异步的

rainbond 创建路径中"代码检查"是异步的：

1. POST `/check` 调 region API 启动检查任务，返回 `check_uuid`
2. 前端轮询 GET `/get_check_uuid` 直到 `check_status="success" | "failure"`
3. 检查通过后 PUT `/check_update` 提交"我接受推荐配置（语言 / 端口 / env）"，console 把推荐值持久化到 service_source 与 envs / ports 表
4. 用户点"构建" → POST `/build` 调 region buildService，返回 event_id；前端轮询 event 状态（监控通过 ServiceEventOperations 在 app-runtime change 落地）

**实现**：本 change 落 1-3，build 端点仅**触发**（返回 event_id）；event 状态查询由 app-runtime change 落地。

### 决策 5：third_party 创建简化版

rainbond 端 source_outer.py 含 source_outer / 3rd-party endpoint 完整管理（含 health 检查、updatekey、动态 endpoint 列表）。本 change 仅落创建路径：

- POST `/apps/third_party` 创建组件（在 tenant_service 写入 `service_source="third_party"`，区分于 source_code / image 来源）
- endpoints / health / updatekey 留给 hardening change

### 决策 6：region createService 调用的 body 形状

rainbond `region_api.create_service` 的 body 需要：service_id / service_alias / cmd / image / extend_method / port_type / cpu / memory / namespace / k8s_component_name / arch（amd64/arm64）...

**选择**：在 service 层 `RegionServicePayloadBuilder.build(TenantService)` 把已写入的 entity 字段拼成 region API body（仅一份转换逻辑，3 种来源都用）。

### 决策 7：删除组件时 service_alias 是否复用

rainbond 删除后 service_alias 立刻可被新组件复用（不留唯一锁）。本 change 沿用：tenant_service_delete 表中的 service_alias 不参与新建唯一性校验（仅原 tenant_service 表参与）。

## Risks / Trade-offs

- **[create 失败 region 不一致]** console DB 写入成功但 region createService 失败 → 本地有孤儿组件行。
  - **Mitigation**：事务包裹整个流程，region 调用在事务内；失败时 Spring 自动 rollback console 写入；同时记日志 + 告警
- **[delete 时 region 已删 console 失败]** 反向情况：region 已释放 K8s 资源但 console 写归档失败。
  - **Mitigation**：先写 tenant_service_delete + 删 tenant_service（同事务），再调 region；如果 region 调用失败抛异常但本地已写归档 → 由独立 reconciliation 任务定期清理（hardening 处理）
- **[service_alias 冲突]** 两个并发创建同 alias 触发 unique constraint violation。
  - **Mitigation**：建在 unique constraint 上 + Spring 自动转 ServiceHandleException(400, msg_show="组件别名已存在")
- **[check 超时]** region check 任务慢（如大型代码仓库 clone），前端轮询超时。
  - **Mitigation**：本 change 不引入超时；前端做长轮询；rainbond 现有行为
- **[component_name 唯一性]** k8s_component_name 在 K8s namespace 内必须唯一，console 端无法 100% 验证。
  - **Mitigation**：region API 失败抛 RegionApiException("component_name conflict") → console 透传给前端

## Migration Plan

1. PR 1：`ServiceSourceInfo` + `TenantServiceInfoDelete` entity + repository
2. PR 2：扩展 `ServiceOperationsImpl`（新增 createService / updateService / deleteService / buildService / codeCheck / getServiceLanguage）
3. PR 3：`AppImageCreateController` (docker_run) —— 最简来源
4. PR 4：`AppSourceCodeCreateController`（git）+ `AppCheckController`（check 异步链路）
5. PR 5：`AppThirdPartyCreateController`
6. PR 6：`AppBuildController`（build / compile_env / code/branch）
7. PR 7：`AppDeleteController`（删除链路）+ 集成测试 + 文档

无 DB 迁移；本 change 不发任何 DDL。

## Open Questions

- **service_alias 命名**：rainbond 是 "gr" + uuid[:6]，是否应改为 "ks"（kuship）？暂保留 rainbond 命名，避免与历史数据混淆
- **build event 状态查询**：本 change 仅触发 build，event 查询要 ServiceEventOperations，留给 app-runtime change
- **lang_version 写**：rainbond 端 region 同步逻辑复杂；本 change punt 写端点（仅落 GET）
- **复制组件 (clone)**：跨 team / 跨 region 复制 → 留给 misc change
