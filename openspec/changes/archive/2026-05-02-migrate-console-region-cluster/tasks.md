## 1. RegionInfo entity 与 repository（业务层 JPA）

- [x] 1.1 `cn.kuship.console.modules.region.entity.RegionInfo` `@Entity` 21 列对齐 `region_info` schema
- [x] 1.2 SQL 保留字 `desc` 用反引号 `@Column(name = "`desc`")`
- [x] 1.3 `RegionInfoEntityRepository extends JpaRepository<RegionInfo, Integer>`：含 `findByRegionId`、`findByRegionName`、`findByEnterpriseId`、`findByEnterpriseIdAndRegionName`、`findByEnterpriseIdAndStatus`
- [x] 1.4 单测验证：通过 `EnterpriseRegionsIntegrationTest` 真实 DB（kuship-mysql）写入 → 列表 → 详情 → 删除全链路验证 read+write 路径

## 2. Region 与 token 服务

- [x] 2.1 `RegionService.parseToken(String yaml, ...)` 用 snakeyaml `Yaml.load` → Map；7 字段顺序对齐 rainbond，缺失抛 `ServiceHandleException(400, ...)` 含中文 msg_show
- [x] 2.2 `RegionService.addRegion(enterpriseId, req)` 调 parseToken + 填充 region_id/alias/type(JSON)/status="1"/createTime/scope/provider
- [x] 2.3 `RegionService.deleteRegion(enterpriseId, regionId)` 校验 tenant_region 无关联 → JPA delete → 显式 `regionClientFactory.evict(enterpriseId, regionName)`
- [x] 2.4 `RegionParseTokenTest` 5 用例：合法 / 缺 ca / 缺 apiAddress / 非法 YAML / 空 token 全部通过，错误消息完全对齐 rainbond

## 3. EnterpriseRegions controller（CRUD）

- [x] 3.1 `EnterpriseRegionsController` 路径 `/console/enterprise/{enterprise_id}/regions` 完整 CRUD（GET 列表 / POST 添加 / GET 详情 / PUT 修改 / DELETE）；写入要求 `@RequireEnterpriseAdmin`
- [x] 3.2 `RegionDto.fromSafe / fromFull` 序列化脱敏：默认 cert/key/sslCa 输出占位长度（如 `***1234 chars***`），sys_admin 看完整内容
- [x] 3.3 `EnterpriseRegionsIntegrationTest` 2 用例：CRUD 全链路 + invalid token 400

## 4. TeamRegion controller（开通/关闭/查询）

- [x] 4.1 `TeamRegionController` 路径 `/console/teams/{team_name}/region`：query / unopen / POST 开通；带 `@RequirePerm("team_region_*")` 注解
- [x] 4.2 开通逻辑：先调 `tenantOperations.createTenant` → 再写 `tenant_region` 行；失败回滚（`@Transactional` 包裹）
- [x] 4.3 重复开通幂等：`findByTenantIdAndRegionName + active=true` 命中 → 跳过写库，返回 `已开通该集群`
- [ ] 4.4 集成测试：留待联调真实 region 时一并验证（kuship-rainbond docker 默认无对外 region API exposure，本机集成测试无法 mock 调用）

## 5. RegionLicense controller

- [x] 5.1 `RegionLicenseController` 路径 `/console/enterprise/{enterprise_id}` 下 4 个端点：licenses 列表 / cluster-id / activate (admin) / status
- [x] 5.2 全部转发给 `clusterOperations`；错误透传由 `GlobalExceptionHandler` 处理 RegionApiException 族

## 6. RegionQuery controller

- [x] 6.1 `RegionQueryController`：4 端点（全局 regions、publickey、features、protocols）；publickey 转发 `tenantOperations`、features 转发 `clusterOperations`、protocols 内嵌枚举（HTTP/HTTPS/TCP/UDP/GRPC）

## 7. ClusterNamespaces / Resource controller

