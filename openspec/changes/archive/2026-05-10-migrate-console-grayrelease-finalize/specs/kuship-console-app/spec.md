## ADDED Requirements

### Requirement: 灰度发布 region 通信收尾

kuship-console SHALL 新建 `GrayReleaseOperations` 接口（位于 `cn.kuship.console.modules.grayrelease.api`，**非 14 核心 Operations**，与 `infrastructure/region/api/` 区分；与 `add-gray-release` 已落地的 `ApisixRouteWeightUpdater` 形成"命令面 vs 数据面"职责分层），含 3 个 region API 透传 method —— `createAppGrayRelease` / `updateAppGrayRelease` / `operateAppGrayRelease`，分别 1:1 透传 rainbond `regionapi.py:create_app_gray_release / update_app_gray_release / operate_app_gray_release`；并落地 `GrayReleaseOperationsImpl @Primary @Service`。本 Requirement 同时移除 `GrayReleaseTemplateInstaller` 的 region 调用 stub —— 创建灰度时调 `createAppGrayRelease` 让 region 端真实创建灰度 service group；回滚时调 `operateAppGrayRelease(operationMethod="rollback")` 让 region 端卸载；ratio 变更时调 `updateAppGrayRelease` 同步给 region 端。本 Requirement 同时锁定与未来 `migrate-console-app-install` 子 change 的解耦边界：本 change 仅做 region 通信，**本地 service_group / tenant_service / service_group_relation 批量 INSERT 仍走 stub**（保留 `[GrayRelease][stub] local service_group write bypassed` WARN 日志），待 `migrate-console-app-install` 落地后再二次扩 stub 调真实 install service。

业务规则：

- `GrayReleaseOperations.createAppGrayRelease(regionName, tenantName, regionAppId, body)` MUST 把路径段 `tenant_name` 替换为 `Tenants.namespace`（缺失时回退 `tenant_name`），与 rainbond `regionapi.py:2940` `region_tenant_name` 行为一致；URL 形如 `/v2/tenants/{namespace}/apps/{regionAppId}/gray_release`；HTTP POST + body JSON
- `GrayReleaseOperations.updateAppGrayRelease(regionName, tenantName, regionAppId, body)` MUST 与 `createAppGrayRelease` 同 URL，HTTP PUT + body JSON；body 至少包含 `gray_ratio` 字段（caller 传入）
- `GrayReleaseOperations.operateAppGrayRelease(regionName, tenantName, regionAppId, namespace, operationMethod)` MUST 拼装 query string `?namespace={namespace}&app_id={regionAppId}&operation_method={operationMethod}`（key/value 全部 URL encode）；URL 形如 `/v2/tenants/{namespace}/apps/{regionAppId}/operate_gray_release?...`；HTTP PUT 无 body
- `GrayReleaseTemplateInstaller.installGrayServiceGroup` MUST 调 `GrayReleaseOperations.createAppGrayRelease`，解析响应字段 `original_service_id` / `gray_service_id` / `original_upgrade_group_id` / `gray_upgrade_group_id` 回填给上层；任一字段缺失时 fallback 为合成 id（兼容 region 实现差异）
- `GrayReleaseTemplateInstaller.uninstallGrayServiceGroup` MUST 调 `GrayReleaseOperations.operateAppGrayRelease(... operationMethod="rollback")`；调用失败仅 WARN 日志不抛（与 `ApisixRouteWeightUpdater` rollback 路径行为对齐，避免 record 卡 ACTIVE）
- `GrayReleaseService.updateGrayRatio` MUST 在调 `ApisixRouteWeightUpdater.update` 之**后**、`record.setGrayRatio + repo.save` 之**前**调 `GrayReleaseOperations.updateAppGrayRelease`；region 调用失败 → 事务回滚（数据面已切但 record 不落库）
- `GrayReleaseService.createGrayRelease` MUST 保持既定调用顺序：`installer.installGrayServiceGroup`（含 region createAppGrayRelease）→ `ApisixRouteWeightUpdater.update` → `repo.save(record)`；任一阶段失败抛异常 → 事务自动回滚
- 配置项 `kuship.gray-release.skip-region-template-install`（默认 `false`）MUST 提供降级阀：true 时跳过 `GrayReleaseOperations` 调用、回退到 `add-gray-release` 既定的合成 id 行为；用于无 region 集成测试 / 离线开发
- region 异常 MUST 透传 `RegionApiException`，由 `GlobalExceptionHandler` 自动映射为 general_message 形状响应；HTTP 状态码 = region httpStatus
- 本 change MUST NOT 新增 `get_app_gray_release` region 调用 method 与 console URL；UI "判断按钮态" 场景由现有 `GrayReleaseInfoController.grayReleaseInfo` 读本地 `GrayReleaseRecord` 覆盖
- 本 change MUST NOT 新增 / 修改 `GrayReleaseRecord` 之外的本地表
- 本 change MUST NOT 实现本地 `tenant_service` / `service_group_relation` / `tenant_service_env_var` / `tenant_services_port` / `tenant_service_volume` 批量 INSERT；这些行为属 `migrate-console-app-install` 范围
- 本 change MUST NOT 修改 `add-gray-release` 既定 5 个 endpoint（4 OpenAPI v1 + 1 console）的 URL / 鉴权 / 响应形状
- 本 change MUST NOT 修改 `ApisixRouteWeightUpdater` 调用顺序 / body 形状 / 配置项语义
- `GrayReleaseTemplateInstaller` MUST 保留 `[GrayRelease][stub] local service_group write bypassed; tenant=... app=... pending migrate-console-app-install` WARN 日志，便于运维监控本地写 stub 触达频率，待 `migrate-console-app-install` 落地后日志自然消失

