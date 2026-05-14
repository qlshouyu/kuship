## Why

kuship-ui 切到 kuship-console（8080）后，应用网关 / HTTP 路由 / TCP 路由 三块整页空白：

- 团队网关页 `GET /console/teams/{team_name}/domain/query`、`GET /console/teams/{team_name}/tcpdomain/query` 没有对应 controller
- 应用维度网关页 `GET /console/enterprise/{eid}/team/{team_name}/app/{app_id}/domain` / `.../tcpdomain` 没有对应 controller
- 组件添加端口后无法绑定外网域名（`POST /console/teams/{team_name}/apps/{service_alias}/domain` / `httpdomain` / `tcpdomain` 全部无 handler）
- 5.1 起的"高级路由参数"`PUT /console/teams/{team_name}/domain/{rule_id}/put_gateway` 缺失，已新建的路由无法编辑超时 / 重试 / header 等高级配置
- API Gateway 透传代理 `/api-gateway/v1/{tenant_name}/...` 无 handler，UI 上"路由列表/创建路由"按钮全部 5xx

`migrate-region-coverage-roadmap` 把这块归为 **P0 #2 路线起点**（~29 method）：UI 影响面最大、与前两轮已新建的 `ServiceDomain` / `ServiceTcpDomain` entity 衔接顺、controller 边界清晰。本 change 完整迁移 rainbond `console/views/app_config/app_domain.py`（1500+ 行）的 14 个 view + `console/services/app_config/domain_service.py` 的 30+ 业务方法 + ~22 个 region API。

## What Changes

### 新增 controller（11 个，~22 endpoint）

按 rainbond `console/urls/__init__.py:629-672` 行号锚点，路径与方法严格保留：

- `ServiceDomainController` (`/console/teams/{team_name}/apps/{service_alias}/domain`) GET/POST/DELETE — 组件 HTTP 域名 CRUD
- `HttpStrategyController` (`/console/teams/{team_name}/httpdomain`) GET/POST/PUT/DELETE — 高级 HTTP 策略
- `DomainController` (`/console/teams/{team_name}/domain`) GET — 单条域名查询
- `DomainQueryController` (`/console/teams/{team_name}/domain/query`) GET — HTTP 域名列表（搜索 / 分页）
- `ServiceTcpDomainController` (`/console/teams/{team_name}/tcpdomain`) GET/POST/PUT/DELETE — 团队 TCP 策略 CRUD
- `ServiceTcpDomainQueryController` (`/console/teams/{team_name}/tcpdomain/query`) GET — TCP 域名列表
- `AppServiceDomainQueryController` (`/console/enterprise/{eid}/team/{team_name}/app/{app_id}/domain`) GET — 应用维度 HTTP 列表
- `AppServiceTcpDomainQueryController` (`/console/enterprise/{eid}/team/{team_name}/app/{app_id}/tcpdomain`) GET — 应用维度 TCP 列表
- `GatewayCustomConfigurationController` (`/console/teams/{team_name}/domain/{rule_id}/put_gateway`) GET/PUT — 5.1 高级路由参数
- `GetPortController` (`/console/teams/{team_name}/domain/get_port`) GET — 可用 TCP port 查询
- `GetSeniorUrlController` (`/console/teams/{team_name}/domain/get_senior_url`) GET — 高级路由地址
- `GatewayRouteController` (`/console/teams/{team_name}/gateway-http-route`) GET/POST/PUT/DELETE — Gateway API CRUD
- `GatewayRouteBatchController` (`/console/teams/{team_name}/batch-gateway-http-route`) GET — Gateway API 批量
- `AppApiGatewayController` (`/api-gateway/v1/{tenant_name}/**`) GET/POST/PUT/DELETE — region 透传代理（不带 `/console` 前缀）
- `AppApiGatewayConvertController` (`/api-gateway/convert`) POST — 旧域名 → ApisixRoute 转换

### 新增 / 扩充 Region Operations 接口

- 扩 `GatewayOperations`（`infrastructure/region/api/`）+ ~10 method：bind_http_domain / update_http_domain / delete_http_domain / bind_tcp_domain / update_tcp_domain / unbind_tcp_domain / list_gateways / get_api_gateway / upgrade_configuration（高级路由参数下发）
- 新增 `GatewayRouteOperations`（`modules/gateway/api/`）5 method：list / get / add / update / delete `gateway-http-route`
- 扩 `GatewayOperations.apiGatewayProxy` 已实现，新增 `apiGatewayBindHttpDomain` / `apiGatewayBindHttpDomainConvert` / `apiGatewayBindTcpDomain` 3 method（独立于通用 proxy）

### 新增 entity / repository

- `GatewayCustomConfigure`（`gateway_custom_configuration` 表，3 列：ID / rule_id / value（longtext JSON））
- 复用 前两轮新建的 `ServiceDomain`、`ServiceTcpDomain`、扩充其余字段（current 仅映射 access_urls 拼装所需字段）：
  - `ServiceDomain` 补全列：http_rule_id / region_id / tenant_id / service_name / domain_path / domain_cookie / domain_heander / certificate_id / domain_type / is_senior / type / the_weight / rule_extensions / is_outer_service / auto_ssl / auto_ssl_config / path_rewrite / rewrites
  - `ServiceTcpDomain` 补全列：tcp_rule_id / region_id / tenant_id / service_name / type / rule_extensions / is_outer_service
