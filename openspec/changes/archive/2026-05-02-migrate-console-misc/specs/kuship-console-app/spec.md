## ADDED Requirements

### Requirement: 用户消息中心端点

kuship-console SHALL 实现 `GET /teams/{team_name}/message` 列出当前用户未读消息 + `PUT /teams/{team_name}/message` 批量标记已读/未读/删除共 2 endpoint。`UserMessage` Entity 落地 `user_message` 表（11 列含 `announcement_id` + `level`）。

#### Scenario: GET /message 返回当前用户未读消息

- **WHEN** 调 `/console/teams/team1/message?msg_type=warning&is_read=false`
- **THEN** kuship-console 按 receiver_id = current user_id 过滤
- **AND** 响应 list 含 message_id / title / content / level / msg_type / create_time

#### Scenario: PUT /message 批量标记已读

- **WHEN** 调 PUT body `{"message_ids":["m1","m2"],"action":"read"}`
- **THEN** kuship-console UPDATE `user_message.is_read = 1` WHERE message_id IN (m1,m2) AND receiver_id = current user_id

### Requirement: Webhook 触发部署端点

kuship-console SHALL 实现 8 个 Webhook 端点：
- `POST /webhooks/{service_id}` git webhook 触发 redeploy
- `POST /image/webhooks/{service_id}` 镜像仓库 webhook
- `POST /custom/deploy/{service_id}` 通用 webhook
- `GET /teams/{team}/apps/{alias}/webhooks/get-url` 拿 webhook URL + secret
- `POST /teams/{team}/apps/{alias}/webhooks/trigger` 手动触发
- `GET /teams/{team}/apps/{alias}/webhooks/status` 查询配置
- `PUT /teams/{team}/apps/{alias}/webhooks/updatekey` 重新生成 secret

`ServiceWebhooks` Entity 落地 `service_webhooks` 表（5 列）；secret key 沿用 `tenant_service.secret` 字段（已有第 7 阶段 entity）。MVP **不做 HMAC 验签**，按 `secret` query param 简单匹配；正式 HMAC 留作 hardening。

#### Scenario: POST /webhooks/{service_id}?secret=xxx 触发部署

- **WHEN** GitHub push 调 webhook
- **THEN** kuship-console 校验 query string `secret` 与 `tenant_service.secret` 是否匹配
- **AND** 不匹配返回 401
- **AND** 匹配后调 region build 触发 redeploy
- **AND** 响应 `{event_id: ...}`

#### Scenario: GET /webhooks/get-url 返回 URL + secret

- **WHEN** 调 GET
- **THEN** 响应 `{webhook_url: "https://kuship.example.com/console/webhooks/{service_id}?secret={secret}", secret: ...}`

### Requirement: MCP Query JSON-RPC 端点

kuship-console SHALL 实现 `POST /mcp/query/http` MCP（Model Context Protocol）JSON-RPC 同步入口共 1 endpoint，接受标准 JSON-RPC 2.0 请求格式，将 LLM 来源的查询请求路由至已有的 application / plugin / market 等 query method。SSE / message 流式入口推迟到独立 hardening change。

#### Scenario: POST /mcp/query/http 返回 LLM 友好的应用列表

- **WHEN** 调 POST body `{"jsonrpc":"2.0","id":1,"method":"list_apps","params":{"team_name":"team1"}}`
- **THEN** kuship-console 路由到 GroupController.list 等已有方法
- **AND** 响应 JSON-RPC 2.0 格式 `{"jsonrpc":"2.0","id":1,"result":{...}}` 或 error 形式

### Requirement: 文件上传端点

kuship-console SHALL 实现 `POST /files/upload` 通用文件上传 + `GET /files/{file_id}` 读取共 2 endpoint。文件存储到 `${kuship.upload.dir:/tmp/kuship}/`，按 UUID 重命名。单文件最大 5MB。响应含 file_url + file_name + file_size。

#### Scenario: POST /files/upload 上传图片

- **WHEN** 调 POST multipart form 含 `file=@avatar.png`
- **THEN** kuship-console 写文件至 `${kuship.upload.dir}/{uuid}.png`
- **AND** 响应 `{file_url: "/console/files/{uuid}.png", file_name: "avatar.png", file_size: ...}`

#### Scenario: 文件超过 5MB 返回 400

- **WHEN** 调 POST 上传 6MB 文件
- **THEN** 响应 400 + `msg_show: "文件超过 5MB"`