#### Scenario: 创建灰度发布 happy path

- **GIVEN** team `t1` 的 `Tenants.namespace="ns-prod"`，team 下应用 `app_id=123`，`region_app_id=123`，region `rainbond` 已就绪
- **WHEN** 调用 OpenAPI `POST /openapi/v1/teams/t1/regions/rainbond/apps/123/gray-release` 携带 `{template_id:"tpl-a",template_version:"v1",domain_name:"foo.example.com",gray_ratio:20}`
- **THEN** kuship 先调 region `POST /v2/tenants/ns-prod/apps/123/gray_release` body 含 `template_id="tpl-a"` + `gray_ratio=20`
- **AND** region 返 `{"bean":{"original_service_id":"o1","gray_service_id":"g1","original_upgrade_group_id":1,"gray_upgrade_group_id":2}}`
- **AND** 然后调 `ApisixRouteWeightUpdater.update(... ratio=20)` 切换 ApisixRoute 流量
- **AND** 最后写 `gray_release_record` 行 status=`active` + `original_service_id="o1"` + `gray_service_id="g1"`
- **AND** 响应 200 + body 透传 record 字段（OpenAPI v1 不走 general_message 包装）

#### Scenario: 更新灰度比例同步给 region

- **GIVEN** 已有 ACTIVE 灰度 record（tenant_id=`t1`，app_id=123，gray_ratio=20）
- **WHEN** 调用 OpenAPI `PUT /openapi/v1/teams/t1/regions/rainbond/apps/123/gray-ratio` 携带 `{template_id:"tpl-a",gray_ratio:70}`
- **THEN** kuship 先调 `ApisixRouteWeightUpdater.update(... ratio=70)` 切换 ApisixRoute 权重为 30:70
- **AND** 然后调 region `PUT /v2/tenants/ns-prod/apps/123/gray_release` body 含 `gray_ratio=70`
- **AND** 最后更新 `gray_release_record.gray_ratio=70` + `update_time` 刷新
- **AND** 响应 200

#### Scenario: 回滚灰度调 operate 的 rollback 子动作

- **GIVEN** 已有 ACTIVE 灰度 record（tenant_id=`t1`，app_id=123）
- **WHEN** 调用 OpenAPI `POST /openapi/v1/teams/t1/regions/rainbond/apps/123/gray-rollback` 携带 `{template_id:"tpl-a"}`
- **THEN** kuship 先调 `ApisixRouteWeightUpdater.update(... ratio=0)` 把流量切回原版本
- **AND** 然后调 region `PUT /v2/tenants/ns-prod/apps/123/operate_gray_release?namespace=ns-prod&app_id=123&operation_method=rollback`（query 参数全部 URL encode）
- **AND** 最后更新 `gray_release_record.status="cancelled"` + `gray_ratio=0` + `update_time` 刷新
- **AND** 响应 200 + body 透传 record + `rolled_back=true`
- **AND** 即使 region `operate_app_gray_release` 调用失败（5xx），record 仍写 `cancelled` + WARN 日志（与 `ApisixRouteWeightUpdater` rollback 路径行为对齐，不阻塞下次 create）

