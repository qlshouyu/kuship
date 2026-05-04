## ADDED Requirements

### Requirement: GrayReleaseRecord JPA 实体

kuship-console SHALL 提供 `cn.kuship.console.modules.grayrelease.entity.GrayReleaseRecord` JPA 实体，1:1 映射共享 console 数据库 `gray_release_record` 表的全部 18 个字段；表结构 SHALL NOT 由 kuship-console 修改（`ddl-auto=validate` 模式校验失败 SHALL 拒绝启动）；字段 `service_mappings` 在数据库为 TEXT 但 Java 侧 SHALL 暴露为 `List<ServiceMappingEntry>`，通过 JPA `@Convert` 用 Jackson 3 序列化往返。

#### Scenario: 启动时 schema validate 通过
- **WHEN** kuship-console 在 contract-test profile 启动并连接到含 `gray_release_record` 表的 console 数据库
- **THEN** Hibernate `validate` SHALL 不报告任何字段或列差异
- **AND** 应用启动正常，Spring 上下文无 BeanCreationException

#### Scenario: service_mappings 序列化往返
- **WHEN** 测试代码 `repo.save(record.withMappings(List.of(new ServiceMappingEntry("orig-svc-1", "gray-svc-1"))))`
- **AND** 重新 `repo.findById(record.getId()).get().getServiceMappings()`
- **THEN** 返回的 list size SHALL = 1，且元素字段 originalServiceId/grayServiceId SHALL 与写入时一致

### Requirement: GrayReleaseService 创建灰度发布

`GrayReleaseService.createGrayRelease(team, regionName, user, app, templateId, domainName, grayRatio, version)` SHALL 完成以下 5 步：(1) 校验 grayRatio ∈ [0, 100] 整数；(2) 校验同一 (tenantId, appId) 不存在 status=ACTIVE 的记录；(3) 通过 `GrayReleaseTemplateInstaller` 调 `AppInstallService` 安装灰度 service group（skipCreateDomain=true）；(4) 调 `ApisixRouteWeightUpdater` 把指定 domain 的 ApisixRoute 后端权重设为 (100-grayRatio):grayRatio；(5) 写入 GrayReleaseRecord(status=ACTIVE)。前 4 步任一失败 SHALL 让整个事务回滚，无残留 record；步骤 4 失败时 SHALL 返回业务错误 `failed to update apisix route` 但允许调用方稍后调 `update-gray-ratio` 端点重试。

#### Scenario: gray_ratio 越界
- **WHEN** 客户端 POST `/openapi/v1/teams/{tid}/regions/{rn}/apps/{aid}/gray-release` body 含 `gray_ratio=120`
- **THEN** 端点 SHALL 返回 400 + `msg_show="灰度比例必须在0-100之间"`
- **AND** 数据库 SHALL 不插入新 GrayReleaseRecord

#### Scenario: 同 app 已有 active 拒绝创建
- **WHEN** app A 已存在 1 条 status=ACTIVE 的 GrayReleaseRecord
- **AND** 客户端再次 POST `/openapi/v1/.../apps/A/gray-release`
- **THEN** 端点 SHALL 返回 409 + `msg_show="该应用已存在进行中的灰度发布"`
- **AND** 不调用 AppInstallService 或 ApisixRouteWeightUpdater

#### Scenario: 模板实例化失败回滚
- **WHEN** AppInstallService 抛 ServiceHandleException
- **THEN** GrayReleaseService SHALL 不写入新 record
- **AND** 不调用 ApisixRouteWeightUpdater
- **AND** 端点 SHALL 透传该 exception 的状态码

### Requirement: GrayReleaseService 更新灰度比例

`GrayReleaseService.updateGrayRatio(team, regionName, app, recordId, newRatio)` SHALL 校验 newRatio ∈ [0, 100]、record 存在且 status=ACTIVE，然后调 `ApisixRouteWeightUpdater` 把 ApisixRoute 后端权重重设为 (100-newRatio):newRatio，最后写回 record.grayRatio = newRatio。完成后端点 SHALL 返回 200 + 更新后的 record DTO。

#### Scenario: 已 completed 不可改
- **WHEN** record status=COMPLETED
- **AND** 客户端 PUT `/openapi/v1/.../apps/{aid}/gray-ratio` 试图改 ratio
- **THEN** 端点 SHALL 返回 422 + `msg_show="该灰度发布已结束，不可修改"`

