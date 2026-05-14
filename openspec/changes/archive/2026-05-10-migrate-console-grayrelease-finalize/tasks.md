# Tasks — migrate-console-grayrelease-finalize

## 1. 校验既有依赖与锚点

- [ ] 1.1 确认 `add-gray-release` 已归档（`openspec/changes/archive/2026-05-04-add-gray-release/`），`GrayReleaseRecord` / `GrayReleaseStatus` / `GrayReleaseRecordRepository` / `GrayReleaseService` / `GrayReleaseTemplateInstaller`（stub 形态） / `ApisixRouteWeightUpdater` 6 件已在 main 分支
- [ ] 1.2 确认 rainbond 锚点 `reference/rainbond-console/www/apiclient/regionapi.py:2937-2960`（`create_app_gray_release` / `update_app_gray_release` / `operate_app_gray_release`）签名与 design.md "Region API URL 表" 一致；如 rainbond 端签名变更，先与作者对齐
- [ ] 1.3 确认 `GrayReleaseTemplateInstaller` 当前行为：`installGrayServiceGroup` 仅生成合成 service_id / upgrade_group_id 不调 region；`uninstallGrayServiceGroup` 仅 WARN 日志不调 region；本 change 在此基础上**追加** region 调用，**不删** stub 用于"本地 service_group 批量 INSERT" 的 WARN 日志（属决策 2 仍 stub 范围）
- [ ] 1.4 确认 `kuship-console/src/main/java/cn/kuship/console/modules/grayrelease/api/` 目录尚不存在；本 change 新建该包

## 2. 新建 `GrayReleaseOperations` 接口与 Impl

- [ ] 2.1 新建 `modules/grayrelease/api/GrayReleaseOperations.java`，3 个 method 签名（参考 design.md "Region API URL 表"）：
  - `Map<String, Object> createAppGrayRelease(String regionName, String tenantName, Integer regionAppId, Map<String, Object> body)`
  - `Map<String, Object> updateAppGrayRelease(String regionName, String tenantName, Integer regionAppId, Map<String, Object> body)`
  - `Map<String, Object> operateAppGrayRelease(String regionName, String tenantName, Integer regionAppId, String namespace, String operationMethod)`
  - 接口顶部 javadoc 注明：与 `ApisixRouteWeightUpdater` 的职责分层（命令面 vs 数据面）+ rainbond 3 个 region method 锚点行号
- [ ] 2.2 新建 `modules/grayrelease/api/GrayReleaseOperationsImpl.java`：
  - `@Primary @Service` 注解
  - 注入 `RegionClientFactory` + `TenantsRepository` + `tools.jackson.databind.ObjectMapper`
  - `resolveNamespace(tenantName)` helper：`tenantsRepo.findByTenantName(tn).map(Tenants::getNamespace).filter(StringUtils::hasText).orElse(tenantName)`
  - `createAppGrayRelease`：URL = `/v2/tenants/{namespace}/apps/{regionAppId}/gray_release`，POST + body JSON；用 `processor.extractBean(resp, Map.class, ...)` 取 region 返回的 bean
  - `updateAppGrayRelease`：URL 同上，PUT + body JSON；用 `processor.extractBean(...)`
  - `operateAppGrayRelease`：URL = `/v2/tenants/{namespace}/apps/{regionAppId}/operate_gray_release` + query string `?namespace={ns}&app_id={regionAppId}&operation_method={opMethod}`，PUT 无 body；query value URL encode；用 `processor.checkStatus(...)` 取 root JsonNode 的 `data` 节点
