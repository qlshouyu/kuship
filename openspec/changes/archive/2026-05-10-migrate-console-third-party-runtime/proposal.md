## Why

`migrate-console-app-create` 落地了第三方组件的**创建** (`/teams/{name}/apps/third_party` POST)，但创建后的运行时管理（endpoint CRUD、健康探针配置）整片缺失：

- `GET /console/teams/{team_name}/apps/{service_alias}/third_party/pods` —— 看第三方组件的当前 endpoint 列表（IP/PORT/状态），整页空
- `POST` / `PUT` / `DELETE` 同 URL —— endpoint 增删改 405
- `GET /console/teams/{team_name}/apps/{service_alias}/3rd-party/health` —— 健康探针配置查询，空
- `PUT /console/teams/{team_name}/apps/{service_alias}/3rd-party/health` —— 健康探针配置写入，405

UI 表现：第三方组件创建后挂在那里，无法配置实际的 endpoint 列表（K8s ExternalName / Endpoints 无法同步），健康探针不可设置 → 第三方组件的核心价值（接入外部服务）完全断。

`migrate-region-coverage-roadmap` 把这块归为 **P0 #8**（6 method）。本 change 完整迁移 rainbond `console/views/app_create/source_outer.py:ThirdPartyAppPodsView,ThirdPartyHealthzView` 共 6 个 HTTP method（GET/POST/DELETE/PUT for pods + GET/PUT for health）+ `regionapi.py:1828-1893` 共 6 个 region method。

## What Changes

### 新增 Region Operations 接口

新增 `modules/thirdparty/api/ThirdPartyServiceOperations.java` 6 method：

| method                                                  | HTTP   | 路径                                                                   |
|---------------------------------------------------------|--------|------------------------------------------------------------------------|
| getEndpoints(rn, tn, alias)                             | GET    | `/v2/tenants/{namespace}/services/{alias}/endpoints`                    |
| postEndpoints(rn, tn, alias, body)                      | POST   | `/v2/tenants/{namespace}/services/{alias}/endpoints`                    |
| putEndpoints(rn, tn, alias, body)                       | PUT    | `/v2/tenants/{namespace}/services/{alias}/endpoints`                    |
| deleteEndpoints(rn, tn, alias, body)                    | DELETE | `/v2/tenants/{namespace}/services/{alias}/endpoints`                    |
| getHealth(rn, tn, alias)                                | GET    | `/v2/tenants/{namespace}/services/{alias}/3rd-party/probe`              |
| putHealth(rn, tn, alias, body)                          | PUT    | `/v2/tenants/{namespace}/services/{alias}/3rd-party/probe`              |

`namespace` 取自 `Tenants.namespace || tenant_name`。

注意 POST/PUT/DELETE endpoints 的 region 调用 rainbond 端会带 `resource_validation: "true"` header（防止 K8s API server 拒绝）—— 本 change 沿用，由 `RegionApiSupport` 透传。

### 新增 controller（2 个，6 endpoint）

按 rainbond `console/urls/__init__.py:494,576` 行号锚点：

- `ThirdPartyEndpointsController` (`/console/teams/{team_name}/apps/{service_alias}/third_party/pods`) GET / POST / PUT / DELETE — `urls.py:576` `ThirdPartyAppPodsView`
- `ThirdPartyHealthController` (`/console/teams/{team_name}/apps/{service_alias}/3rd-party/health`) GET / PUT — `urls.py:494` `ThirdPartyHealthzView`

### 业务规则迁移

按 `console/views/app_create/source_outer.py:ThirdPartyAppPodsView,ThirdPartyHealthzView`：

- **endpoints body 形状**：
  - POST/PUT body=`{"address":"<ip:port>", "is_online":true}` 或批量 `{"endpoints":[{address,is_online},...]}`
  - DELETE body=`{"ep_id":"<endpoint-id>"}`
  - 全部透传 region
- **resource_validation header**：POST/PUT/DELETE endpoints 时 region client 带 `resource_validation: true`（rainbond 用 `_set_headers(token, resource_validation="true")`），本 change 在 `ThirdPartyServiceOperationsImpl` 透传请求头
- **health probe 配置**：rainbond 的 health probe 是 K8s readinessProbe / livenessProbe 的简化封装（含 mode / scheme / path / port / period / timeout），body 透传给 region
- **service 校验**：controller 层先用 `serviceRepo.findByTenantIdAndServiceAlias(...)` 校验组件存在 + 是 third_party 类型；非 third_party 抛 400

### 不在本 change 内（明确推迟 / 切出）

- 第三方组件创建（已在 `migrate-console-app-create`）
- 内部组件的 K8s service 信息（不算第三方）
- KubeBlocks 数据库实例（独立子 change）

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "第三方组件 endpoint 与健康探针管理"。覆盖 6 endpoint 契约、6 region method 实现、`resource_validation: true` header 透传、组件类型校验（必须是 third_party）。

## Impact

- **代码新增**：
  - 接口：`ThirdPartyServiceOperations` 6 method + `ThirdPartyServiceOperationsImpl @Primary`
  - controller：`ThirdPartyEndpointsController` + `ThirdPartyHealthController`
  - service：`ThirdPartyEndpointService`（含组件类型校验）
  - 单测 + 集成测试：6 region method × 2 + 6 endpoint
- **数据库**：无变更（endpoint 数据全在 region K8s，无本地表）
- **跨 change 衔接**：
  - 与 `migrate-console-app-create` 已落地的第三方组件创建路径无冲突
  - 与 `migrate-console-resource-center` 不重叠（resource-center 看 K8s 原生 Pod，本 change 看 third_party 组件的逻辑 endpoint）
- **路径变量**：`{team_name}` / `{service_alias}` snake_case