#### Scenario: ratio=100 自动 promote
- **WHEN** 客户端调 update-gray-ratio newRatio=100
- **THEN** ApisixRouteWeightUpdater SHALL 把权重设为 0:100
- **AND** record.grayRatio SHALL = 100，但 status SHALL 仍为 ACTIVE（status 推进到 COMPLETED 由 rollback 或单独 promote 端点完成；本端点不自动 promote，与 rainbond Python 行为对齐）

### Requirement: GrayReleaseService 回滚

`GrayReleaseService.rollback(team, regionName, app, recordId)` SHALL 把 ApisixRoute 权重恢复为 100:0（流量全回原始版本），删除灰度 service group 关联的 component/domain，并把 record.status 推进为 CANCELLED。如 rollback 时 ApisixRouteWeightUpdater 失败 SHALL 仍把 record.status 写为 CANCELLED 并返回 WARN 提示 `apisix-route 权重恢复失败，请手动更新`，避免业务卡死。

#### Scenario: 正常回滚
- **WHEN** record status=ACTIVE，客户端 POST `/openapi/v1/.../apps/{aid}/gray-rollback`
- **THEN** ApisixRouteWeightUpdater SHALL 收到调用 newBackends=[(orig, 100), (gray, 0)]
- **AND** AppInstallService.uninstall (or equivalent) SHALL 清理灰度 upgrade_group 下所有 service
- **AND** record.status 写为 CANCELLED + record.gray_ratio = 0
- **AND** 端点返回 200

#### Scenario: 回滚 already cancelled 幂等
- **WHEN** record status=CANCELLED，再次调 rollback
- **THEN** 端点 SHALL 返回 200（幂等）+ 不改 record

### Requirement: ApisixRouteWeightUpdater 单职责

`ApisixRouteWeightUpdater.update(team, region, app, domain, originalService, grayService, ratio)` SHALL 构造 PUT body（含 namespace / name / app_id / section_name / gateway_name / gateway_namespace / hosts / rules / backends / plugins / websocket / authentication 12 字段），调用 `RegionClient.apiGatewayPostProxy(region, tenantName, "/api-gateway/v1/{tenantName}/routes/http?appID={appId}&service_alias={origAlias}&port={port}", body, appId)`，把 backends 数组生成为：

```json
[
  {"service_name": "<orig-svc-name>", "service_port": <port>, "weight": 100 - ratio},
  {"service_name": "<gray-svc-name>", "service_port": <port>, "weight": ratio}
]
```

非 2xx 响应 SHALL 抛 `ServiceHandleException(502, "failed to update apisix route: <body>")`，不静默吞错。

#### Scenario: 100/0 比例时 backends 完整
- **WHEN** ratio=0
- **THEN** PUT body backends SHALL 包含 2 项，weight 分别 100 与 0（不是省略 gray 那一项）

#### Scenario: 50/50 比例时 backends 加权
- **WHEN** ratio=50
- **THEN** PUT body backends SHALL 包含 2 项，weight 各 50

#### Scenario: hosts/plugins/authentication 透传
- **WHEN** 既有 domain 配置含 hosts=["api.example.com"], plugins=[{name: "rate-limit", ...}], authentication={type: "jwt", ...}
- **THEN** PUT body 这 3 字段 SHALL 与原 domain 完全一致（仅替换 backends）

### Requirement: OpenAPI v1 4 端点暴露

