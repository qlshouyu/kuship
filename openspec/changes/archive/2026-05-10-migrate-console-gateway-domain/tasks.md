# Tasks — migrate-console-gateway-domain

## 1. Schema 真相校验 + Entity 扩字段

- [ ] 1.1 `docker exec kuship-mysql mysql ... DESC service_domain` 校验 19 列；`DESC service_tcp_domain` 校验 14 列；`DESC gateway_custom_configuration` 校验 3 列（已确认）
  <!-- 需用户联动验证：需要本地 MySQL 环境 -->
- [x] 1.2 扩 `modules/application/entity/ServiceDomain.java`：补 `httpRuleId` (`http_rule_id` UNIQUE) / `regionId` / `tenantId` / `serviceName` / `serviceAlias` / `domainCookie` (longtext) / `domainHeander` (longtext，**保留拼写**) / `certificateId` / `domainType` / `isSenior` / `type` / `theWeight` / `ruleExtensions` (longtext) / `isOuterService` / `autoSsl` / `autoSslConfig` (varchar 32 nullable) / `pathRewrite` / `rewrites` (longtext) 共 13 个新字段
- [x] 1.3 扩 `modules/application/entity/ServiceTcpDomain.java`：补 `tcpRuleId` (`tcp_rule_id` UNIQUE) / `regionId` / `tenantId` / `serviceName` / `serviceAlias` / `protocol` / `type` / `ruleExtensions` (longtext nullable) 共 8 个新字段（已有 `serviceId` / `containerPort` / `endPoint` / `outerService`）
- [x] 1.4 新建 `modules/gateway/entity/GatewayCustomConfigure.java`：`@Table(name = "gateway_custom_configuration")`，3 列（`ID` / `rule_id` UNIQUE / `value` longtext）
- [x] 1.5 新建 `modules/gateway/repository/GatewayCustomConfigureRepository`：`Optional<GatewayCustomConfigure> findByRuleId(String)` + `boolean existsByRuleId(String)`
- [x] 1.6 扩 `ServiceDomainRepository`：加 `findByTenantIdWithSearch`（分页+搜索原生 SQL）、`findByServiceIdAndContainerPort`、`findByCertificateId`、`findByHttpRuleId`、`findByServiceIdsWithSearch`
- [x] 1.7 扩 `ServiceTcpDomainRepository`：加 `findByTenantIdWithSearch`（分页+搜索）、`findByServiceIdAndContainerPort`、`findByTcpRuleId`、`findByServiceIds`、`findEndPointsByRegionId`（用于 GetPortView）

## 2. Region API 接口扩展

- [x] 2.1 扩 `infrastructure/region/api/GatewayOperations.java`：新增 method —— `bindHttpDomain` / `updateHttpDomain` / `deleteHttpDomain` / `bindTcpDomain` / `updateTcpDomain` / `unbindTcpDomain` / `listGateways` / `getApiGateway` / `upgradeConfiguration` / `apiGatewayGet` / `apiGatewayPut` / `apiGatewayDelete` / `apiGatewayBindHttpDomainConvert` 共 13 method（原 `apiGatewayProxy` 不动，保留并实现）
- [x] 2.2 扩 `infrastructure/region/api/GatewayOperationsImpl.java`（`@Primary`）：实现全部 13+1 method
  - `bindHttpDomain` URL: `POST /v2/tenants/{tenant_name}/http-rule`
  - `updateHttpDomain` URL: `PUT /v2/tenants/{tenant_name}/http-rule`
  - `deleteHttpDomain` URL: `DELETE /v2/tenants/{tenant_name}/http-rule`
  - `bindTcpDomain` / `updateTcpDomain` / `unbindTcpDomain` URL: `*/v2/tenants/{tenant_name}/tcp-rule`
  - `upgradeConfiguration` URL: `PUT /v2/tenants/{tenant_name}/http-rule/{rule_id}/configurations`
- [x] 2.3 新建 `modules/gateway/api/GatewayRouteOperations.java` + `GatewayRouteOperationsImpl.java`（`@Primary`）：5 method（list / get / add / update / delete `gateway-http-route`）路径前缀 `/v2/proxy-pass/gateway/{tenant_name}/{kind}*`
- [x] 2.4 单测 `GatewayOperationsImplTest`（7 用例）、`GatewayRouteOperationsImplTest`（3 用例）：MockRestServiceServer 断言 URL、错误透传

## 3. Service 层（业务规则迁移）

- [x] 3.1 新建 `modules/gateway/service/GatewayDomainService.java`：迁移 bind / unbind / update HTTP domain 逻辑（6 method）
  - `@Transactional` 包 INSERT + region call；region 失败事务自动回滚
  - `unbind` 路径：先 region call，成功后才删本地行；region 失败抛异常
