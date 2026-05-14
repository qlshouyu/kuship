## ADDED Requirements

### Requirement: 网关 HTTP/TCP 路由与高级配置

kuship-console SHALL 提供与 rainbond-console `console/views/app_config/app_domain.py` 等价的网关 HTTP / TCP 路由管理能力，覆盖 14 个 controller / ~22 个 endpoint，路径与 rainbond `console/urls/__init__.py:191/192/641-672` 严格对齐（路径变量统一改为 snake_case），响应形状沿用 general_message（`code/msg/msg_show/data.bean/data.list`）。所有写操作 MUST 走"先本地后 region"或"先 region 后本地"的两阶段事务（绑定路径先本地后 region，解绑路径先 region 后本地），region 失败时 MUST 保证两端数据一致：绑定失败时本地行回滚，解绑失败时本地行保留并抛业务异常。

业务规则：

- HTTP 路由表 `service_domain` 的 `http_rule_id` MUST 由 console 端 UUID 生成，与 region API 共享同一 ID，绑定 / 更新 / 解绑时作为唯一定位键
- TCP 路由表 `service_tcp_domain` 的 `tcp_rule_id` 同上
- `gateway_custom_configuration.value` 字段以 JSON longtext 落盘，业务层读写 SHALL 用 Jackson 3 ObjectMapper 反/序列化为 `Map<String, Object>`；不强制 typed DTO（5.1+ 字段集仍在演化）
- 域名搜索（`domain/query` / `tcpdomain/query` / 应用维度 `app/{app_id}/domain` / `app/{app_id}/tcpdomain`）SHALL 同时匹配 `domain_name` / `end_point` / `service_cname` / `service_alias` 四个字段（rainbond 行为），结果按 `create_time DESC` 排序
- API Gateway 透传 `/api-gateway/v1/{tenant_name}/**` MUST 经过 JWT 鉴权（不放 permitAll）；path tail / body / method 完整透传给 region，header 不需透传业务 header（rainbond 也不透）
- 域名 / 端口冲突 SHALL 返回 409 + region 原始 `msg_show`（汉化由 `RegionErrorMsgEnricher` 兜底，不在业务层硬编码）

#### Scenario: 组件 HTTP 域名 CRUD

- **GIVEN** 已存在组件 `gr512dd5`，team `default`，container_port=80 已对外
- **WHEN** `POST /console/teams/default/apps/gr512dd5/domain` body=`{"domain_name":"foo.example.com","container_port":80,"protocol":"http"}`
- **THEN** 响应 200，`data.bean.http_rule_id` 为 32-char UUID
- **AND** `service_domain` 表新增一行，`http_rule_id` 与响应一致
- **AND** 后续 `GET /console/teams/default/apps/gr512dd5/domain` 含该规则

#### Scenario: 域名解绑路径区分本地/region 失败语义

- **GIVEN** 已绑定 HTTP 域名规则 `http_rule_id=R1`
- **WHEN** `DELETE /console/teams/default/apps/gr512dd5/domain` body=`{"http_rule_id":"R1"}`，region API `delete_http_domain` 返回 5xx
- **THEN** 响应 5xx + region 原始 `msg_show`
- **AND** 本地 `service_domain` 行 `R1` MUST 仍存在（不允许 region 失败但本地已删）

#### Scenario: 域名绑定 region 失败时本地回滚

- **GIVEN** 用户提交新 HTTP 域名规则
- **WHEN** 本地 INSERT 完成，region API `bind_http_domain` 返回 5xx 或抛异常
- **THEN** 响应 5xx + region 原始 `msg_show`
- **AND** 事务回滚，本地 `service_domain` 行 MUST 不存在（不允许 region 失败但本地已写）

#### Scenario: 域名搜索分页

- **GIVEN** team `default` 下有 30 条 HTTP 域名规则，其中 5 条 `domain_name` 含 `prod-`
- **WHEN** `GET /console/teams/default/domain/query?page=1&page_size=10&search_conditions=prod-`
- **THEN** 响应 200，`data.list.length=5`，`data.bean.total=5`
- **AND** 列表项按 `create_time DESC` 排序

#### Scenario: 高级路由参数读写

- **GIVEN** 已存在 HTTP 规则 `http_rule_id=R1`
- **WHEN** `PUT /console/teams/default/domain/R1/put_gateway` body=`{"value":{"connection_timeout":60,"set_headers":[{"key":"X-Real-IP","value":"$remote_addr"}]}}`
- **THEN** 响应 200
- **AND** `gateway_custom_configuration` 表 `rule_id=R1` 行的 `value` 列 MUST 是上述 Map 的 JSON 串
- **AND** region API `upgradeConfiguration` MUST 被调用一次，body 含 `set_headers` 数组
- **AND** 后续 `GET /console/teams/default/domain/R1/put_gateway` 返回原 Map 形状一致

#### Scenario: API Gateway 透传

- **GIVEN** 用户带有效 JWT
- **WHEN** `POST /api-gateway/v1/default/routes/http body={...}`
- **THEN** kuship 把 path tail `default/routes/http`、method `POST`、body 完整转给 region API `api_gateway_post_proxy`
- **AND** region 响应（含 5xx）原样回传客户端，不做 general_message 包装（透传场景）
- **AND** 同请求若无 JWT 返回 401 而非 403（与 SecurityConfig 既定行为一致）

#### Scenario: TCP 端口冲突

- **GIVEN** team `default` 下已绑定 TCP 规则 end_point=`0.0.0.0:30000`
- **WHEN** `POST /console/teams/default/tcpdomain` 提交另一组件用同一 30000 端口
- **THEN** region 返回 409 + msg_show 含"端口已被使用"
- **AND** kuship 响应 409，本地 `service_tcp_domain` 不新增行
