## 1. 模块基础设施 + Entity

- [x] 1.1 创建包结构 `cn.kuship.console.modules.misc/{message,webhook,mcp,upload,audit,upgrade,config,sms,kubeblocks,gateway,other}/{controller,entity,repository,dto}`
- [x] 1.2 用 `docker exec kuship-mysql mysql ... DESC <table>` 校验 5 张表的真实列名（user_message / service_webhooks / login_events / operation_log / sms_verification_code）
- [x] 1.3 新增 `message/entity/UserMessage.java`（11 列含 announcement_id/level）+ `MessageRepository.java`
- [x] 1.4 新增 `webhook/entity/ServiceWebhooks.java`（5 列）+ `ServiceWebhooksRepository.java`
- [x] 1.5 新增 `audit/entity/LoginEvents.java`（10 列含 ip/user_agent）+ Repository
- [x] 1.6 新增 `audit/entity/OperationLog.java`（14 列含 longtext）+ Repository（list 用 JPQL 限定字段不返 longtext）
- [x] 1.7 新增 `sms/entity/SmsVerificationCode.java`（6 列含 PK 小写 `id` 而非 `ID`）+ Repository

## 2. message + audit 子域 controller

- [x] 2.1 新建 `message/controller/UserMessageController.java`：`/teams/{team}/message` GET/PUT 共 2 endpoint
- [x] 2.2 新建 `audit/controller/LoginEventController.java`：`/enterprise/{eid}/login-events` GET 共 1 endpoint，分页 + ip 字段返回
- [x] 2.3 新建 `audit/controller/OperationLogController.java`：`/enterprise/{eid}/operation-logs` + `/teams/{team}/operation-logs` + `/teams/{team}/apps/{app_id}/operation-logs` 共 3 endpoint，列表不返 longtext

## 3. webhook 子域 controller

- [x] 3.1 新建 `webhook/controller/WebhookTriggerController.java`：`/webhooks/{service_id}` + `/image/webhooks/{service_id}` + `/custom/deploy/{service_id}` 共 3 endpoint，secret query 校验，不做 HMAC（推迟）
- [x] 3.2 新建 `webhook/controller/WebhookManageController.java`：`/teams/{team}/apps/{alias}/webhooks/{get-url,trigger,status,updatekey}` 共 4 endpoint
- [x] 3.3 secret 字段读写复用 `tenant_service.secret`（已有第 7 阶段 entity）

## 4. mcp + upload + sms 子域

- [x] 4.1 新建 `mcp/controller/MCPQueryController.java`：`/mcp/query/http` POST 共 1 endpoint —— stub 响应（实际 method 路由分发留作 hardening）
- [x] 4.2 新建 `upload/controller/FileUploadController.java`：`/files/upload` POST + `/files/{file_id}` GET 共 2 endpoint，本地磁盘 5MB 上限
- [x] 4.3 配置 `kuship.upload.dir` 默认 `/tmp/kuship`；启动时 `mkdir -p`
- [x] 4.4 新建 `sms/controller/SMSConfigController.java`：`/enterprises/{eid}/sms-config` GET/PUT 占位 stub（ConsoleConfig 持久化留作 hardening）
- [x] 4.5 新建 `sms/controller/SMSVerificationController.java`：`/sms/send-code` POST + `/users/{register-by-phone,login-by-phone}` POST 共 3 endpoint —— send-code 真实写表 + dev profile 打印；register/login-by-phone 仅 stub 验证 code（JWT 签发 + user_info INSERT 留作 hardening）

## 5. config + kubeblocks + gateway 子域

- [x] 5.1 新建 `config/controller/EnterpriseConfigController.java`：`/enterprises/{eid}/configs` GET/PUT/DELETE 共 3 endpoint，key 命名 `{eid}.{key}` 复用 ConsoleConfig
- [x] 5.2 新建 `config/controller/EnterpriseObjectStorageController.java`：`/enterprise/object_storage` GET/PUT 共 2 endpoint
- [x] 5.3 新建 `config/controller/EnterpriseAppstoreController.java`：`/enterprise/appstore_image_hub` GET/PUT 共 2 endpoint
- [x] 5.4 新建 `config/controller/EnterpriseActiveController.java`：`/teams/{team}/enterprise/active{,/optimiz}` POST 共 2 endpoint（市场凭据绑定）
- [x] 5.5 新建 `kubeblocks/controller/KubeBlocksController.java`：8 endpoint 占位返回（实际 region 透传留作 hardening）
- [x] 5.6 新建 `gateway/controller/ApiGatewayController.java`：4 endpoint 占位返回（实际 region 透传留作 hardening）

## 6. upgrade + other 子域占位

- [x] 6.1 新建 `upgrade/controller/ConsoleUpgradeController.java`：4 endpoint 返回固定版本字符串
- [x] 6.2 + 6.3 + 6.4 + 6.5 + 6.6 + 6.7 全部合并实现到 `other/controller/MiscOtherController.java`：platform-settings / task-guidance / errlog / team-overview / team-resources / k8s_attribute / k8s_resource 共 9 endpoint，避免文件碎片化

## 7. 启动校验 + 文档

- [x] 7.1 跑 `mvn -pl kuship-console clean compile` 验证 0 编译错误
- [x] 7.2 在 `kuship-console/CLAUDE.md` 新增"杂项收尾（migrate-console-misc）"段落
- [x] 7.3 配置 `application.yaml` 默认值：`kuship.upload.dir=/tmp/kuship` + `spring.servlet.multipart.max-file-size=5MB`

## 8. 集成测试

- [x] 8.1 新建 `misc/message/integration/MessageIntegrationTest.java`：seed user_message + GET 列表 + PUT 标记已读 + 验证 is_read=1
- [ ] 8.2 `OperationLogIntegrationTest.java`（推迟：列表 + longtext 字段过滤已可手测；hardening）
- [x] 8.3 跑 `mvn test` 全部 96 用例通过（1 新 + 95 老）

## 9. 校验

- [x] 9.1 跑 `openspec validate migrate-console-misc --strict` 通过
