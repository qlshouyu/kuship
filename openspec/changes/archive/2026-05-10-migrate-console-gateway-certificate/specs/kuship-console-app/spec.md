## ADDED Requirements

### Requirement: 网关证书 CRUD 与域名校验

kuship-console SHALL 提供与 rainbond `console/views/app_config/app_domain.py:61-298,490-498` + `console/services/gateway_api.py:27-47` 等价的网关证书管理能力，覆盖 4 个 controller / ~7 个 endpoint，路径与 rainbond `console/urls/__init__.py:630/631-632/655/932` 严格对齐（路径变量统一 snake_case），响应形状沿用 general_message。

业务规则：

- 证书 PEM MUST 以 `Base64.getEncoder().encodeToString(pemBytes)` 编码后存入 `service_domain_certificate.certificate` 列；`private_key` 列直存原文 PEM —— 与 rainbond Python 端跨服务读写互操作的硬约束
- 私钥 / 证书匹配 MUST 在写入前校验（RSA 比 modulus；ECDSA 比公钥派生）；不匹配返回 400 + `证书与私钥不匹配`
- 证书 alias MUST 在 tenant 范围内唯一；重名返回 409
- 删除证书前 MUST 检查 `service_domain.certificate_id` 是否仍引用该证书；仍被引用返回 409 + `证书仍被 HTTP 规则使用`
- `certificate_type == "gateway"` 类型证书的 CRUD MUST 同步调用 region `createGatewayCertificate` / `updateGatewayCertificate` / `deleteGatewayCertificate`；非 gateway 类型仅本地表无 region 调用
- 双写顺序：写入路径"先本地后 region"（事务回滚），删除路径"先 region 后本地"
- `EnterpriseCertificateController` (`POST /console/enterprise/team/certificate`) MUST 占位返回 `{is_certificate: 1}`，与 rainbond Python 行为一致

#### Scenario: 上传普通证书

- **GIVEN** team `default` 内尚无 alias 为 `prod-cert` 的证书
- **WHEN** `POST /console/teams/default/certificates` body=`{alias:"prod-cert", certificate:<pem>, private_key:<pem>, certificate_type:"服务端证书"}`，cert 与 key 配对正确
- **THEN** 响应 200，`data.bean.id` 是 INT 主键
- **AND** `service_domain_certificate` 表新增一行，`certificate` 列为 Base64 编码的 PEM
- **AND** region API `createGatewayCertificate` MUST NOT 被调用（非 gateway 类型）

#### Scenario: 私钥不匹配证书

- **GIVEN** 用户上传的 cert 与 key 模数不一致
- **WHEN** `POST /console/teams/default/certificates`
- **THEN** 响应 400 + `msg_show=证书与私钥不匹配`
- **AND** 本地表无新增行，region API 无任何调用

#### Scenario: 证书 alias 重名

- **GIVEN** team `default` 已有 alias 为 `prod-cert` 的证书
- **WHEN** 上传同名 alias 的另一证书
- **THEN** 响应 409 + `msg_show=证书名称已存在`

#### Scenario: 上传 gateway 类型证书触发 region 双写

- **WHEN** `POST /console/teams/default/certificates` body 含 `certificate_type:"gateway"`
- **THEN** 本地 INSERT 后调用 region `createGatewayCertificate`，body=`{namespace: tenant.namespace, name: alias, private_key, certificate}`
- **AND** region 失败时事务回滚，本地行不存在
- **AND** region 成功时事务提交，本地行 + region GatewayTLS 资源都存在

#### Scenario: 删除被引用的证书

- **GIVEN** 证书 pk=5 仍被 `service_domain.certificate_id=5` 的至少一行引用
- **WHEN** `DELETE /console/teams/default/certificates/5`
- **THEN** 响应 409 + `msg_show=证书仍被 HTTP 规则使用，不能删除`
- **AND** region API `deleteGatewayCertificate` MUST NOT 被调用

#### Scenario: 更新证书类型从普通切到 gateway

- **GIVEN** 证书 pk=5 当前 certificate_type=`服务端证书`
- **WHEN** `PUT /console/teams/default/certificates/5` body=`{certificate_type:"gateway", alias, certificate, private_key}`
- **THEN** 调用 region `createGatewayCertificate` 一次（创建 GatewayTLS）
- **AND** 本地行的 certificate_type 列更新为 `gateway`

#### Scenario: 更新证书类型从 gateway 切到普通

- **GIVEN** 证书 pk=5 当前 certificate_type=`gateway`
- **WHEN** `PUT /console/teams/default/certificates/5` body 含 `certificate_type:"服务端证书"`
- **THEN** 调用 region `deleteGatewayCertificate(namespace, alias)` 一次（移除 GatewayTLS）
- **AND** 本地行 certificate_type 列更新

#### Scenario: 校验证书覆盖域名（通配符）

- **GIVEN** 证书 pk=5 的 SAN 含 `*.foo.com`
- **WHEN** `POST /console/teams/default/calibration_certificate` body=`{certificate_id:5, domain_name:"bar.foo.com"}`
- **THEN** 响应 200 + `data.bean.is_pass=pass`

#### Scenario: 通配符不匹配根域

- **GIVEN** 证书 pk=5 的 SAN 仅含 `*.foo.com`（不含 `foo.com`）
- **WHEN** 校验 domain_name=`foo.com`
- **THEN** 响应 200 + `data.bean.is_pass=un_pass` —— 与 rainbond 行为一致：通配符 `*.foo.com` 不覆盖根域 `foo.com`

#### Scenario: 列表分页与 alias 模糊搜索

- **GIVEN** team `default` 下证书 alias 为 `prod-a`、`prod-b`、`dev-c`
- **WHEN** `GET /console/teams/default/certificates?page_num=1&page_size=10&search_key=prod`
- **THEN** 响应 200，`data.list.length=2`，`data.bean.nums=2`
- **AND** 返回项含 issuer / subject / valid_from / valid_to / issued_to (SAN 列表) 字段（由 X.509 解析填充）
