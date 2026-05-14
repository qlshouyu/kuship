## ADDED Requirements

### Requirement: 组件存储卷只读查询与依赖挂载

kuship-console SHALL 落地 `ServiceVolumeOperations` 接口中既存的 6 个 default unsupported method（`getVolumeOptions` / `getVolumes` / `getVolumeStatus` / `getDepVolumes` / `addDepVolumes` / `deleteDepVolumes`），并补齐对应的 6 个 console controller 端点（其中 2 个挂在已建的 `AppVolumeController`，3 个新建 `AppMntController`，1 个新建 `ApplicationVolumesController`），路径与 rainbond `console/urls/__init__.py:425/602/605/625/628` 严格对齐。

业务规则：

- 路径段 `tenant_name` MUST 替换为 `Tenants.namespace`（缺失回退 `tenant_name`），与 helm-release / cluster-extras 规约一致
- `getVolumeOptions` 路径 `/v2/volume-options` 是集群级（不带 tenant），但接口签名仍带 `tenantName` 形参用于权限校验与 RegionClient 路由
- `DELETE /mnt/{dep_vol_id}` 端点 MUST 把路径变量 `dep_vol_id` 提取后并入 region body（path 优先覆盖 body）
- `GET /groups/{app_id}/volumes` 应用级聚合 MUST 用并发调用（每组件一个 `CompletableFuture`），超时 10s；单组件 region 失败用 fallback 空 list + warn 日志，**不让一个组件失败导致整应用响应 5xx**
- 读端点 `@RequirePerm("describe_team_app")`，写端点 `@RequirePerm("manage_team_app")` 或 fallback `app_create_perms`
- region 异常透传 `RegionApiException` + `GlobalExceptionHandler` 自动映射

#### Scenario: 存储类型选项查询

- **WHEN** `GET /console/teams/default/apps/gr512dd5/volume-opts`
- **THEN** kuship 调 region `GET /v2/volume-options`
- **AND** 响应 200 + `data.bean` 含集群支持的存储类型 list

#### Scenario: 组件卷列表查询

- **WHEN** `GET /console/teams/default/apps/gr512dd5/volumes?enterprise_id=eid`
- **THEN** kuship 调 region `GET /v2/tenants/<ns>/services/gr512dd5/volumes?enterprise_id=eid`
- **AND** 响应 200 + 组件已挂载卷列表

#### Scenario: 应用级卷状态聚合

- **GIVEN** app_id=6 含 3 个组件 c1/c2/c3
- **WHEN** `GET /console/teams/default/groups/6/volumes`
- **THEN** kuship 并发调 region `getVolumeStatus` 3 次（每组件 1 次）
- **AND** 响应 200 + `data.list` 含 3 组件的卷状态聚合

#### Scenario: 应用级卷状态单组件失败降级

- **GIVEN** 组件 c2 调 region 返 5xx
- **WHEN** 同上 endpoint
- **THEN** 响应仍 200，c2 的 entry 是 fallback 空 list（含 warn 日志），c1/c3 正常
- **AND** 不抛 5xx 给 client

#### Scenario: 添加依赖挂载

- **WHEN** `POST /console/teams/default/apps/gr512dd5/mnt` body=`{"volume_name":"data","volume_path":"/data","dep_service_id":"<id>","dep_vol_id":"<vid>"}`
- **THEN** kuship 调 region `POST /v2/tenants/<ns>/services/gr512dd5/depvolumes` body 透传
- **AND** 响应 200 + `data.bean`（透传 region 响应）

#### Scenario: 取消依赖挂载路径变量到 body 转换

- **WHEN** `DELETE /console/teams/default/apps/gr512dd5/mnt/vol-abc-123`（无 body 或 body 部分字段）
- **THEN** kuship 构造 body=`{"dep_vol_id":"vol-abc-123",...其他 client body 字段}`，调 region `DELETE /depvolumes` with body
- **AND** 响应 200 + general_message

#### Scenario: 写操作权限校验

- **GIVEN** 普通团队成员（无 `manage_team_app` 权限）
- **WHEN** POST `/mnt` 或 DELETE `/mnt/{dep_vol_id}`
- **THEN** 响应 403 + 权限不足提示
- **AND** 同用户 GET 端点正常（`describe_team_app` 通过）