- [ ] 2.3 单测 `GrayReleaseOperationsImplTest`：≥ 7 用例
  - `createAppGrayRelease_happy`：MockRestServiceServer 断言 URL = `/v2/tenants/<ns>/apps/123/gray_release` + 方法 POST + body JSON 含 `template_id`
  - `createAppGrayRelease_namespace_fallback_to_tenant_name`：`Tenants.namespace` 为 null 时用 `tenant_name`
  - `createAppGrayRelease_propagates_region_5xx_as_RegionApiException`：MockRestServiceServer 返 503 + region general_message → 抛 `RegionApiException(503,...)`
  - `updateAppGrayRelease_happy`：URL 同 create + 方法 PUT
  - `updateAppGrayRelease_propagates_4xx`：region 返 400 → 抛 `RegionApiException(400,...)`
  - `operateAppGrayRelease_happy_rollback`：URL = `/v2/tenants/<ns>/apps/123/operate_gray_release?namespace=<ns>&app_id=123&operation_method=rollback`
  - `operateAppGrayRelease_query_string_url_encoded`：namespace 含 `-`/数字时 URL encode 不乱码

## 3. 改造 `GrayReleaseTemplateInstaller` 去 region 调用 stub

- [ ] 3.1 在 `GrayReleaseTemplateInstaller` 注入 `GrayReleaseOperations` + `TenantsRepository`；新增 ctor 参数 + 字段
- [ ] 3.2 新增 `@Value("${kuship.gray-release.skip-region-template-install:false}") boolean skipRegionTemplateInstall` 字段（决策 5 降级阀）
- [ ] 3.3 修改 `installGrayServiceGroup` 签名 / 行为：
  - 新增参数 `String regionName` + `Integer regionAppId`（现有 caller `GrayReleaseService.createGrayRelease` 已持有 `regionName` + `appId`，需先在 service 层做 region_app_id 查询；如 `region_app` 映射查询逻辑未在 kuship 实现，本 change 临时直接复用 `appId` 作 `regionAppId`，加 TODO 注释指向 `migrate-console-app-install` 引入正式映射）
  - `skipRegionTemplateInstall=false` 时：构造 region body（含 `template_id` / `version` / `market_name` / `install_from_cloud`）→ 调 `grayReleaseOps.createAppGrayRelease(regionName, tenantName, regionAppId, body)` → 解析响应取 `original_service_id` / `gray_service_id` / `original_upgrade_group_id` / `gray_upgrade_group_id` 字段；缺失字段 fallback 为合成 id（避免 region 实现差异时崩溃）
  - `skipRegionTemplateInstall=true` 时：保留 `add-gray-release` 原合成 id 行为；用于无 region 集成测试 / 离线开发
  - **不删** "本地 service_group / tenant_service 批量 INSERT 仍 stub" 的 WARN 日志（决策 2），文案改为 `[GrayRelease][stub] local service_group write bypassed; tenant=... app=... pending migrate-console-app-install`，与 region 调用 WARN 区分
- [ ] 3.4 修改 `uninstallGrayServiceGroup` 签名 / 行为：
  - 新增参数 `String regionName` + `String tenantName` + `Integer regionAppId` + `String namespace`
  - `skipRegionTemplateInstall=false` 时调 `grayReleaseOps.operateAppGrayRelease(regionName, tenantName, regionAppId, namespace, "rollback")`；调用失败仅 WARN，不抛（与 `ApisixRouteWeightUpdater` rollback 路径行为对齐 —— record 已经要写 CANCELLED 不阻塞）
  - `skipRegionTemplateInstall=true` 时保留 WARN 日志原状
- [ ] 3.5 在 `Result` record 增加可选字段 `String regionResponse`（透传 region 原始响应给上层调试用）；现有 caller 不读该字段不影响兼容性 —— 或直接复用既有字段集，本字段属可选改进，可不实施

## 4. 改造 `GrayReleaseService` 接线

- [ ] 4.1 `createGrayRelease`：调 `installer.installGrayServiceGroup(...)` 时改用新签名（追加 `regionName` + `regionAppId`）；事务内 region 调用先于 record INSERT，region 失败 → 事务自动回滚（无 record 落库 + 无 ApisixRoute 切权重）
- [ ] 4.2 `updateGrayRatio`：在调 `apisixUpdater.update(...)` 之**后**、`record.setGrayRatio + repo.save` 之**前**追加：
  - `if (!skipRegionTemplateInstall) { grayReleaseOps.updateAppGrayRelease(regionName, tenantName, regionAppId, Map.of("gray_ratio", newRatio)); }`
  - region 调用失败抛 `RegionApiException` → 事务自动回滚 + ApisixRoute 已切回（接受短暂不一致，运维监控 grep `[GrayRelease].*region update failed` 重试）
  - 备选：把 `updateAppGrayRelease` 调用放在 `apisixUpdater.update` 之前（顺序与 create 一致）；本 change 在 design.md 决策 5 选择"先 apisix 后 region"，理由：ApisixRoute 是数据面（流量切换更紧急），region updateAppGrayRelease 是命令面（让 region 灰度对象的 desired_replicas / strategy 同步新 ratio，可容忍短暂滞后）
