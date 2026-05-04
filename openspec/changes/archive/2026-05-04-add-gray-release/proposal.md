## Why

rainbond-console 提供了应用级灰度发布能力（GrayReleaseRecord 模型 + 1327 行 `gray_release_service.py` + 4 个 OpenAPI v1 端点），可对一个应用同时部署原版本与灰度版本，并通过 ApisixRoute 按权重（0–100%）分流。kuship-console 13 阶段迁移完成账户/团队/区域/应用市场/运行时等核心控制面后，**灰度发布是 rainbond-console 仍未迁移的最重要 OpenAPI 业务能力**——对外 SaaS 客户依赖该能力做生产渐进发布。先迁移它有三个原因：

1. **OpenAPI 兼容性**：rainbond-ui 与外部租户脚本调用 `/openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-release` 等 4 个端点，kuship-console 切流后必须 1:1 兼容这些端点的请求/响应包结构与状态码。
2. **共享 DB 已就位**：`gray_release_record` 表通过 rainbond-console migration 0002 已创建，kuship-console `ddl-auto=validate` 模式下只需新增 JPA 实体即可读写，不引入 schema 变更。
3. **K8s CRD 操作走 RegionAPI 委托**：rainbond-go core 已封装 `/api-gateway/v1/{tenant_name}/routes/http` 接口操作 ApisixRoute CRD，kuship-console 既有 `RegionClient` 直接复用即可，**不**需要新增 K8s client 依赖。

不做的话：rainbond-ui 的"灰度发布"按钮在 kuship-console 切流环境下会 404，外部租户脚本会产生兼容性事故，阻塞 kuship-console 替换 rainbond-console 的最后一公里。

## What Changes

- 新增 `cn.kuship.console.modules.grayrelease` 模块：
  - `entity/GrayReleaseRecord` JPA 实体（映射 `gray_release_record` 表，全部 18 个字段；`status` 枚举 active / completed / cancelled）
  - `repository/GrayReleaseRecordRepository` Spring Data JPA 仓库，提供 by_app / by_tenant / by_domain / active_only 等查询
  - `service/GrayReleaseService` 业务编排：参数校验（gray_ratio 0–100）、模板实例化（委托 `appmarket.AppInstallService`）、ApisixRoute 权重更新（委托 RegionClient）、状态机推进（active → completed / cancelled）
  - `service/ApisixRouteWeightUpdater` 单职责组件：把 `(domain, original_service, gray_service, ratio)` 转成 RegionAPI `/api-gateway/v1/.../routes/http` PUT 请求体并调用
  - OpenAPI v1 controller `OpenApiGrayReleaseController` 暴露 4 端点：
    - `POST /openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-release` 创建灰度
    - `PUT /openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-ratio` 更新比例
    - `POST /openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-rollback` 回滚（清理灰度服务组 + 恢复 100/0 权重）
    - `GET /openapi/v1/gray-releases` 列表（支持 tenant_id / status / page 过滤）
  - 内部 console 端点（rainbond-ui 调用）`POST /console/teams/{team_name}/regions/{region_name}/apps/{app_id}/gray-release-info` 用于查询某 service 是否参与灰度（前端按钮态判断）
- 4 端点的 `OpenApiAuthFilter` 鉴权与既有 OpenAPI v1 端点一致（apikey + permission）
- `application.yaml` 加 `kuship.gray-release` 段：`max-active-per-app`（默认 1，同一 app 不允许并行多个 active）、`apisix-route-update-timeout-seconds`（默认 30）
- GraalVM native 兼容：在 `KuShipConsoleRuntimeHints` 注册 `GrayReleaseRecord` 反射 hint
- 单元测试：`GrayReleaseService` 状态机 + `ApisixRouteWeightUpdater` 请求体生成
- 集成测试：4 OpenAPI 端点的 happy path + 边界（gray_ratio=0 / 100、回滚已 completed 记录、并发创建第二个 active 拒绝）
- `kuship-console/CLAUDE.md` 新增 "灰度发布（add-gray-release）" 段落，覆盖：状态机、与 rainbond-go core /api-gateway/v1 的契约、模板实例化复用 path、未来 hardening（基于 header / cookie 的灰度匹配规则、A/B 实验埋点）

## Capabilities

### New Capabilities

- `kuship-console-grayrelease`: 应用级灰度发布的控制平面契约——状态机、OpenAPI 端点、ApisixRoute 权重委托给 rainbond-go core 的方式。

### Modified Capabilities

<!-- 既有 capabilities 不修改：
- kuship-console-app: 不动 spec，仅增加 module 实现
- 共享 DB schema 已经存在，无 schema delta -->

## Impact

- **新增文件**：`modules/grayrelease/{entity,repository,service,controller,dto}/*.java`（约 12–15 文件，预计 800–1200 行 Java，远低于 Python 1327 行因为 region API 调用收敛在 RegionClient）
- **新增测试**：`modules/grayrelease/{service,integration}/*Test.java`（约 6–8 个测试类）
- **修改文件**：`KuShipConsoleRuntimeHints.java`、`application.yaml`、`SecurityConfig.java`（GET /openapi/v1/gray-releases 走 OpenApiAuthFilter，无需 permitAll；console 端点走 JWT 默认认证，也无需 permitAll）、`kuship-console/CLAUDE.md`
- **不修改**：rainbond-go core（既有 `/api-gateway/v1/.../routes/http` 接口已就位）、`gray_release_record` 表 schema、AppMarket / AppRuntime 已迁移模块的 public API
- **影响范围**：rainbond-ui 灰度发布页面 + 外部 OpenAPI 客户端切到 kuship-console 后无感切换；不涉及 SQL DDL 风险因为表已存在
- **回退路径**：本 change 仅新增端点与 module，不修改任何既有路径；如发现 bug 可在 RegionClient 层 disable 端点回退到 rainbond-console 处理灰度调用
