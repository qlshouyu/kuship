## Why

第 10 阶段（migrate-console-plugin）让 kuship-console 把"插件织入"完整覆盖。但仍有一批"杂项"端点没迁移：**消息中心 / Webhook 触发部署 / MCP Query / 文件上传 / 登录事件 / 操作审计日志 / Console 升级 / 企业配置 / SMS 短信 / KubeBlocks 数据库 detail / API Gateway / k8s_attribute / 错误日志 / Task Guidance**。这些都是单点功能，没有统一主题，但每一项都被 kuship-ui 用到——少了任何一个就会有空白页。

本次把 rainbond `views/` 下 14 个剩余 view 文件 ~3000 行迁移到 kuship-console，覆盖 **~50 endpoint**。方法：按"低耦合 + 单子域单 controller"原则集中实施，最大化复用已有 entity/repository。

## What Changes

- **消息中心**：`/teams/{team_name}/message` GET/PUT —— `user_message` 表（11 列含 `announcement_id`/`level`），未读消息列表 + 标记已读。
- **Webhook 触发部署**（rainbond 让外部 git push / 镜像 push 自动 redeploy）：
  - `/webhooks/{service_id}` POST —— git webhook 触发器（GitHub / GitLab 兼容）
  - `/image/webhooks/{service_id}` POST —— 镜像仓库 webhook（Harbor/DockerHub）
  - `/custom/deploy/{service_id}` POST —— 通用 webhook
  - `/teams/{team}/apps/{alias}/webhooks/{get-url,trigger,status,updatekey}` —— webhook URL 管理
  - `service_webhooks` 表（5 列）+ secret key 字段保存在 `tenant_service.secret`（已有第 7 阶段 entity）
- **MCP Query**：`/mcp/query/{sse,message,http}` —— Model Context Protocol 三种调用入口（rainbond 让 LLM 拉应用元数据）。MVP 仅实现 HTTP RPC 同步入口（SSE 留作 hardening，不上 reactor-netty）。
- **文件上传**：`/files/upload` POST —— 通用文件上传到本地或对象存储。MVP 仅本地磁盘。
- **登录事件**：`/enterprise/{eid}/login-events` GET 列表 —— `login_events` 表（10 列含 ip/user_agent/duration），分页查询。
- **操作审计日志**：3 endpoint —— `OperationLogView`（自我）/ `TeamOperationLogView`（团队）/ `AppOperationLogView`（组件级）—— `operation_log` 表（14 列含 longtext old_/new_information，已在 Authorization Filter 中通过 AOP 自动写入；本 change 仅暴露查询）。
- **Console 升级**：`/enterprise/upgrade{,/version{,/{version}{,/images}}}` —— 4 endpoint，console 自身的版本检测（不实现真实升级流；返回固定版本字符串作为 MVP）。
- **企业配置**：`/enterprises/{eid}/configs` GET/PUT/DELETE / `/enterprise/object_storage` / `/enterprise/appstore_image_hub` / `/enterprise/visualmonitor` / `/enterprise/alerts` —— 6 endpoint，复用第 4 阶段已有的 `console_config` 表。
- **企业激活**：`/teams/{team}/enterprise/active{,/optimiz}` —— 2 endpoint，绑定云端市场凭据。
- **SMS 短信**：`/enterprises/{eid}/sms-config` GET/PUT + `/sms/send-code` POST + `/users/{register-by-phone,login-by-phone}` POST —— 4 endpoint。MVP：sms_verification_code 表落地 + 占位短信发送（不真正调阿里云 SMS API）。
- **KubeBlocks 数据库**：`/teams/{team}/regions/{r}/kubeblocks/{supported_databases,storage_classes,backup_repos}` + `/apps/{alias}/kubeblocks/{detail,backup-config,backups,parameters,restore}` —— 8 endpoint 全部 region 透传，不写本地表。
- **API Gateway**：`/teams/{team}/api-gateway/{routes,certificates}` —— 简化版，4 endpoint 透传 region。
- **K8s Attribute**：`/teams/{team}/apps/{alias}/k8s_attributes` GET/POST —— 2 endpoint，复用第 6 阶段 service entity。
- **错误日志 + Task Guidance + 平台设置 + 团队总览 + 团队资源**：剩余 5 view 共 ~10 endpoint 占位实现。

## Capabilities

### Modified Capabilities

- `kuship-console-app`: 新增约 14 条 misc 端点 Requirement —— 消息中心 / Webhook / MCP / 文件上传 / 登录事件 / 操作日志 / Console 升级 / 企业配置 / SMS / KubeBlocks / 杂项。

## Impact

- **新增包**：`cn.kuship.console.modules.misc/`（最后一个业务模块），按子域细分：`message/`、`webhook/`、`mcp/`、`upload/`、`audit/`（login_event + operation_log）、`upgrade/`、`config/`（enterprise_config + active）、`sms/`、`kubeblocks/`、`gateway/`、`other/`（task_guidance + errlog + platform_settings + team_overview + team_resources）。
- **新增 Entity**（5 张本地表 JPA 映射，按 schema 真相）：
  - `UserMessage`（user_message，11 列含 `announcement_id`）
  - `ServiceWebhooks`（service_webhooks，5 列）
  - `LoginEvents`（login_events，10 列）
  - `OperationLog`（operation_log，14 列含 longtext old_/new_information）
  - `SmsVerificationCode`（sms_verification_code，6 列含 created_at/expires_at）
- **复用 Entity**：`ConsoleConfig`（第 4 阶段已有）作为企业配置的存储。
- **不引入新依赖**：MCP Query 用同步 HTTP（rainbond 原版有 SSE，本阶段只做 JSON-RPC over HTTP，graalvm-native 兼容）；文件上传用 Spring `MultipartFile`（已默认在）；SMS MVP 不调外部 SDK。
- **测试**：扩展 2 个集成测试（消息中心 + 操作日志查询）。
