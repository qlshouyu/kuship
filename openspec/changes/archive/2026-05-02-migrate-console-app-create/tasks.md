## 1. JPA Entity 与 Repository（2 张新表）

- [x] 1.1 `cn.kuship.console.modules.appcreate.entity.ServiceSourceInfo`（service_source 表 9 列；service_id 唯一）
- [x] 1.2 `TenantServiceInfoDelete`（tenant_service_delete 表 50+ 列；含 delete_time / app_name / app_id 软删除归档字段）
- [x] 1.3 2 个 Repository：`ServiceSourceInfoRepository.findByServiceId / deleteByServiceId`、`TenantServiceInfoDeleteRepository`
- [x] 1.4 启动时 hibernate validate 通过（kuship-mysql 真库，84 测试无回归）

## 2. ServiceOperations 6 method 完整实现

- [x] 2.1 升级 `ServiceOperationsImpl`（在 application module，避免 @Primary 多 bean 冲突）：补 `createService` / `updateService` / `deleteService` / `buildService` / `codeCheck` / `getServiceLanguage` 6 method
- [x] 2.2 沿用 `RegionApiSupport.exchange` lambda 模式
- [x] 2.3 接口签名：与 ServiceOperations 接口默认 method 完全对齐（含 enterpriseId 参数等）

## 3. RegionServicePayloadBuilder 工具类

- [x] 3.1 `RegionServicePayloadBuilder.build(TenantService, ServiceSourceInfo)` —— 22 字段映射
- [x] 3.2 字段映射：service_id / service_alias / cmd / image / extend_method / port_type / cpu / memory / namespace / k8s_component_name / arch / dockerfile / language / build_strategy / 等
- [ ] 3.3 单测：仅集成测试覆盖（AppThirdPartyCreate 测试间接验证 builder 路径）；显式单元测试留给 hardening

## 4. AppImageCreate Controller（docker_run）

- [x] 4.1 `AppImageCreateController` 路径 `/console/teams/{team_name}/apps/docker_run` POST
- [x] 4.2 `DockerRunCreateReq` DTO 含 image / cmd / port / cpu / memory / extend_method / group_id / region_name / service_cname / k8s_component_name / arch
- [x] 4.3 流程：service_id 生成 → 写 3 表 → 调 region createService → 事务包裹自动回滚
- [x] 4.4 `@RequirePerm("app_overview_create")`

## 5. AppSourceCodeCreate Controller

- [x] 5.1 `AppSourceCodeCreateController` 路径 `/console/teams/{team_name}/apps/source_code` POST
- [x] 5.2 `SourceCodeCreateReq` DTO 含 git_url / code_version / build_strategy / language / dockerfile / oauth_service_id / git_username / git_password 等
- [x] 5.3 git 鉴权字段透传到 service_source.user_name / password（明文，与 rainbond 一致；hardening 加密留给独立 change）
- [x] 5.4 service_origin="assistant"、service_source="source_code"

## 6. AppThirdPartyCreate Controller

- [x] 6.1 `AppThirdPartyCreateController` 路径 `/console/teams/{team_name}/apps/third_party` POST
- [x] 6.2 `ThirdPartyCreateReq` DTO 含 endpoints[] / service_cname / group_id / kind="static" 默认
- [x] 6.3 service_source.extend_info 写入 endpoints JSON 序列化（限长 1024）
- [x] 6.4 不调 region createService（第三方组件无 K8s deployment）；create_status="complete"

## 7. AppCheck Controller

- [x] 7.1 `AppCheckController` 三段式异步：POST /check / GET /get_check_uuid / PUT /check_update
- [x] 7.2 check_uuid 持久化到 tenant_service.check_uuid + check_event_id 字段；前端基于此轮询 region event 状态

## 8. AppBuild Controller

- [x] 8.1 `AppBuildController`：POST /build（调 ServiceOperations.buildService）/ GET /code/branch（简化版仅返回 current 分支）/ GET+PUT /compile_env（用 envs 表 scope="build"）
- [x] 8.2 build 成功后 update_version 自增 + update_time 刷新

## 9. AppDelete Controller（软删除归档）

- [x] 9.1 `AppDeleteController` 路径 `/console/teams/{team_name}/apps/{service_alias}/delete` POST
- [x] 9.2 `AppDeleteService.delete`：先调 region deleteService（third_party 跳过）→ 写 tenant_service_delete 归档（55 字段一次性 copy）→ 清理本地 envs/ports/volumes/dependency/probe + service_source + service_group_relation 行 + tenant_service 行（事务包裹）
- [x] 9.3 `@RequirePerm("app_overview_create")` 与 rainbond 一致

## 10. 集成测试

- [x] 10.1 `AppThirdPartyCreateIntegrationTest`：1 用例覆盖第三方组件创建 + 验证 tenant_service / service_source 写入、service_origin=third_party、endpoints JSON 持久化
- [ ] 10.2 `AppImageCreate / AppSourceCodeCreate / AppDelete` 集成测试：需 mock region API 或独立 region 节点；本机 kuship-rainbond docker 无完整 region API exposure，留给真实部署环境
- [ ] 10.3 其他端点（check / build）真实端到端：留待真实部署环境

## 11. 文档

- [x] 11.1 `kuship-console/CLAUDE.md` 增加"应用创建（migrate-console-app-create）"段落（含 6 controller + 2 entity + 写两阶段策略 + 14 接口骨架进度）
- [x] 11.2 风险提示已在段落中明确（创建先 console 后 region；删除先 region 后 console；事务包裹自动 rollback）

## 12. 验证

- [x] 12.1 `mvn -pl kuship-console clean compile` BUILD SUCCESS
- [x] 12.2 `mvn -pl kuship-console test` 84/84 通过（83 之前 + 1 新增）
- [x] 12.3 真实 rainbond docker schema 校验：hibernate validate 全部 25 entity（之前 23 + 本 change 2）通过
- [x] 12.4 `openspec validate migrate-console-app-create --strict` 通过
- [ ] 12.5 端到端真实 region 验证：需要独立 region API exposure（K3s region runtime 配置 + mTLS），留待真实部署环境
