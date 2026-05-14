# 网关域名管理（migrate-console-gateway-domain）

## Controller 清单（15 个，落地到 `modules/gateway/controller/`）

| Controller | 路径 | 方法 |
|---|---|---|
| `ServiceDomainController` | `/console/teams/{team_name}/apps/{service_alias}/domain` | GET/POST/DELETE |
| `HttpStrategyController` | `/console/teams/{team_name}/httpdomain` | GET/POST/PUT/DELETE |
| `DomainController` | `/console/teams/{team_name}/domain` | GET |
| `DomainQueryController` | `/console/teams/{team_name}/domain/query` | GET |
| `ServiceTcpDomainController` | `/console/teams/{team_name}/tcpdomain` | GET/POST/PUT/DELETE |
| `ServiceTcpDomainQueryController` | `/console/teams/{team_name}/tcpdomain/query` | GET |
| `AppServiceDomainQueryController` | `/console/enterprise/{enterprise_id}/team/{team_name}/app/{app_id}/domain` | GET |
| `AppServiceTcpDomainQueryController` | `/console/enterprise/{enterprise_id}/team/{team_name}/app/{app_id}/tcpdomain` | GET |
| `GatewayCustomConfigurationController` | `/console/teams/{team_name}/domain/{rule_id}/put_gateway` | GET/PUT |
| `GetPortController` | `/console/teams/{team_name}/domain/get_port` | GET |
| `GetSeniorUrlController` | `/console/teams/{team_name}/domain/get_senior_url` | GET |
| `GatewayRouteController` | `/console/teams/{team_name}/gateway-http-route` | GET/POST/PUT/DELETE |
| `GatewayRouteBatchController` | `/console/teams/{team_name}/batch-gateway-http-route` | GET |
| `AppApiGatewayController` | `/api-gateway/v1/{tenant_name}/**` | GET/POST/PUT/DELETE |
| `AppApiGatewayConvertController` | `/api-gateway/convert` | POST |

## gateway_custom_configuration 表说明

- `ID` 自增主键
- `rule_id` VARCHAR(128) UNIQUE — 对应 HTTP 规则 ID（http_rule_id）
- `value` LONGTEXT — JSON 序列化的高级路由参数（set_headers / connection_timeout / proxy_buffering 等 5.1+ 字段）
- 业务层通过 `GatewayCustomConfigurationService.getValue/setValue` 序列化/反序列化

## API Gateway 透传 SecurityConfig 配置

`/api-gateway/v1/**` 与 `/api-gateway/convert` 在 `SecurityConfig` 中标记为 `authenticated`（需要 JWT），不 `permitAll`。
已在 `SecurityConfig.securityFilterChain` 的 `authorizeHttpRequests` 链显式添加。

## 两阶段写策略

- **HTTP bind**：`@Transactional` 内 INSERT → region bindHttpDomain；region 失败 → 事务回滚（本地 INSERT 撤销）
- **HTTP unbind**：先 region deleteHttpDomain → 成功后本地 DELETE；region 失败抛异常，本地不删
- **TCP bind**：同 HTTP bind 策略
- **高级配置 setValue**：先 region upgradeConfiguration → 成功后本地 INSERT/UPDATE；region 失败不写本地

## Entity 扩字段情况

- `ServiceDomain`：19 列全字段（含 http_rule_id UNIQUE、domain_heander 保留历史拼写）
- `ServiceTcpDomain`：14 列全字段（含 tcp_rule_id UNIQUE）
- `GatewayCustomConfigure`（新增）：3 列