- [x] 3.2 新建 `GatewayTcpDomainService.java`：迁移 bind / update / unbind TCP domain
- [x] 3.3 新建 `GatewayQueryService.java`：原生 SQL 分页+搜索查询（HTTP/TCP 域名，团队/应用维度）
- [x] 3.4 新建 `GatewayCustomConfigurationService.java`：写 `gateway_custom_configuration` + region upgradeConfiguration；先 region 后本地
- [x] 3.5 新建 `GatewayProxyService.java`：GET/POST/PUT/DELETE + convert 透传 GatewayOperations
- [x] 3.6 新建 `GatewayRouteService.java`：5 method 透传 GatewayRouteOperations
- [x] 3.7 新建 `GatewayPortService.java`：GetPortView（可用 TCP port 列表）+ GetSeniorUrlView（region.httpdomain 前缀）
- [x] 3.8 `manage_outer_port` 自动建默认域名接线 **明确推迟**（design.md 决策 7），本 change 不改 `AppPortService`

## 4. Controller 落地

- [x] 4.1 新建 `ServiceDomainController.java`：GET/POST/DELETE，trailing slash 双声明
- [x] 4.2 新建 `HttpStrategyController.java`：GET/POST/PUT/DELETE
- [x] 4.3 新建 `DomainController.java`：GET
- [x] 4.4 新建 `DomainQueryController.java`：GET（分页 + 搜索）
- [x] 4.5 新建 `ServiceTcpDomainController.java`：GET/POST/PUT/DELETE
- [x] 4.6 新建 `ServiceTcpDomainQueryController.java`：GET
- [x] 4.7 新建 `AppServiceDomainQueryController.java`：GET
- [x] 4.8 新建 `AppServiceTcpDomainQueryController.java`：GET
- [x] 4.9 新建 `GatewayCustomConfigurationController.java`：GET/PUT
- [x] 4.10 新建 `GetPortController.java`：GET
- [x] 4.11 新建 `GetSeniorUrlController.java`：GET
- [x] 4.12 新建 `GatewayRouteController.java`：GET/POST/PUT/DELETE
- [x] 4.13 新建 `GatewayRouteBatchController.java`：GET
- [x] 4.14 新建 `AppApiGatewayController.java`：`/api-gateway/v1/{tenant_name}/**` GET/POST/PUT/DELETE，`HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE` 取 path tail
- [x] 4.15 新建 `AppApiGatewayConvertController.java`：POST
- [x] 4.16 全部 controller 路径变量 snake_case，trailing slash 双声明，advice 自动包 ApiResult
- [x] 4.17 写操作加 `@RequirePerm(PermCode.APP_CREATE_PERMS)`；读操作加 `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)`

## 5. SecurityConfig 调整

- [x] 5.1 把 `/api-gateway/v1/**` 与 `/api-gateway/convert` / `/api-gateway/convert/` 标记为 `authenticated`（JWT 必需，不 permitAll）
- [x] 5.2 SecurityFilterChain 顺序不变（TraceId → Jwt → Username），新 `/api-gateway/**` 链不跳过
- [ ] 5.3 写集成测试断言 `/api-gateway/v1/foo/bar` 无 token 401、有 token 触发 proxy
  <!-- 需用户联动验证：需要本地 MySQL + region 环境 -->

## 6. 集成测试

- [ ] 6.1 `ServiceDomainControllerTest`
  <!-- 需用户联动验证：需要本地 MySQL + region stub -->
- [ ] 6.2 `HttpStrategyControllerTest`
  <!-- 需用户联动验证 -->
- [ ] 6.3 `DomainQueryControllerTest`
  <!-- 需用户联动验证 -->
- [ ] 6.4 `ServiceTcpDomainControllerTest`
  <!-- 需用户联动验证 -->
- [ ] 6.5 `GatewayCustomConfigurationControllerTest`
  <!-- 需用户联动验证 -->
- [ ] 6.6 `AppApiGatewayControllerTest`
  <!-- 需用户联动验证 -->
- [ ] 6.7 `GatewayRouteControllerTest`
  <!-- 需用户联动验证 -->

## 7. accesses 字段联动复核

- [ ] 7.1 启动 console + UI，在 `/console/teams/default/apps?...` 列表里观察 `accesses[].access_info[].access_urls`
  <!-- 需用户联动验证 -->
- [ ] 7.2 创建 HTTP 域名规则后断言 access_urls 含新 domain
  <!-- 需用户联动验证 -->
- [ ] 7.3 删除规则后断言 access_urls 不含被删 domain
  <!-- 需用户联动验证 -->

## 8. 编译 / 重启 / 文档

- [x] 8.1 `cd kuship-console && mvn -DskipTests package` 通过（已验证）
- [ ] 8.2 重启 console，curl 验证 GET /console/teams/default/domain/query 返回 200
  <!-- 需用户联动验证 -->
- [x] 8.3 新增 `openspec/changes/migrate-console-gateway-domain/CLAUDE_FRAGMENT.md`（不修改 kuship-console/CLAUDE.md）
- [ ] 8.4 路线图反向更新（归档时执行）
  <!-- 由 archive change 统一处理 -->
