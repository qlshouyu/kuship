## Why

第 11 阶段（migrate-console-misc）让 kuship-console 完成所有"内部 console UI 后端"端点。剩下还有一类**面向第三方 / CLI 工具 / 自动化集成**的端点没有迁移：rainbond `openapi/v1/*` 共 ~50 endpoint，路径前缀是 `/openapi/v1/...`，认证模式与 console JWT 完全不同（用 **Token 头部 + INTERNAL_API_TOKEN 环境变量**双模式）。

这是 13 阶段路线倒数第二阶段。完成它后 kuship-console 就能支撑 grctl CLI、第三方平台集成、CI/CD 流水线直接调用。是发布 standalone 镜像、给企业客户对接 OA 系统的前置。

## What Changes

- **OpenAPI 认证**：新增 `OpenApiAuthFilter`（与 console JWT filter 平级），处理 `/openapi/v1/**` 路径：
  - `X-Internal-Token` 头部 + 环境变量 `INTERNAL_API_TOKEN` 比对（内部服务调用，注入虚拟管理员）
  - `Authorization` 头部 + `user_access_key` 表查询（外部用户 PAT，复用第 4 阶段已有 entity）
  - 失败一律 401 + JSON `{"detail": "..."}`（OpenAPI 风格，**与 console general_message 不同**）
- **OpenAPI 权限**：`OpenApiPermissions` 切面 —— 全部端点需 sys_admin（rainbond 历史约束），不走 `@RequirePerm` 复杂权限码体系。
- **响应格式适配**：OpenAPI 端点 SHALL 用 `OpenApiResult` 简化包装：直接返回业务对象 JSON（不像 console 包 `{code, msg, msg_show, data}`），错误时返回 `{detail: "...", code: 4xx}`。
- **路由前缀全局唯一**：`/openapi/v1/...`（与 `/openapi/v2/...` 区分；v2 留作 hardening）
- **Swagger UI**：暴露 `/openapi/swagger-ui` + `/openapi/v3/api-docs` 由 springdoc-openapi 自动生成，无需手写 JSON schema。
- **业务 endpoint 50 个**（按 view 文件分子域）：
  - `enterprise_view.py` 432 行 14 endpoint：overview / configs / monitor 系列 / app rank / 资源占用 / Prometheus 透传 / Performance / 实例监控
  - `team_view.py` 580 行 11 endpoint：teams 列表 / app_model / regions / certificates / events / overview / app resource
  - `apps/apps.py` ~400 行 9 endpoint：list apps / app port / app deploy / smart-deploy / import / chart info / delete app / helm-chart
  - `apps/gray_release.py` 4 endpoint：gray-release / gray-ratio / gray-rollback / list
  - `region_view.py` 212 行 3 endpoint：list regions / region info / replace ip
  - `user_view.py` 280 行 7 endpoint：users CRUD / current / changepwd / close / delete
  - `admin_view.py` 92 行 2 endpoint：administrators / admin info
  - `gateway/gateway.py` 1 endpoint：list http rules
  - `announcement_view.py` + `appstore_view.py` + `groupapp.py` + `upload_view.py` + `config_view.py` 共 ~10 endpoint
- **MCP openapi sub-route**：`/openapi/v1/mcp/...` 透传到第 11 阶段已实现的 MCP controller

## Capabilities

### Modified Capabilities

- `kuship-console-app`: 新增约 12 条 OpenAPI v1 端点 Requirement —— 认证机制 / 权限 / 响应格式 / 6 子域端点 / 5 张关联表 / 测试覆盖。

## Impact

- **新增包**：`cn.kuship.console.modules.openapi/v1/`（按 6 子域细分：auth / enterprise / team / app / user / admin / region / gateway / announcement / appstore / groupapp / upload / config / monitor / mcp）。
- **新增 Filter**：`OpenApiAuthFilter`（在 `JwtAuthenticationFilter` 之前注册，仅匹配 `/openapi/**`）。
- **新增 Wrapper**：`OpenApiResult` + `OpenApiResponseBodyAdvice`（`@ConditionalOn` `@OpenApi` 标注的 controller 包，避免与 console 的 `GeneralMessageResponseBodyAdvice` 冲突）。
- **Springdoc**：新增 `springdoc-openapi-starter-webmvc-ui` 依赖（GraalVM-native 兼容性需验证；如不行 fallback 静态 swagger.json）。
- **复用 Entity**：`UserAccessKey`（第 4 阶段）+ 全部业务 entity（直接查 console 库）。
- **测试**：扩展 1 类集成测试覆盖 OpenAPI 认证 + enterprise overview 端点。
- **不引入新业务 Entity**：OpenAPI 仅是 console 已有 entity 的别名查询面，零新增表。