- [ ] 4.3 `rollback`：调 `installer.uninstallGrayServiceGroup(...)` 时改用新签名；保持 `add-gray-release` 既定行为（region 失败仍把 record 写 CANCELLED + WARN 不抛）

## 5. 配置项与文档

- [ ] 5.1 在 `application.yaml` 新增配置项 `kuship.gray-release.skip-region-template-install: false`（默认 false）；contract-test profile 同样默认 false（让集成测试通过 `@MockitoBean GrayReleaseOperations` 验证 region 调用接线）
- [ ] 5.2 不修改 `kuship.gray-release.skip-apisix-update` / `kuship.gray-release.max-active-per-app` 既有配置项语义
- [ ] 5.3 SecurityConfig：本 change **不**修改 SecurityConfig（既有 OpenAPI / console endpoint 鉴权链不变）

## 6. 集成测试

实施合并到一个 `GrayReleaseFinalizeIntegrationTest` 类（`@SpringBootTest` + `@ActiveProfiles({"local","contract-test"})` + `@MockitoBean GrayReleaseOperations` + `@MockitoBean GatewayOperations` + `JdbcTemplate seed`），降低 fixture 重复成本。≥ 5 用例：

- [ ] 6.1 `createGrayRelease_happy_path`：seed `tenants` + `service_group` 行；mock `GrayReleaseOperations.createAppGrayRelease` 返 `{original_service_id:"o1",gray_service_id:"g1",original_upgrade_group_id:1,gray_upgrade_group_id:2}`；mock `GatewayOperations.apiGatewayProxy` 返 `Map.of()`；调 OpenAPI POST `/openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-release`；ArgumentCaptor 断言 region body 含 `template_id` + `gray_ratio`；响应 200 + `data.bean` 含 record 字段 + `gray_release_record` 表新增 1 行 status=ACTIVE
- [ ] 6.2 `updateGrayRatio_propagates_to_region`：先 6.1 build state；调 OpenAPI PUT `/gray-ratio` 改 `gray_ratio=70`；ArgumentCaptor 断言 `GrayReleaseOperations.updateAppGrayRelease` 被调用一次 + body 含 `gray_ratio:70`；同时 `GatewayOperations.apiGatewayProxy` 也被调用（与既有 ApisixRoute 权重更新协作）；响应 200
- [ ] 6.3 `rollback_invokes_operate_with_rollback_method`：先 6.1 build state；调 OpenAPI POST `/gray-rollback`；ArgumentCaptor 断言 `GrayReleaseOperations.operateAppGrayRelease` 被调一次 + 第 5 参数 `operationMethod="rollback"`；响应 200 + 表 record.status="cancelled"
- [ ] 6.4 `region_5xx_transparently_propagates_via_RegionApiException`：mock `GrayReleaseOperations.createAppGrayRelease` 抛 `RegionApiException(503, "region down", "集群不可用")`；调 OpenAPI POST `/gray-release`；断言响应 HTTP 503 + body `{detail:"region down", code:503}`（OpenAPI v1 错误格式）；并断言 `gray_release_record` 表**未**新增行（事务回滚生效）+ `GatewayOperations.apiGatewayProxy` **未**被调用（顺序保证）
- [ ] 6.5 `coexistence_with_apisix_route_weight_updater`：mock 双 `@MockitoBean` 全 happy；用 `InOrder` 验证 `createGrayRelease` 流程内调用顺序：`GrayReleaseOperations.createAppGrayRelease` → `GatewayOperations.apiGatewayProxy`（顺序由 `GrayReleaseService.createGrayRelease` 决定 —— installer 先于 apisix 先于 record save，与 `add-gray-release` 既定串联一致）
- [ ] 6.6（可选）`skip_region_template_install_falls_back_to_synthetic_ids`：profile override `kuship.gray-release.skip-region-template-install=true`；mock 不让 `createAppGrayRelease` 被调；调 OpenAPI POST `/gray-release`；断言 `GrayReleaseOperations.createAppGrayRelease` **未**被调用 + record 仍落库（合成 id 路径）