- [x] 7.1 `ClusterNamespacesController`：5 端点（teams/cluster/namespaces、enterprise/regions/{rid}/namespace、resource、tenants、tenants/{tn}/limit）；setLimit `@RequireEnterpriseAdmin`
- [x] 7.2 全部转发给 `ClusterOperations`

## 8. ClusterOperations 8 method 完整实现

- [x] 8.1 `ClusterOperations` 接口扩展 8 个强类型 method（getClusterId / activateLicense / getLicenseStatus / getRegionFeatures / getRegionNamespaces / getRegionResources / setTenantLimit / listTenantsInRegion）；保留原有 default 占位 method
- [x] 8.2 `ClusterOperationsImpl @Primary @Service` 实现 8 个 method，沿用 `TenantOperationsImpl` 的 `RegionClientFactory + exchange + RegionApiResponseProcessor` 模式
- [x] 8.3 7 个新 DTO：ClusterIdResp / LicenseStatusResp / RegionFeaturesResp / NamespaceListResp / RegionResourceResp / TenantLimitReq + 复用 RegionPublickeyResp
- [ ] 8.4 ClusterOps 单测：留待 application-core 等业务 change 联调真实 region API 时统一覆盖（`MockRestServiceServer` 集成进现有 region 单测框架）

## 9. HubRegistry controller

- [x] 9.1 复用 `TeamRegistryAuth` entity 对应 `team_registry_auths` 表（不引入新表，与 rainbond 实际实现一致；design.md 决策 4 已修订）
- [x] 9.2 `HubRegistryController` 路径 `/console/hub/registry`：GET 列表 / POST 新增 / PUT 修改 / DELETE 删除（query 参数 `secret_id`）；写入要求 sys_admin
- [x] 9.3 `GET /console/hub/registry/image` —— 当前为占位实现（返回提示 + 空 list），rainbond 端依赖 hub_type 适配多种 registry HTTP API；正式 V2 调用留给 hardening change
- [ ] 9.4 集成测试：留给 hardening change（涉及多种 hub_type 适配）

## 10. TeamRegistryAuth controller

- [x] 10.1 entity `TeamRegistryAuth` 对应 `team_registry_auths`（已确认 rainbond 表名末尾 `s`）
- [x] 10.2 `TeamRegistryAuthController` 路径 `/console/teams/{team_name}/registry/auth`：5 端点（list / create / RUD），全部 `@RequirePerm("team_registry_auth")`
- [x] 10.3 `RegistryAuthService` 共享 service：参数化 tenant_id + region_name 实现 hub/team 双层语义；secret_id 用 UuidGenerator.makeUuid()
- [ ] 10.4 集成测试：参考 `EnterpriseRegionsIntegrationTest` 模式，留给 hardening change

## 11. 文档与配置

- [x] 11.1 `kuship-console/CLAUDE.md` 增加"集群管理"段落（controller 列表 + entity + token 解析 + delete evict + 14 接口骨架进度）
- [x] 11.2 `kuship-console/CLAUDE.md` 风险提示已含 password 明文（与 rainbond 一致）
- [x] 11.3 `kuship-console/README.md` 增加"集群管理 端到端验证"段（含 5 步 curl 示例）

## 12. 验证

- [x] 12.1 `mvn -pl kuship-console clean compile` BUILD SUCCESS（173 source files）
- [x] 12.2 `mvn -pl kuship-console test` 81/81 通过（含 7 个新增 region 测试 = 5 parseToken 单测 + 2 集成测试）
- [x] 12.3 真实 rainbond docker schema 校验：kuship-console 启动时 hibernate validate 全部 15 entity（13 account + RegionInfo + TeamRegistryAuth）通过
- [x] 12.4 `openspec validate migrate-console-region-cluster --strict` 通过
- [ ] 12.5 真实 region 端到端：本机 kuship-rainbond docker 没有完整的 Go region API exposure（needs k3s region runtime），手工验证留给真实部署环境
