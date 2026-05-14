# Design — migrate-console-gateway-domain

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P0 #2**（路线起点），估计 method 数 **~22**，方法上限阈值 ≤ 30 内可控。本 change 落地后，路线表的对应行需在归档 commit 里标注完成。

## Region API URL 前缀分配（与路线 design.md 决策 4 一致）

| 业务子域           | URL 前缀                                           |
|--------------------|----------------------------------------------------|
| HTTP 域名规则      | `/v2/tenants/{tenant_name}/http-rule[/{rule_id}]`   |
| TCP 域名规则       | `/v2/tenants/{tenant_name}/tcp-rule[/{rule_id}]`    |
| 高级路由参数       | `/v2/tenants/{tenant_name}/http-rule/{rule_id}/configurations` |
| Gateway API 路由   | `/v2/proxy-pass/gateway/{tenant_name}/{kind}*`      |
| API Gateway 透传   | `/api-gateway/v1/{tenant_name}/...`                 |
| API Gateway 转换   | `/api-gateway/convert`                              |

`/api-gateway/v1/**` 是 region 后端的"通用 ApisixRoute 操作通道"，rainbond Python 端用 `region_api.api_gateway_get_proxy/post/put/delete_proxy` 做透传。kuship 端把它放在 `GatewayOperations.apiGatewayProxy*` 系列方法（已实现 `apiGatewayProxy` 通用入口；扩 4 个特化入口）。

## Controller 路径锚点（rainbond `console/urls/__init__.py`）

| kuship Controller                       | path                                                       | method                  | rainbond 锚点                                                            |
|-----------------------------------------|------------------------------------------------------------|-------------------------|--------------------------------------------------------------------------|
| ServiceDomainController                 | `/console/teams/{team_name}/apps/{service_alias}/domain`    | GET / POST / DELETE      | `urls.py:641` `ServiceDomainView`                                        |
| HttpStrategyController                  | `/console/teams/{team_name}/httpdomain`                     | GET / POST / PUT / DELETE | `urls.py:653` `HttpStrategyView`                                         |
| DomainController                        | `/console/teams/{team_name}/domain`                         | GET                      | `urls.py:649` `DomainView`                                               |
| DomainQueryController                   | `/console/teams/{team_name}/domain/query`                   | GET                      | `urls.py:651` `DomainQueryView`                                          |
| ServiceTcpDomainController              | `/console/teams/{team_name}/tcpdomain`                      | GET / POST / PUT / DELETE | `urls.py:663` `ServiceTcpDomainView`                                     |
| ServiceTcpDomainQueryController         | `/console/teams/{team_name}/tcpdomain/query`                | GET                      | `urls.py:659` `ServiceTcpDomainQueryView`                                |
| AppServiceDomainQueryController         | `/console/enterprise/{eid}/team/{team_name}/app/{app_id}/domain` | GET                  | `urls.py:667` `AppServiceDomainQueryView`                                |
| AppServiceTcpDomainQueryController      | `/console/enterprise/{eid}/team/{team_name}/app/{app_id}/tcpdomain` | GET               | `urls.py:665` `AppServiceTcpDomainQueryView`                             |
| GatewayCustomConfigurationController    | `/console/teams/{team_name}/domain/{rule_id}/put_gateway`   | GET / PUT                | `urls.py:671` `GatewayCustomConfigurationView`                           |
| GetPortController                       | `/console/teams/{team_name}/domain/get_port`                | GET                      | `urls.py:661` `GetPortView`                                              |
| GetSeniorUrlController                  | `/console/teams/{team_name}/domain/get_senior_url`          | GET                      | `urls.py:657` `GetSeniorUrlView`                                         |
| GatewayRouteController                  | `/console/teams/{team_name}/gateway-http-route`             | GET / POST / PUT / DELETE | `urls.py:647` `GatewayRoute`                                             |
| GatewayRouteBatchController             | `/console/teams/{team_name}/batch-gateway-http-route`       | GET                      | `urls.py:646` `GatewayRouteBatch`                                        |
| AppApiGatewayController                 | `/api-gateway/v1/{tenant_name}/**`                          | GET / POST / PUT / DELETE | `urls.py:191` `AppApiGatewayView`                                        |
| AppApiGatewayConvertController          | `/api-gateway/convert`                                      | POST                     | `urls.py:192` `AppApiGatewayConvertView`                                 |

