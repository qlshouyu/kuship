## 1. JPA Entity 与 Repository（7 个核心 + 1 个关联表）

- [x] 1.1 `cn.kuship.console.modules.application.entity.ServiceGroup`（service_group 表，~17 列）
- [x] 1.2 `ServiceGroupRelation`（service_group_relation 应用-组件 N:N 关联）
- [x] 1.3 `TenantService`（tenant_service ~50 列；本 change 一次性映射全部，hibernate validate 一次过）
- [x] 1.4 `TenantServiceEnvVar`（tenant_service_env_var）
- [x] 1.5 `TenantServicesPort`（tenant_services_port）
- [x] 1.6 `TenantServiceVolume`（tenant_service_volume）
- [x] 1.7 `TenantServiceRelation`（tenant_service_relation 依赖关联）
- [x] 1.8 `ServiceProbe`（service_probe）
- [x] 1.9 8 个 Repository（继承 `JpaRepository<E, Integer>`）；高频 derived query `findByServiceId` / `findByGroupId` / `findByServiceIdAndContainerPort` / `findByServiceIdAndAttrNameAndScope` 等
- [x] 1.10 启动 schema 校验通过（kuship-mysql 真库，全套 83 测试通过）

## 2. ServiceOperations（infrastructure 接口实现）

- [x] 2.1 `ServiceOperationsImpl @Primary @Service` 实现 `getServiceInfo`（接口已有 method，对应 region GET /v2/tenants/{}/services/{}）；status 转发由 ServiceStatusOperations 在 app-runtime change 实现
- [x] 2.2 `ServiceOperations` 接口未修改（保留所有 default unsupported method）；其他 method 由后续 change 补完
- [x] 2.3 沿用 `TenantOperationsImpl` 的 RestClient + exchange + processor 模式（共享 helper `RegionApiSupport`）

## 3. ServicePort/Volume/Dependency/Probe Operations 实现

- [x] 3.1 `ServicePortOperationsImpl @Primary @Service`：`addPort` / `updatePort` / `deletePort` / `manageInnerPort` / `manageOuterPort` 5 method
- [x] 3.2 `ServiceVolumeOperationsImpl @Primary @Service`：`addVolumes` / `deleteVolumes` / `upgradeVolumes` 3 method（注意接口 method 名是复数）
- [x] 3.3 `ServiceDependencyOperationsImpl @Primary @Service`：`addDependency` / `deleteDependency` 2 method
- [x] 3.4 `ServiceProbeOperationsImpl @Primary @Service`：`addProbe` / `deleteProbe` / `updateProbe` 3 method
- [x] 3.5 `ServiceEnvOperations` 不实现（rainbond env 走本地优先策略；保持 default unsupported）
- [x] 3.6 配套 DTO：`PortReq` / `VolumeReq` / `DependencyReq` / `ProbeReq` / `EnvReq`

## 4. 应用主体（Group）Controller

- [x] 4.1 `GroupController` 路径 `/console/teams/{team_name}/groups`：8 端点（list / create / GET / PUT / DELETE / status / component_names / governancemode-{GET,PUT}）
- [x] 4.2 创建空 application：写 `service_group` 行，默认值符合 rainbond 约定
- [x] 4.3 删除前置校验：`ServiceGroupRelationRepository.findByGroupId(appId)` 非空 → 拒绝（400 + 中文 msg_show）
- [x] 4.4 整体状态聚合（简版）：组件数 + create_status 去重列表；运行状态留给 app-runtime change
- [x] 4.5 governance_mode 校验：值在 `[KUBERNETES_NATIVE, BUILD_IN_SERVICE_MESH, RAINBOND_NATIVE_SERVICE_MESH, NO_GOVERNANCE]` 内，否则 400
- [x] 4.6 路径权限：read `@RequirePerm("app_overview_describe")`，write `@RequirePerm("app_create_perms")`

## 5. 组件（Component）Controller

- [x] 5.1 `ComponentController` 路径 `/console/teams/{team_name}/apps/{service_alias}`：detail / brief / group GET / group PUT / keyword 共 5 端点（status 留给 app-runtime change）
- [x] 5.2 序列化分 brief/detail 两套；列表场景默认用 brief（避免大对象传输）
- [x] 5.3 detail 序列化时 `secret` 字段脱敏（`***N chars***`）
- [x] 5.4 GET /group：通过 `service_group_relation` 反查归属 application
- [x] 5.5 PUT /group：事务包裹删旧关联 + 写新关联

## 6. AppEnv Controller（仅本地写）

- [x] 6.1 `AppEnvController` 路径 `.../envs`：GET (list, scope query) / POST / PUT/{env_id} / DELETE/{env_id}
- [x] 6.2 仅写本地 `tenant_service_env_var` 表（不调 region API）
- [x] 6.3 唯一性校验：同 service_id + scope + attr_name 已存在 → 400 中文 msg_show

## 7. AppPort Controller（写库 + region 同步）

- [x] 7.1 `AppPortController` 路径 `.../ports`：GET / POST / DELETE/{port} / PUT/{port}
- [x] 7.2 POST：先调 `servicePortOperations.addPort` → 写本地表
- [x] 7.3 PUT：根据 inner/outer 字段差异调 `manageInnerPort` / `manageOuterPort`
- [x] 7.4 失败 region 时本地不写；事务包裹

## 8. AppVolume / AppDependency / AppProbe Controllers

- [x] 8.1 `AppVolumeController` 路径 `.../volumes`：GET / POST / DELETE / PUT；先 region 后本地
- [x] 8.2 `AppDependencyController` 路径 `.../dependency` + `dependency-list` + `dependency-reverse`：4 端点
- [x] 8.3 `AppProbeController` 路径 `.../probe`：GET / POST（软去重：先 delete 同 service_id+mode → 再 insert）/ DELETE/{probe_id}

## 9. 集成测试

- [x] 9.1 `GroupLifecycleIntegrationTest`：1 用例 6 步全链路（创建 → 列表 → 改 governance → 非法 governance 400 → 详情 → 删除）
- [x] 9.2 `AppEnvIntegrationTest`：2 用例（CRUD + 同 attr_name 唯一性 400）含 fixture 一个 tenant_service 行
- [ ] 9.3 `ComponentDetailIntegrationTest`：留给 hardening change（与 9.2 fixture 重叠，可合并）
- [ ] 9.4 其他子资源（ports/volumes/dependency/probe）的 region API 调用：本机 kuship-rainbond docker 缺乏 region API exposure，留待真实部署环境验证

## 10. 文档

- [x] 10.1 `kuship-console/CLAUDE.md` 增加"应用与组件管理"段落（含 7 controller + 8 entity + 写两阶段策略 + 14 接口骨架进度）
- [x] 10.2 风险提示：region 与本地状态可能不一致（已在 CLAUDE.md 段落中明确）
- [ ] 10.3 `kuship-console/README.md` 增加"应用管理 端到端验证"段：留待 hardening change 一并补充 curl 示例

## 11. 验证

- [x] 11.1 `mvn -pl kuship-console clean compile` BUILD SUCCESS（181 source files）
- [x] 11.2 `mvn -pl kuship-console test` 83/83 通过（含 2 个新增集成测试 + 之前 81 全部）
- [x] 11.3 真实 rainbond docker schema 校验：kuship-console 启动时 hibernate validate 全部 23 entity（之前 15 + 本 change 8）通过
- [x] 11.4 `openspec validate migrate-console-application-core --strict` 通过
- [ ] 11.5 端到端真实 region 验证（envs 全本地已通过；ports/volumes/dependency/probe 需要真实 region API exposure 留给真实部署环境）