kuship-console SHALL 在 `OpenApiGrayReleaseController` 暴露 4 个端点，路径与请求/响应包结构与 rainbond-console 1:1 兼容；鉴权走既有 `OpenApiAuthFilter`；端点列表：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-release` | 创建灰度发布 |
| PUT | `/openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-ratio` | 调整灰度比例 |
| POST | `/openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-rollback` | 回滚灰度 |
| GET | `/openapi/v1/gray-releases` | 列表（支持 tenant_id / status / page / page_size 过滤） |

响应包结构 SHALL 是既有 `general_message` 风格 `{code, msg, msg_show, data: {bean | list}}`。

#### Scenario: 创建端点 happy path
- **WHEN** 客户端 POST `/openapi/v1/teams/{tid}/regions/{rn}/apps/{aid}/gray-release` body `{template_id, domain_name, gray_ratio: 30}`
- **AND** 校验通过且模板实例化成功且 ApisixRoute 更新成功
- **THEN** 响应 SHALL 是 200 + `data.bean` 含创建的 GrayReleaseRecord 序列化结果

#### Scenario: 列表分页
- **WHEN** 数据库已有 25 条 record，客户端 GET `/openapi/v1/gray-releases?page=2&page_size=10`
- **THEN** 响应 SHALL 是 200 + `data.list` 含 10 条 + `data.total=25` + `data.page=2`

### Requirement: console 内部端点 gray-release-info

kuship-console SHALL 暴露 `POST /console/teams/{team_name}/regions/{region_name}/apps/{app_id}/gray-release-info` 内部端点，body 含 `service_id` 或 `upgrade_group_id`，返回该 service 是否参与灰度发布，含字段 `is_gray_release`、`gray_release_type` (`original` / `gray`)、`paired_service_id`、`gray_ratio`；不参与灰度时返回 `is_gray_release=false`。鉴权走默认 JWT。

#### Scenario: 查询灰度参与情况
- **WHEN** service `svc-A` 是某 active 灰度的 original_service_id
- **AND** 客户端 POST `.../gray-release-info` body `{"service_id": "svc-A"}`
- **THEN** 响应 SHALL 含 `is_gray_release=true`、`gray_release_type="original"`、`paired_service_id="<gray-svc-id>"`、`gray_ratio=<当前比例>`

#### Scenario: 非灰度 service 返 false
- **WHEN** service `svc-X` 不在任何 active 灰度记录中
- **THEN** 响应 SHALL 含 `is_gray_release=false`，其它字段缺省或为 null

### Requirement: GrayReleaseRecordRepository 查询接口

`GrayReleaseRecordRepository extends JpaRepository<GrayReleaseRecord, Long>` SHALL 至少提供以下查询：

- `Optional<GrayReleaseRecord> findFirstByTenantIdAndAppIdAndStatus(String tenantId, Integer appId, GrayReleaseStatus status)` —— active 唯一性校验
- `Page<GrayReleaseRecord> findByTenantIdAndStatus(String tenantId, GrayReleaseStatus status, Pageable pageable)` —— 列表 + 状态过滤
- `Page<GrayReleaseRecord> findAll(Specification<GrayReleaseRecord> spec, Pageable pageable)` —— OpenAPI 列表组合查询（Spring Data JpaSpecificationExecutor）
- `Optional<GrayReleaseRecord> findFirstByTenantIdAndOriginalServiceIdAndStatus(String tenantId, String originalServiceId, GrayReleaseStatus status)` —— gray-release-info 端点 by service
- `Optional<GrayReleaseRecord> findFirstByTenantIdAndGrayServiceIdAndStatus(String tenantId, String grayServiceId, GrayReleaseStatus status)` —— gray-release-info 端点 by service（gray 侧）

#### Scenario: active 唯一性查询命中
- **WHEN** 数据库已有 (tenantId=t1, appId=42, status=ACTIVE) 1 条 + (tenantId=t1, appId=42, status=CANCELLED) 2 条
- **AND** 调 `findFirstByTenantIdAndAppIdAndStatus("t1", 42, ACTIVE)`
- **THEN** Optional 含值 + 该 record.status=ACTIVE

### Requirement: GraalVM Native 兼容

`KuShipConsoleRuntimeHints` SHALL 注册 `GrayReleaseRecord`（以及 ServiceMappingEntry / GrayReleaseStatus enum）的 `MemberCategory.values()` 反射 hint，使 native binary 下 JPA 字段访问正常。

#### Scenario: native 模式启动 + 写读 record
- **WHEN** 用 native 模式启动 kuship-console 并连接 H2 测试库（已建 gray_release_record 表）
- **AND** 调 `repo.save(...)` + `repo.findById(...)`
- **THEN** SHALL 不抛 InaccessibleObjectException 或 NoSuchMethodException

### Requirement: 文档与运维指引

`kuship-console/CLAUDE.md` SHALL 新增 "灰度发布（add-gray-release）" 段落，至少含：

- 状态机图（ACTIVE → COMPLETED / CANCELLED + 不允许反向）
- 4 OpenAPI 端点表 + 1 console 端点表
- 与 rainbond-go core `/api-gateway/v1/{tenant_name}/routes/http` 的契约约定
- 模板实例化复用 `AppInstallService` 的 path
- 已知非原子边界（ApisixRoute 跨服务事务限制）+ 运维 WARN 日志位置
- 后续 hardening 路径：`add-grayrelease-header-routing`、`add-grayrelease-analytics`、`enforce-grayrelease-uniqueness`

#### Scenario: 文档段落齐全
- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档 SHALL 含 "灰度发布（add-gray-release）" 段落 + 状态机说明 + 4 端点表 + 跨服务事务限制说明
