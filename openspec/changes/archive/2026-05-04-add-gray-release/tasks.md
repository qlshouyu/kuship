## 1. 前置探查

- [x] 1.1 确认 `gray_release_record` 表存在共享 console 库（用 `mysql -e "DESC gray_release_record"` 或检查 contract-test schema dump）
- [x] 1.2 确认 `RegionClient` 是否已暴露 `apiGatewayPostProxy(region, tenantName, path, body, appId)` 方法；如缺则在 RegionClient 加该方法（约 30 行，HTTP POST + bearer token + JSON body）
- [x] 1.3 确认 `AppInstallService` 是否暴露 `skipCreateDomain` 与 `upgradeGroupId` 入参；如缺则补加可选入参（不破坏既有调用）
- [x] 1.4 检查 `OpenApiAuthFilter` 当前对 `/openapi/v1/teams/.../apps/.../gray-*` 路径无特殊跳过逻辑（应走默认链）

## 2. JPA 实体与仓库

- [x] 2.1 新建 `cn.kuship.console.modules.grayrelease` 包结构 `entity/repository/service/controller/dto`
- [x] 2.2 新增 `GrayReleaseStatus` enum (ACTIVE / COMPLETED / CANCELLED) + 字符串值映射 `active`/`completed`/`cancelled`
- [x] 2.3 新增 `ServiceMappingEntry` record (originalServiceId, grayServiceId)
- [x] 2.4 新增 `ServiceMappingsConverter implements AttributeConverter<List<ServiceMappingEntry>, String>` 用 ObjectMapper 序列化
- [x] 2.5 新增 `GrayReleaseRecord` JPA 实体，18 字段对齐 `gray_release_record` 表，`status` 用 EnumType.STRING，`serviceMappings` 用 @Convert(ServiceMappingsConverter)
- [x] 2.6 新增 `GrayReleaseRecordRepository extends JpaRepository<GrayReleaseRecord, Long>, JpaSpecificationExecutor<GrayReleaseRecord>` 含 spec 列出的 5 个 finder
- [x] 2.7 启动 contract-test profile + Hibernate validate，确认 schema 匹配（任何 column not found 立刻修字段类型）

## 3. ApisixRouteWeightUpdater

- [x] 3.1 新增 `ApisixRouteWeightUpdater` (@Component)，注入 RegionClient + ObjectMapper
- [x] 3.2 实现 `update(team, region, app, domain, originalService, grayService, ratio)` 构造 12 字段 PUT body
- [x] 3.3 backends 数组永远含 2 项 (orig, gray)，weight = (100-ratio, ratio)
- [x] 3.4 hosts/plugins/authentication 从入参 domain 透传不修改
- [x] 3.5 PUT path = `/api-gateway/v1/{tenantName}/routes/http?appID={appId}&service_alias={origAlias}&port={port}`
- [x] 3.6 非 2xx 抛 ServiceHandleException(502, "failed to update apisix route: <body>")
- [x] 3.7 单元测试 `ApisixRouteWeightUpdaterTest`：3 case (ratio=0/50/100 各自 backends + plugins 透传 + 5xx 抛错)

## 4. GrayReleaseTemplateInstaller

- [x] 4.1 新增 `GrayReleaseTemplateInstaller` (@Component)，注入 AppInstallService
- [x] 4.2 提供 `installGrayServiceGroup(team, region, app, templateId, version)` 返回新创建的 upgradeGroupId 与 service 列表
- [x] 4.3 调用 AppInstallService.install 时传 `skipCreateDomain=true` + 新生成的 `upgradeGroupId`（用 IdWorker 或 sequence）
- [x] 4.4 提供 `uninstallGrayServiceGroup(team, region, upgradeGroupId)` 用于 rollback 路径
- [x] 4.5 单元测试 `GrayReleaseTemplateInstallerTest`：mock AppInstallService 验证参数传递

## 5. GrayReleaseService 状态机

- [x] 5.1 新增 `GrayReleaseService` (@Service @Transactional)，注入 Repository + ApisixRouteWeightUpdater + GrayReleaseTemplateInstaller
- [x] 5.2 实现 `createGrayRelease` 5 步：校验 ratio + active 唯一性 + 模板实例化 + ApisixRoute 更新 + 写 record
- [x] 5.3 实现 `updateGrayRatio` 校验 ratio + record active + ApisixRoute 更新 + 改 record.grayRatio
- [x] 5.4 实现 `rollback` ApisixRoute 恢复 100:0 + uninstallGrayServiceGroup + record.status=CANCELLED；ApisixRoute 失败也仍标 CANCELLED + WARN 日志
- [x] 5.5 实现 `getInfoByService(tenantId, serviceId)` 返回 `GrayReleaseInfoDto` 含 is_gray_release / type / paired / ratio
- [x] 5.6 实现 `getInfoByUpgradeGroupId(tenantId, appId, upgradeGroupId)` 类似上但 by group
- [x] 5.7 实现 `listByApp(tenantId, appId)` / `listByTenant(tenantId, statusFilter, pageable)`
- [x] 5.8 单元测试 `GrayReleaseServiceTest`：mock 三个协作类，覆盖 5 步状态机 + 4 失败路径 + 并发拒绝

