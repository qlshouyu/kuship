## Why

经过 6 个 change 落地，kuship-console 已能"看到 / 编辑 / 配置"应用，但**还不能从零创建组件**。一个新 team 进入控制台后只能看到空应用列表 + "+创建组件"按钮点击无响应——前端调的 `POST /console/teams/{}/apps/source_code` / `docker_run` / `third_party` 都是 404。

`migrate-console-app-create` 是首个让 kuship-console **能真正部署一个应用**的 change：把 rainbond 端最常用的"基于镜像 / 基于 Git 仓库 / 第三方组件"3 种来源 + check 流程 + build 触发 + 删除组件的端点全部移植，前端"创建组件"向导可走通。

rainbond 端 app_create 模块体量较大（~2500 LOC across 11 view files）；本 change 严格聚焦 **3 种主流来源（image / source_code / third_party）+ 检查 + 构建 + 删除**，把 `docker_compose` / `VM` / `kubeblocks` / `package_build`（离线包上传）/ `multi-app`（批量克隆）punt 到对应独立 change。

## What Changes

### 应用创建入口（3 种来源）

- ADDED：`AppImageCreateController`（路径 `/console/teams/{team_name}/apps/docker_run`）
  - `POST /` —— 基于镜像创建组件（最简单，仅需 image / cmd / 名称）
- ADDED：`AppSourceCodeCreateController`（路径 `/console/teams/{team_name}/apps/source_code`）
  - `POST /` —— 基于 Git 仓库创建组件（含 git_url / code_version / build_strategy）
- ADDED：`AppThirdPartyCreateController`（路径 `/console/teams/{team_name}/apps/third_party`）
  - `POST /` —— 创建第三方组件（外部 IP/域名 endpoint，不真正部署 K8s pod）

### 创建前/后检查

- ADDED：`AppCheckController`（路径 `/console/teams/{team_name}/apps/{service_alias}`）
  - `POST /check` —— 触发对组件的代码检查（异步，返回 check_uuid）
  - `GET /get_check_uuid` —— 查 check 状态
  - `PUT /check_update` —— 在检查结果上更新组件配置（语言 / 端口 / env 推荐值）

### 应用构建

- ADDED：`AppBuildController`（路径 `/console/teams/{team_name}/apps/{service_alias}`）
  - `POST /build` —— 触发组件构建（git pull + 镜像 build + 部署到 K8s）
  - `GET /code/branch` —— 列出 git 仓库可用分支（用于切换构建分支）
  - `GET /compile_env` —— 查询编译环境变量
  - `PUT /compile_env` —— 修改编译环境变量

### 应用删除

- ADDED：`AppDeleteController`（路径 `/console/teams/{team_name}/apps/{service_alias}`）
  - `POST /delete` —— 删除组件（写 `tenant_service_delete` 表 + 调 region API 释放 K8s 资源）
- ADDED：`TenantServiceInfoDelete` entity（对应 `tenant_service_delete` 表，仅用于"已删除组件回收站"软删除归档）

### Service 来源元数据

- ADDED：`ServiceSourceInfo` entity（对应 `service_source` 表，存放 git/image/cmd/dockerfile/build_strategy 等创建参数；rainbond 端独立表与 tenant_service 解耦）
- ADDED：`UpdateServiceCmdReq` 等用于"修改启动命令"的 DTO

### Region API 扩展

- MODIFIED：`ServiceOperations`（已实现 `getServiceInfo`）—— 完整实现 `createService` / `updateService` / `deleteService` / `buildService` / `codeCheck` / `getServiceLanguage`（5 method）
- 写入两阶段策略：先写 console DB（service_id 由 console 生成）→ 调 region `createService`（K8s ConfigMap / Deployment 建好）→ region 失败时回滚本地 service 行 + 关联

### 不进入此 change（明确 punt）

- **docker_compose** —— 独立 change `migrate-console-compose`（rainbond 端 382 LOC，含 yaml 解析 + 多组件批量编排）
- **VM 虚拟机**（vm_run.py 94 LOC）—— 独立 change `migrate-console-vm`，与容器路径完全不同
- **KubeBlocks 数据库**（kubeblocks_create.py 112 LOC）—— 独立 change `migrate-console-kubeblocks`（依赖 KubeBlocks operator）
- **package_build**（jar/war/tar 离线上传，~500 LOC across multiple views）—— 独立 change `migrate-console-package-build`
- **multi-app create**（multi_app.py 88 LOC，批量克隆/迁移组件）—— `migrate-console-misc`
- **image_repositories / image_tags 列表**—— `migrate-console-misc`（与 hub registry 重叠）
- **3rd-party endpoint API**（外部端点详细管理 source_outer.py 部分）—— 简化版本本 change 落，详细版留给 hardening
- **lang_version 写**（rainbond 端 region 同步）—— 与 region-cluster change 已落 GET 配套
- **app_grant**（应用授权链路）—— 几乎不用，punt
- **service_share**（应用分享）—— `migrate-console-app-market`

## Capabilities

### New Capabilities

无（不引入新 capability）

### Modified Capabilities

- `kuship-console-app`：在已有 53 requirements 基础上 ADDED 3 种应用创建入口 / check 流程 / 构建端点 / 删除端点 / 2 张新 entity 等 requirements；MODIFIED `Region API 客户端基础设施`（`ServiceOperations` 6 method 完整实现状态更新）

## Impact

- **新增 entity**：`ServiceSourceInfo`（service_source 表）+ `TenantServiceInfoDelete`（tenant_service_delete 软删除归档）；累计 entity 31 张
- **写两阶段策略**：创建时先写 console（拿 service_id）→ 再调 region API → region 失败回滚本地；删除时先调 region 释放 K8s 资源 → 再写 delete 归档表 + 删 tenant_service 行（事务包裹）
- **kuship-ui**：前端"创建组件"向导（image / 源代码 / 第三方）所有 step 解锁；"组件 → 构建" / "组件 → 删除" 按钮可工作
- **path 改动**：所有创建端点都在 `/console/teams/{team_name}/apps/*`，与 rainbond URL 完全一致
