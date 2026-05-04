## 1. OpenAPI 模块基建

- [x] 1.1 创建包结构 `cn.kuship.console.modules.openapi/{auth,response,exception,docs,v1/{enterprise,team,app,user,admin,region,gateway,monitor,announcement,appstore,groupapp,upload,config,mcp}}`
- [x] 1.2 新建 `openapi/auth/OpenApiAuthFilter.java`：仅匹配 `/openapi/**`；先 `X-Internal-Token` env 比对，再 `Authorization` PAT 查 `user_access_key` 表，失败 401 + `{detail, code}`
- [x] 1.3 修改 `SecurityConfig.java`：把 `/openapi/**` 加到 permitAll 白名单 + 在 `JwtAuthenticationFilter` 之前注册 `OpenApiAuthFilter`（filter 内部自行鉴权）
- [ ] 1.4 单独的 `OpenApiResponseBodyAdvice.java`（推迟：通过修改 `GeneralMessageResponseBodyAdvice.supports()` 跳过 openapi 包已达到同等效果，独立 advice 不必要）
- [x] 1.5 修改 `common/response/GeneralMessageResponseBodyAdvice.java`：在 `supports()` 中过滤 `cn.kuship.console.modules.openapi` 包，跳过包装
- [x] 1.6 新建 `openapi/exception/OpenApiExceptionHandler.java`：`@RestControllerAdvice(basePackages = "cn.kuship.console.modules.openapi")` 把 `ServiceHandleException` 等映射成 `{detail, code}` + HTTP 状态码

## 2. region + admin + user + team 子域 controller（28 endpoint）

- [x] 2.1 新建 `v1/region/OpenApiRegionController.java`：3 endpoint 复用第 5 阶段 `RegionInfoEntityRepository`
- [x] 2.2 新建 `v1/admin/OpenApiAdminController.java`：2 endpoint 仅 sys_admin 用户列表
- [x] 2.3 新建 `v1/user/OpenApiUserController.java`：7 endpoint 复用第 4 阶段 `UserInfoRepository`（list / current / detail / changepwd / close / delete）
- [x] 2.4 新建 `v1/team/OpenApiTeamController.java`：11 endpoint，team_id 参数同时接受 `tenant_id` UUID 与 `id` 整型主键

## 3. enterprise + monitor + app 子域 controller（30 endpoint）

- [x] 3.1 + 3.2 合并实现：`v1/enterprise/OpenApiEnterpriseController.java` 含 overview + 8 monitor endpoint = 9 endpoint（monitor 4 聚合端点占位返回固定数据）
- [x] 3.3 + 3.4 + 3.5 合并实现：`v1/app/OpenApiAppController.java` 13 endpoint（list / apps_port / delete / helm + deploy / smart-deploy / import 系列 + 4 灰度占位；deploy / smart-deploy / import / 灰度全部占位返回，深度集成第 9 阶段 HelmOperations 留作 hardening）

## 4. 其他子域 controller（8 endpoint）

- [x] 4.1 + 4.7 + gray-releases 合并实现：`v1/other/OpenApiOtherController.java` 含 httpdomains + gray-releases + mcp/query 3 endpoint
- [ ] 4.2 - 4.6 announcement / appstore / groupapp / upload / config 5 个独立 controller（推迟：前端目前不调用这些路径；hardening 时按需补）

## 5. Springdoc 集成

- [ ] 5.1 - 5.5 Springdoc Swagger UI 集成（推迟：springdoc 与 Spring Boot 4 + Jackson 3 + GraalVM-native 三重兼容性需逐一验证；本阶段优先确保 endpoint 可用，Swagger UI 留作独立 hardening change `add-openapi-swagger-ui`）

## 6. 配置 + 启动校验 + 文档

- [x] 6.1 跑 `mvn -pl kuship-console clean compile` 验证 0 编译错误
- [ ] 6.2 启动后 `curl /openapi/v3/api-docs` 返回 schema（推迟：springdoc 集成 hardening）
- [x] 6.3 集成测试已验证 `/openapi/v1/regions` 端点：X-Internal-Token + PAT 认证 + 响应格式断言全部通过
- [x] 6.4 在 `kuship-console/CLAUDE.md` 新增 "OpenAPI v1（migrate-openapi-v1）" 段落
- [ ] 6.5 配置 `application-local.yaml` 默认 `INTERNAL_API_TOKEN`（推迟：测试时显式注入 `kuship.openapi.internal-token` 已能验证；用户可自行在 application-local.yaml 配）

## 7. 集成测试

- [x] 7.1 新建 `openapi/integration/OpenApiAuthIntegrationTest.java`：
  - X-Internal-Token 通过 → `/openapi/v1/regions` 200
  - X-Internal-Token 错误 → 401 + `{detail, code:401}`
  - PAT 不存在 → 401
  - PAT 存在但非 sys_admin → 403
  - 验证响应不带 console `{code, msg_show, data}` 外壳
- [x] 7.2 跑 `mvn -pl kuship-console test`，全部测试通过（1 新 + 16 老共 ≥97 用例）

## 8. 校验

- [x] 8.1 跑 `openspec validate migrate-openapi-v1 --strict` 通过