trailing slash 兼容沿用 `migrate-console-response-contract` 既定规则：每个 endpoint 同时声明 `path` 与 `path/`。

## 决策 1 — 路径变量命名风格混用（保留 rainbond 风格）

rainbond `urls/__init__.py` 在不同 view 下用了不一致的路径变量风格：
- `tenantName`（驼峰）—— 出现在 `app_domain.py` 系列绝大多数 view（`urls.py:641,646,647,649,651,653,655,657,659,661,663,671`）
- `team_name`（snake_case）—— 出现在 `urls.py:665,667` 应用维度域名查询
- `serviceAlias`（驼峰）/ `service_alias`（snake_case）—— 同样混用

kuship-ui 的 `services/gateWay.js` 调用时用的是 `team_name`、`service_alias`（这是因为它走 baseUrl + 路径模板，路径变量在 `apiconfig` 里），但 Spring 端 `@PathVariable` 的 name 必须严格匹配 URL 模板里的占位符。

**决策**：kuship controller 路径里的占位符 SHALL 全部使用 **snake_case**（`{team_name}`、`{service_alias}`、`{rule_id}`），与 `kuship-console/CLAUDE.md` 既定的"路径变量必须 snake_case"硬约束一致。kuship-ui 端实际调用的是 `${team_name}`，所以即使 rainbond Python 用 `tenantName`，UI 调用也是兼容的（URL 模板生成时已替换）。

唯一例外：`/api-gateway/v1/{tenant_name}/**` 不带 `/console` 前缀，但变量名仍 snake_case。

## 决策 2 — `service_domain` 字段策略（增量映射 vs 全字段映射）

前两轮（`accesses` 字段）只映射了 `id / service_id / container_port / domain_name / protocol / domain_path` 6 列。本 change 必须把 19 列全部映射，否则 `bindHttpDomain` 的 INSERT 会缺列校验失败（DB schema validate 模式下也会因为业务读这些字段时 entity 没字段而漏数据）。

**决策**：本 change 把 `ServiceDomain` 扩到 19 列、`ServiceTcpDomain` 扩到 14 列。命名遵循 rainbond Python（`http_rule_id`、`tcp_rule_id`、`domain_heander`【拼写错保留】、`is_senior`、`auto_ssl_config`），不在本 change 改正拼写。

## 决策 3 — 域名绑定 / 解绑两阶段写顺序

rainbond `domain_service.py:306 bind_httpdomain` 行为：
1. 先 INSERT `service_domain` 行（生成本地 http_rule_id UUID）
2. 调 region `bind_http_domain` 下发 ingress
3. region 成功 → 提交事务；region 失败 → 把刚 INSERT 的行 DELETE 回滚

`unbind_httpdomain` 反向：
1. 先调 region `delete_http_domain` 释放 ingress
2. 再 DELETE 本地 `service_domain` 行
3. region 失败抛异常，本地不删（避免幽灵行）

**决策**：kuship 端遵循同样顺序。`@Transactional` 包 INSERT + region call；rollback 用 try-catch 显式删除（`@Transactional` 自动回滚不能撤销 region 副作用，需要业务层显式 region 反向 call —— 但 `bind` 路径下 region 失败时本地写未提交，事务回滚即可，不需反向 region call；`unbind` 路径下 region 失败应抛异常，让 controller 返 5xx，本地保持原状）。

## 决策 4 — 域名搜索分页：JPA Specification vs 原生 SQL

rainbond `domain_service.py:1003 DomainQueryView.get` 用了 `cursor.execute("""SELECT ... FROM service_domain JOIN tenant_service ... WHERE domain_name LIKE '%X%' OR service_cname LIKE '%X%' OR service_alias LIKE '%X%' ...""")`。

**决策**：kuship 用 `@Query` 原生 SQL（JPA `nativeQuery=true`）+ `Pageable` 复刻。原因：
- JOIN tenant_service 跨 entity，Specification 需要写 metamodel 关联，工作量大
- 搜索逻辑稳定（rainbond 原 SQL 行为已被前端依赖），原样迁移最低风险
- `migrate-console-app-market` change 已有原生 SQL 先例（`market_create.py` 内 cmd_create）