## 6. OpenAPI v1 Controller (4 端点)

- [x] 6.1 新增 `OpenApiGrayReleaseController` (@RestController, base path `/openapi/v1`)
- [x] 6.2 POST `/teams/{teamId}/regions/{regionName}/apps/{appId}/gray-release` 创建
- [x] 6.3 PUT `/teams/{teamId}/regions/{regionName}/apps/{appId}/gray-ratio` 改比例
- [x] 6.4 POST `/teams/{teamId}/regions/{regionName}/apps/{appId}/gray-rollback` 回滚
- [x] 6.5 GET `/gray-releases?tenant_id=&status=&page=&page_size=` 列表
- [x] 6.6 用 `@Operation` Swagger 注解描述各端点，与既有 OpenAPI v1 controller 风格一致
- [x] 6.7 各端点返回 `ApiResult.ok(bean | list, total)` 走 `GeneralMessageResponseBodyAdvice`

## 7. Console 内部 Controller

- [x] 7.1 新增 `GrayReleaseInfoController` (@RestController, base `/console/teams/{teamName}/regions/{regionName}/apps/{appId}`)
- [x] 7.2 POST `/gray-release-info` 接受 `{service_id?, upgrade_group_id?}` 调对应 service 方法
- [x] 7.3 与既有 appruntime 模块 controller 同样的 JWT 鉴权链 + tenant context 注入

## 8. DTO + 序列化

- [x] 8.1 新增 `GrayReleaseRecordDto`、`GrayReleaseInfoDto`、`CreateGrayReleaseRequest`、`UpdateGrayRatioRequest`
- [x] 8.2 DTO 字段名走 snake_case (Jackson @JsonProperty) 与 rainbond Python 对齐
- [x] 8.3 GrayReleaseStatus 在 DTO 序列化为小写字符串 `active`/`completed`/`cancelled`

## 9. 配置 + Native 兼容

- [x] 9.1 `application.yaml` 加 `kuship.gray-release.max-active-per-app: 1` + `apisix-route-update-timeout-seconds: 30`
- [x] 9.2 `KuShipConsoleRuntimeHints` 注册 `GrayReleaseRecord` + `ServiceMappingEntry` + `GrayReleaseStatus` 反射 hint
- [x] 9.3 `bash scripts/native-test.sh --quick` 验证 4/4 pass

## 10. 集成测试

- [x] 10.1 新增 `GrayReleaseIntegrationTest` 用 contract-test profile + Mock RegionClient
- [x] 10.2 case `create_gray_release_happy_path` 200 + DB 落 1 条 + RegionClient.apiGatewayPostProxy 调用 1 次
- [x] 10.3 case `create_gray_release_invalid_ratio_400` ratio=120 → 400 + 不落 record
- [x] 10.4 case `create_gray_release_active_exists_409` 第二次创建 → 409 + 不调模板实例化
- [x] 10.5 case `update_gray_ratio_happy_path` 30 → 50 → record.gray_ratio=50
- [x] 10.6 case `update_gray_ratio_completed_422`
- [x] 10.7 case `rollback_active_to_cancelled` ApisixRoute 调用 weight=100:0 + record.status=CANCELLED
- [x] 10.8 case `rollback_apisix_failure_still_cancels` Mock RegionClient 抛 5xx → record 仍 CANCELLED + WARN 日志
- [x] 10.9 case `list_gray_releases_pagination` 25 条 → page=2, size=10 → 10 条
- [x] 10.10 case `gray_release_info_by_service_id` 返回 is_gray_release=true + type=original
- [x] 10.11 case `gray_release_info_non_gray` 返回 is_gray_release=false

## 11. 文档

- [x] 11.1 `kuship-console/CLAUDE.md` 新增 "灰度发布（add-gray-release）" 段落
- [x] 11.2 含状态机图（ASCII 或 mermaid 风格）
- [x] 11.3 含 4 OpenAPI 端点表 + 1 console 端点
- [x] 11.4 含 RegionClient `/api-gateway/v1/.../routes/http` 契约说明
- [x] 11.5 含跨服务事务限制 + 运维 WARN 日志监控指引
- [x] 11.6 含后续 hardening 路径：add-grayrelease-header-routing / add-grayrelease-analytics / enforce-grayrelease-uniqueness

## 12. 验证收尾

- [x] 12.1 `mvn test` 全 GREEN（既有 146 + 本 change 新增约 15-18 case ≈ 161+）
- [x] 12.2 `bash scripts/native-test.sh --quick` 全 GREEN
- [x] 12.3 `openspec validate add-gray-release --strict` 通过
- [x] 12.4 手工启动 contract-test profile + curl 4 个 OpenAPI 端点 happy path 验证响应格式