### Requirement: 登录事件查询端点

kuship-console SHALL 实现 `GET /enterprise/{eid}/login-events` 列出当前企业最近登录事件共 1 endpoint，分页查询 `login_events` 表（10 列含 client_ip/user_agent/duration）。

#### Scenario: GET /login-events 按时间倒序

- **WHEN** 调 GET `?page=1&page_size=20`
- **THEN** kuship-console 按 enterprise_id 过滤 + 按 login_time 倒序
- **AND** 响应 list 含 username / login_time / client_ip / ip_locale_main / user_agent / duration

### Requirement: 操作审计日志查询端点

kuship-console SHALL 实现 3 个审计查询端点：
- `GET /enterprise/{eid}/operation-logs` —— 企业级（限制 EnterpriseAdmin）
- `GET /teams/{team_name}/operation-logs` —— 团队级
- `GET /teams/{team_name}/apps/{app_id}/operation-logs` —— 应用级

`OperationLog` Entity 落地 `operation_log` 表（14 列含 longtext old_/new_information）。列表查询用 JPQL 限定字段（不返回 longtext），单条详情 endpoint 才查完整行。

#### Scenario: GET /enterprise/{eid}/operation-logs 列表

- **WHEN** 调 GET `?operation_type=upgrade&page=1`
- **THEN** kuship-console 按 enterprise_id 过滤
- **AND** 响应 list 含 username / operation_type / team_name / app_name / service_cname / comment / create_time
- **AND** 不返回 old_information / new_information 字段（避免大字段 SELECT）

### Requirement: Console 升级查询端点

kuship-console SHALL 实现 4 个 console 自身升级查询端点（MVP 占位返回固定版本字符串；真升级靠运维换 jar）：
- `GET /enterprise/upgrade` 当前版本
- `GET /enterprise/upgrade/version` 可升级版本列表
- `GET /enterprise/upgrade/version/{version}` 版本详情
- `GET /enterprise/upgrade/version/{version}/images` 镜像列表

#### Scenario: GET /enterprise/upgrade 返回当前版本

- **WHEN** 调 GET
- **THEN** 响应 `{current_version: "0.1.0-SNAPSHOT", build_time: "..."}`

### Requirement: 企业全局配置端点

kuship-console SHALL 实现企业全局配置 6 endpoint：
- `GET /enterprises/{eid}/configs` 列表
- `PUT /enterprises/{eid}/configs/{key}` 更新单项
- `DELETE /enterprises/{eid}/configs/{key}` 删除
- `GET/PUT /enterprise/object_storage` 对象存储配置
- `GET/PUT /enterprise/appstore_image_hub` 应用市场镜像源
- `GET /enterprise/{eid}/visualmonitor` 可视化监控开关
- `GET /enterprise/{eid}/alerts` 告警配置

复用第 4 阶段已落地的 `ConsoleConfig` entity。key 命名规则：`{enterprise_id}.{config_name}`。

#### Scenario: PUT /enterprise/{eid}/configs/{key} 更新配置

- **WHEN** 调 PUT body `{"value":"...","description":"..."}`
- **THEN** kuship-console UPSERT `console_config`（key=enterprise_id.{key}）

### Requirement: SMS 短信端点

kuship-console SHALL 实现 4 个 SMS 端点：
- `GET/PUT /enterprises/{eid}/sms-config` SMS 配置
- `POST /sms/send-code` 发送验证码
- `POST /users/register-by-phone` 手机号注册
- `POST /users/login-by-phone` 手机号登录

`SmsVerificationCode` Entity 落地 `sms_verification_code` 表（6 列）。MVP **不调外部 SMS SDK**：写表 + dev profile 控制台打印 code（prod profile 拒绝该 controller，要求接 SDK）。

#### Scenario: POST /sms/send-code 写表

- **WHEN** 调 POST body `{"phone":"13800000000","purpose":"login"}`
- **THEN** kuship-console 生成 6 位随机数字 code
- **AND** INSERT `sms_verification_code`（expires_at = now + 5min）
- **AND** dev profile 控制台打印 code
- **AND** 响应 `{sent: true, expires_at: ...}`

#### Scenario: POST /users/login-by-phone 验证 + 签发 JWT