#### Scenario: region 异常透传

- **GIVEN** region 端 `POST /v2/tenants/ns-prod/apps/123/gray_release` 因后端不可用返 503
- **WHEN** 调用 OpenAPI `POST /openapi/v1/teams/t1/regions/rainbond/apps/123/gray-release`
- **THEN** kuship 抛 `RegionApiException(httpStatus=503, code=503, msgShow="集群不可用")`
- **AND** OpenApi 异常处理器映射为 HTTP 503 + body `{"detail":"region down","code":503}`（OpenAPI v1 错误格式，与 console general_message 不同）
- **AND** `gray_release_record` 表**未**新增行（事务回滚验证）
- **AND** `ApisixRouteWeightUpdater.update` **未**被调用（installer → apisix → record 顺序保证：region 失败时 apisix 还没轮到）

#### Scenario: 与 ApisixRouteWeightUpdater 协作的事务串联

- **GIVEN** 已 mock `GrayReleaseOperations` 与 `GatewayOperations` 全部 happy 返回
- **WHEN** 调用 OpenAPI `POST /openapi/v1/teams/t1/regions/rainbond/apps/123/gray-release`
- **THEN** 串联调用顺序严格为：`GrayReleaseOperations.createAppGrayRelease`（命令面：region 创建灰度 service group） → `GatewayOperations.apiGatewayProxy`（数据面：通过 `ApisixRouteWeightUpdater` 切 ApisixRoute 权重） → `GrayReleaseRecordRepository.save`（本地落库）
- **AND** 三步同一 `@Transactional`：任一失败回滚（含 region command 失败 + apisix 数据面切换失败 + DB 主键冲突等场景）
- **AND** `GrayReleaseTemplateInstaller` 仍输出 `[GrayRelease][stub] local service_group write bypassed; pending migrate-console-app-install` WARN（决策 2 仍 stub 范围）

#### Scenario: 与未来 migrate-console-app-install 子 change 的边界

- **GIVEN** 本 change 已落地，region 调用 stub 已移除，但本地 service_group / tenant_service 批量 INSERT 仍走 stub
- **WHEN** 后续 `migrate-console-app-install` 子 change 落地 `AppInstallService.installApp` 完整链路
- **THEN** 该子 change SHALL 在 `GrayReleaseTemplateInstaller.installGrayServiceGroup` 内追加调 `AppInstallService.installApp` 完成本地批量 INSERT，**不替换** `GrayReleaseOperations.createAppGrayRelease` 调用，**不修改** `GrayReleaseOperations` 接口签名
- **AND** 该子 change SHALL 删除 `[GrayRelease][stub] local service_group write bypassed` WARN 日志（stub 行为消失）
- **AND** 该子 change SHALL NOT 修改 `add-gray-release` 5 个 endpoint 契约或本 change `GrayReleaseOperationsImpl` 的 region URL 拼装
- **AND** 业务级灰度运行时状态查询（如灰度 deployment 副本健康度）SHALL 由独立 hardening 提案 `add-grayrelease-runtime-status` 承载，不污染 `GrayReleaseOperations` 接口

#### Scenario: 降级阀跳过 region 调用回退合成 id

- **GIVEN** 配置 `kuship.gray-release.skip-region-template-install=true`（用于无 region 集成测试 / 离线开发）
- **WHEN** 调用 OpenAPI `POST /openapi/v1/teams/t1/regions/rainbond/apps/123/gray-release`
- **THEN** kuship **未**调 `GrayReleaseOperations.createAppGrayRelease`
- **AND** `GrayReleaseTemplateInstaller` 回退到 `add-gray-release` 原合成 id 行为（生成 32-char 随机 service_id + 6 位随机 upgrade_group_id）
- **AND** record 仍落库（验证降级路径不破坏控制平面骨架）
- **AND** 响应 200