## 决策 5 — `gateway_custom_configuration.value` 序列化

DB 是 longtext，rainbond 写入的是 JSON 字符串（包含 `set_headers` / `connection_timeout` / `proxy_buffering` 等 5.1+ 字段）。

**决策**：kuship entity 字段类型 `String value`，业务层 service 提供 `Map<String, Object> getValue(String ruleId)` / `void setValue(String ruleId, Map<String, Object>)` 两个 helper，内部用 `tools.jackson.databind.ObjectMapper` 串行化。controller 直接收 `@RequestBody Map<String, Object>` 不强行 typed DTO（rainbond 端 5.1 字段集仍在演化，typed DTO 容易过时）。

## 决策 6 — `/api-gateway/v1/**` 透传代理实现

rainbond `views/api_gateway.py:AppApiGatewayView` 的实现是把 path tail 全部转给 `region_api.api_gateway_get_proxy/post/put/delete_proxy`，body 透传，header 透传。

**决策**：kuship 端 controller 用 `**` Ant 模式接所有子路径，把 path tail 抽出来传给 `GatewayOperations.apiGatewayProxy(regionName, enterpriseId, tenantName, path, method, body)`。Spring 端要使用 `HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE` 取剩余路径，不要 `@PathVariable("path") String path` 因为 `**` 不算路径变量。

`SecurityConfig` 把 `/api-gateway/**` 加入 JWT 鉴权链（authenticated），不放 permitAll —— 透传代理会修改集群 ApisixRoute，必须有用户上下文。

## 决策 7 — 默认 nip.io 域名规则的自动创建

rainbond `domain_service.py:902 create_default_gateway_rule` 行为：当 `manage_outer_port` 把端口对外开启时，如果端口没有任何已绑定 HTTP 域名，自动 INSERT 一条默认 `service_domain` 行 + 调 region `bind_http_domain`，规则名形如 `gr512dd5-80-default.<region.httpdomain>`。

**决策**：本 change 把 `createDefaultGatewayRule` helper 实现到 `GatewayDomainService`，但 **不修改 `migrate-console-application-core` 已落地的 `AppPortService`**。后续做"端口对外开启自动建默认域名"的接线由独立 hardening change 处理（避免本 change 边界扩张）。当前阶段：用户开端口对外后需要在域名页手动创建规则。

`accesses` 字段（`TeamPublicAreasController.apps` 已实现）从本 change 落地后会显示完整域名列表，不再依赖于"端口自动建规则"路径。

## 决策 8 — 错误消息

region 异常一律走既有 `RegionApiException` + `GlobalExceptionHandler` 自动映射；不在 controller / service 内硬编码中文 `msg_show`。`RegionErrorMsgEnricher` 已有的"域名冲突"短语规则继续生效（`HelmOperations` 时已加），不重复添加。

## 非决策（明确不做）

- **不升级** `service_domain` schema（保留 rainbond 历史拼写如 `domain_heander`、`is_senior`）
- **不改写** kuship-ui 端的调用路径
- **不实现** Certificate（独立 `migrate-console-gateway-certificate` change）
- **不实现** SLD 二级域名（归 misc 后续 hardening）
- **不实现** 监控维度的 `get_query_domain_access` / `get_query_service_access`（归 `migrate-console-monitor-extras`）
- **不接** "端口对外开启自动建默认域名规则" 到 AppPortService（独立 hardening）

## 测试约定

集成测试（`@SpringBootTest + @ActiveProfiles({"local","contract-test"})`）：
- 每个 controller 至少 1 happy path + 1 region 异常透传 + 1 校验失败 401/400
- bindHttpDomain：region 失败时断言本地 `service_domain` 行已回滚
- DomainQueryView 搜索：覆盖 `domain_name` 命中、`service_cname` 命中、`service_alias` 命中三种 pattern + 分页边界
- API Gateway 透传：构造 path tail（含特殊字符如 `:`、`/`）、body 透传断言、5xx 透传断言

`@MockitoBean GatewayOperations` 替换为 stub，断言入参 path / body 形状。