- **WHEN** 调 POST body `{"phone":"...","code":"123456"}`
- **THEN** kuship-console 查询最近未过期的 sms_verification_code 匹配
- **AND** 匹配后签发 JWT 同 user_auth 流程
- **AND** 不匹配返回 401

### Requirement: KubeBlocks 数据库端点

kuship-console SHALL 实现 8 个 KubeBlocks 端点全部 region 透传，无本地 entity：
- `GET /teams/{team}/regions/{r}/kubeblocks/{supported_databases,storage_classes,backup_repos}`
- `GET /teams/{team}/apps/{alias}/kubeblocks/{detail,backup-config,backups,parameters,restore}`

#### Scenario: GET /kubeblocks/supported_databases 透传

- **WHEN** 调 GET
- **THEN** kuship-console 调 region `/v2/kubeblocks/supported-databases` 透传响应

### Requirement: API Gateway 端点

kuship-console SHALL 实现 4 个 API Gateway 透传端点：
- `GET/POST /teams/{team}/api-gateway/routes`
- `GET/POST /teams/{team}/api-gateway/certificates`

全部 region 透传无本地 entity。

#### Scenario: GET /api-gateway/routes 透传

- **WHEN** 调 GET
- **THEN** kuship-console 调 region `/v2/api-gateway/routes` 透传

### Requirement: 占位端点（k8s_attribute / errlog / task_guidance / platform_settings / team_overview / team_resources）

kuship-console SHALL 实现剩余 ~10 个占位端点，返回空数据或固定字符串：
- `/teams/{team}/apps/{alias}/k8s_attributes` GET/POST → region 透传
- `/teams/{team}/apps/{alias}/k8s_resources` GET → region 透传
- `/console/errlog` POST → 写本地 logback，不写 DB
- `/console/task-guidance` GET → 返回空数组
- `/console/platform-settings` GET → 返回固定 platform 配置（type=community + version 字符串）
- `/console/teams/{team}/overview` GET → 聚合 Tenants/TenantService 计数
- `/console/teams/{team}/resources` GET → 聚合资源使用 + 配额

#### Scenario: GET /platform-settings 返回固定配置

- **WHEN** 调 GET
- **THEN** 响应 `{type:"community", version:"0.1.0-SNAPSHOT", commit_id:"..."}`

#### Scenario: POST /errlog 写日志

- **WHEN** 调 POST body `{"msg":"...","stack":"..."}`
- **THEN** kuship-console 用 SLF4J `error()` 输出到日志，响应 200

### Requirement: misc 模块 5 张表的 JPA Entity 与 Repository

kuship-console SHALL 在 `cn.kuship.console.modules.misc.{message,webhook,audit,sms}.entity` 包下新增 5 个 Entity：
1. `UserMessage`（user_message，11 列含 announcement_id/level）
2. `ServiceWebhooks`（service_webhooks，5 列）
3. `LoginEvents`（login_events，10 列）
4. `OperationLog`（operation_log，14 列含 longtext old_/new_information）
5. `SmsVerificationCode`（sms_verification_code，6 列含 created_at/expires_at）

主键全部 Integer 自增；列名严格对齐 schema（特别 SMS 表的 `id` 小写而非 `ID`）；不引入复杂 helper（参考 audit 模块的 longtext 字段处理）。

#### Scenario: ddl-auto=validate 启动通过

- **WHEN** 应用启动连真实 MySQL
- **THEN** Hibernate ddl-auto=validate 不报缺列 / 多列 / 错类型错误

### Requirement: misc 模块测试覆盖

kuship-console SHALL 提供至少 2 类集成测试覆盖 misc 核心：
1. `MessageIntegrationTest`：POST 模拟系统消息插入 + GET 列表 + PUT 标记已读
2. `OperationLogIntegrationTest`：模拟 INSERT operation_log + GET 列表分页 + 验证 longtext 字段不在列表返回

#### Scenario: 集成测试全部使用真实 MySQL

- **WHEN** 在 docker-compose 启动后跑 `mvn -Dtest='cn.kuship.console.modules.misc.**' test`
- **THEN** 每类测试在 `@BeforeAll` 用高位 user_id（9093xx）插入 user/team 数据
- **AND** 在 `@AfterAll` 清理避免数据残留
- **AND** 全部用例通过

#### Scenario: 全套 ≥ 97 用例

- **WHEN** 跑 `mvn test`
- **THEN** 总用例数 ≥ 97（95 老 + 2 新 misc）全部通过