## 7. 文档与归档

- [ ] 7.1 更新 `kuship-console/CLAUDE.md` 在"灰度发布（add-gray-release）"段后追加"灰度发布 region 通信收尾（migrate-console-grayrelease-finalize）"段：列 `GrayReleaseOperations` 3 个 method、URL 路径、与 `add-gray-release` ApisixRouteWeightUpdater 的职责分层、与 `migrate-console-app-install` 的边界（仍 stub 范围）；同步路线图接口表新增 `GrayReleaseOperations` 行（`modules/grayrelease/api/`，3/3 完成）
- [ ] 7.2 路线图 `migrate-region-coverage-roadmap` 的 Requirement 表中把 "migrate-console-grayrelease-finalize" 行的 method 计数列标注为已完成；同步更新 design.md 决策 5 "推进顺序" 图中 P1 #5 分支为 ✓（归档时执行）
- [ ] 7.3 在 `add-grayrelease-promote-endpoint` / `add-grayrelease-lifecycle-endpoints` / `migrate-console-app-install` 三个未来 change（如已存在 issue / proposal）描述中追加引用本 change："region 调用通路已由 migrate-console-grayrelease-finalize 落地，新端点直接复用 GrayReleaseOperations.operateAppGrayRelease(... operationMethod=...)"
- [ ] 7.4 `GrayReleaseTemplateInstaller` 内 javadoc 更新：明确"region 调用 stub 已移除（migrate-console-grayrelease-finalize）；本地 service_group / tenant_service 批量 INSERT 仍 stub（待 migrate-console-app-install）"

## 8. 编译 / 重启 / 联动验证

- [ ] 8.1 `cd kuship-console && mvn -DskipTests package` 编译通过
- [ ] 8.2 `mvn test -Dtest='GrayReleaseOperationsImplTest,GrayReleaseFinalizeIntegrationTest,GrayReleaseServiceTest'` 全部通过（含 `add-gray-release` 既有 `GrayReleaseServiceTest` 不能因签名改动 break；如旧测试因 `installGrayServiceGroup` 新签名失败，先在 task §3 内同步修补 mock）
- [ ] 8.3 `mvn test`（全量 102 + 本 change 新增）零回归
- [ ] 8.4 重启 console；`curl -s -H "Authorization: Bearer $PAT" -X POST "http://localhost:8080/openapi/v1/teams/<tid>/regions/rainbond/apps/<aid>/gray-release" -d '{"template_id":"t1","domain_name":"foo.example.com","gray_ratio":20}'` 返 200 + region 端日志能看到 `POST /v2/tenants/<ns>/apps/<aid>/gray_release`（**需用户本地起 console + region 后联动**）
- [ ] 8.5 `curl -X PUT .../gray-ratio -d '{"template_id":"t1","gray_ratio":70}'` 返 200 + region 端日志能看到 `PUT /v2/tenants/<ns>/apps/<aid>/gray_release` body 含 `gray_ratio:70`（**需用户联动**）
- [ ] 8.6 `curl -X POST .../gray-rollback -d '{"template_id":"t1"}'` 返 200 + region 端日志能看到 `PUT /v2/tenants/<ns>/apps/<aid>/operate_gray_release?operation_method=rollback`（**需用户联动**）
- [ ] 8.7 region 端故意返 5xx（如 stop region 容器后再调 create-gray-release）→ 返 503 + general_message 形状 + `gray_release_record` 表无新行（事务回滚验证，**需用户联动**）