- 新增 repository：`ServiceDomainRepository` 扩 `findByTenantIdAnd*`、`ServiceTcpDomainRepository` 扩 `findByServiceIdAnd*`、`GatewayCustomConfigureRepository`

### 业务规则迁移

按 `console/services/app_config/domain_service.py` 移植 30+ method 的核心：

- `bindHttpDomain` 两阶段写：本地 INSERT `service_domain` → 调 region `bind_http_domain` → region 失败 rollback
- `unbindHttpDomain` 两阶段：先 region `delete_http_domain` 释放 ingress → 再 DELETE 本地行
- `updateHttpDomain` 同 `bind_http_domain` 协议（rainbond 端用同一 region method 做 upsert）
- `bindTcpDomain` / `updateTcpDomain` / `unbindTcpDomain` 同样两阶段
- `getServiceDomainList` / `getAppServiceDomainList` 分页 + 搜索：本地 `service_domain` JOIN `tenant_service` 做名称模糊匹配（rainbond Python 是 `cursor.execute(...)` 原生 SQL，迁移成 JPA `Specification` 或原生查询）
- `getPortBindDomains` / `getTcpPortBindDomains` 端口聚合 access_urls（`migrate-console-app-runtime` 已用过的逻辑迁过来）
- `createDefaultGatewayRule` 端口对外开启时自动建默认 nip.io 域名规则（与 `migrate-console-application-core` 的 `manage_outer_port` 衔接）
- `updateHttpRuleConfig` 写 `gateway_custom_configuration` 表 + 调 region `upgrade_configuration` 下发 ConfigMap

### 不在本 change 内（以下另起独立子 change）

- 证书管理（`get_gateway_certificate` / `create_gateway_certificate` / `update_gateway_certificate` / `delete_gateway_certificate` / `update_ingresses_by_certificate`、`TenantCertificateView`、`TenantCertificateManageView`、`CalibrationCertificate`）→ `migrate-console-gateway-certificate`
- 二级域名（`SecondLevelDomainView`，`/sld-domain`）→ 与租户域名后缀关联弱，归 `migrate-console-misc-extras` 后续 hardening
- 监控视角的域名访问（`get_query_domain_access` / `get_query_service_access`）→ `migrate-console-monitor-extras`

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "网关 HTTP/TCP 路由与高级配置"。覆盖 14 controller / ~22 endpoint 的 URL 契约、`service_domain` / `service_tcp_domain` / `gateway_custom_configuration` 三表 JPA 校验、域名绑定 / 解绑 / 更新的 region 两阶段写约束、API Gateway 透传代理路径（`/api-gateway/v1/**` 无 `/console` 前缀，需在 SecurityConfig 单独白名单）。

## Impact

- **代码新增**：
  - controller：14 个 (`modules/gateway/controller/`)
  - service：`GatewayDomainService`、`GatewayTcpDomainService`、`GatewayRouteService`、`GatewayCustomConfigurationService`、`GatewayProxyService`
  - region API：扩 `GatewayOperations` +~10 method；新增 `GatewayRouteOperations` 5 method
  - entity：扩 `ServiceDomain` / `ServiceTcpDomain` 全字段；新增 `GatewayCustomConfigure`
  - 单测 + 集成测试覆盖每个 endpoint 的 happy path + region 异常透传
- **数据库**：`service_domain` / `service_tcp_domain` / `gateway_custom_configuration` 三表 schema 由 rainbond Django migrations 拥有（kuship 仅 `validate`，不写 Flyway migration）
- **SecurityConfig**：把 `/api-gateway/v1/**` 与 `/api-gateway/convert` 加入 JWT 鉴权链（不放 permitAll，仅是不带 `/console` 前缀，需在路径列表里显式列出）
- **路径变量名**：必须保留 rainbond Python 原始 `tenantName` / `team_name` / `service_alias` / `serviceAlias` / `rule_id` snake/camel 风格 —— 与 rainbond `urls/__init__.py` 的正则一致（部分行用了 `tenantName` 而非 `team_name`，新 controller 须沿用，否则 kuship-ui 调用形式不匹配）
- **跨服务**：region 后端 `/v2/tenants/{name}/http-rule/*`、`/v2/tenants/{name}/tcp-rule/*`、`/api-gateway/v1/...` 已存在（Go 端），无需变更
- **不影响**：rainbond-console（仍可独立跑 7070）、其他已迁移 change（appmarket / application / appruntime / account 等）
- **TeamPublicAreasController.apps**：accesses 字段已用 `ServiceAccessInfoBuilder` 拼装（基于既有 `ServiceDomain` / `ServiceTcpDomain` entity 部分字段），本 change 把 entity 扩全后，accesses 拼装的来源数据不变，但下游"组件域名页面"才有完整 CRUD
