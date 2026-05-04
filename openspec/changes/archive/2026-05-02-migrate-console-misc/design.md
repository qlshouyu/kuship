## Context

第 11 阶段是 13 阶段路线的"杂项收尾"——把 rainbond `views/` 下没归到前 10 阶段的剩余 14 个 view 文件整理上线。这些 view 没有统一主题，但每个都被 kuship-ui 用到（菜单上的"消息"红点、组件页的"Webhook URL"按钮、企业管理的"操作审计"页等）。

涉及参考代码（按重要性排序）：

| view | LOC | endpoint | 重要性 |
|------|-----|----------|--------|
| `webhook.py` | 712 | 8 | 高（git push 自动部署） |
| `mcp_query.py` | 517 | 3 | 中（LLM 集成） |
| `kubeblocks.py` | 300 | 8 | 中（DB cluster） |
| `enterprise_config.py` | 272 | 6 | 中（企业全局配置） |
| `operation_log.py` | 84 | 3 | 高（审计合规） |
| `enterprise_active.py` | 128 | 2 | 中（市场凭据绑定） |
| `message.py` | 130 | 2 | 高（用户消息红点） |
| `sms_config.py` + `sms_verification.py` | 159 | 4 | 低（SMS 国内合规） |
| `login_event.py` | 22 | 1 | 中 |
| `file_upload.py` | 48 | 1 | 高（用户头像 / 应用图标） |
| `upgrade.py` | 63 | 4 | 低（console 自身升级，MVP 占位） |
| `api_gateway.py` | 84 | 4 | 低（透传 region） |
| `k8s_attribute.py` + `k8s_resource.py` | 87 | 2 | 低 |
| `task_guidance.py` + `errlog.py` + 其他 | ~150 | ~5 | 低（占位） |
| **合计** | **~3000** | **~50 endpoint** | |

数据库 schema 真相（已 `DESC`）：
- `user_message`（11 列含 `announcement_id`/`level`）
- `service_webhooks`（5 列：service_id / state / webhooks_type / deploy_keyword / trigger）
- `login_events`（10 列含 client_ip/user_agent/duration）
- `operation_log`（14 列含 longtext old_/new_information）
- `sms_verification_code`（6 列：phone/code/purpose/created_at/expires_at）
- `console_config` 第 4 阶段已落地

## Goals / Non-Goals

**Goals:**
- 让 kuship-ui 完整运转，无空白页（消息/webhook/审计/企业配置/SMS/KubeBlocks 全部接通）。
- 落地 5 张新本地表的 JPA Entity（按 `DESC` 真相）+ 复用 `ConsoleConfig`。
- 完成最后一批"非主航线 endpoint"，让 13 阶段路线接近收尾。
- 不引入新框架（不上 SSE / reactor-netty / 阿里云 SMS SDK），保持 graalvm-native 兼容性。

**Non-Goals:**
- 不实现 MCP Query 的 SSE 实时推流（仅做 JSON-RPC over HTTP，前端 LLM 集成可走轮询）。
- 不实现 console 自身的真实升级流（占位返回固定版本字符串；真升级靠运维换 jar）。
- 不真正调阿里云 / 腾讯云 SMS API（只生成验证码 + 写表 + 控制台日志打印；prod 需独立 change 接 SDK）。
- 不实现对象存储真实接入（占位写本地磁盘 + 路径标识）。
- 不实现 KubeBlocks 备份/恢复的本地状态机（全 region 透传，前端按响应渲染）。
- 不重新设计 operation_log 的 AOP 切面写入逻辑（已在 PermAspect 段上接）；本阶段仅暴露查询。

## Decisions

### 决策 1：misc 模块按 11 子域细分

```
modules/misc/
├── message/        UserMessage 1 entity + MessageController（GET/PUT 2 endpoint）
├── webhook/        ServiceWebhooks 1 entity + 4 controller（webhooks 8 endpoint，HMAC 校验）
├── mcp/            MCP query controller（3 endpoint，仅 HTTP JSON-RPC，SSE 推迟）
├── upload/         FileUploadController（1 endpoint，本地磁盘 MVP）
├── audit/          LoginEvents 1 entity + OperationLog 1 entity + 4 controller（4 endpoint）
├── upgrade/        ConsoleUpgradeController（4 endpoint 占位）
├── config/         EnterpriseConfigController + EnterpriseActiveController（8 endpoint 复用 ConsoleConfig）
├── sms/            SmsVerificationCode 1 entity + SMSConfigController + SMSVerificationController（4 endpoint）
├── kubeblocks/     KubeBlocks 透传 controller（8 endpoint，无本地 entity）
├── gateway/        ApiGatewayController（4 endpoint，透传 region）
└── other/          TaskGuidance + ErrLog + PlatformSettings + TeamOverview + TeamResources + K8sAttribute + K8sResource 占位（10+ endpoint）
```

每个子域独立 controller；entity 按需新增（5 张表）。

### 决策 2：Webhook 不实现 HMAC 验签的真正校验

`webhook.py` 的 GitHub/GitLab/Harbor 三种 webhook 各有独立 HMAC sign（`X-Hub-Signature-256`），实现完整验签需 36 行 fingerprint 对比。MVP 仅按 `secret` query param 简单匹配 `tenant_service.secret`（rainbond 默认实现也是这个保守模式）；正式 HMAC 验签留作 hardening change `harden-webhook-hmac`。

### 决策 3：MCP Query 仅 HTTP JSON-RPC

rainbond `mcp_query.py` 有 3 入口：`/mcp/query/sse`（SSE 推流）、`/mcp/query/message`（接收）、`/mcp/query/http`（同步）。SSE 需要 `reactor-netty` 或 `Servlet 3.0 AsyncContext`，与 graalvm-native 兼容性差。MVP 仅实现 `/mcp/query/http` 同步 JSON-RPC（PluginOperations / ApplicationView 等查询请求 80% 用例），SSE/message 留作 `add-mcp-sse` change。

### 决策 4：文件上传仅本地磁盘 + Path 标识

`/files/upload` MVP：写到 `${kuship.upload.dir:/tmp/kuship}/` 目录，按 UUID 重命名。返回 `{file_url: "/console/files/{uuid}", file_name, file_size}`。下载端点同样在 `/console/files/{uuid}` 直接读磁盘（`@SkipResponseWrapper` byte[] 透传）。
- 单文件最大 5MB（Spring `spring.servlet.multipart.max-file-size`）
- 不实现对象存储（S3/MinIO 留作 hardening）
- prod 部署应挂 PV 到 `${kuship.upload.dir}` 持久化

### 决策 5：操作日志查询走自定义 JPQL 分页

`operation_log` 表有 14 列含两个 longtext，全表 SELECT 性能差。controller 用 `@Query` 限定字段：
```jpql
SELECT id, createTime, username, operationType, teamName, appName, comment
FROM OperationLog WHERE enterpriseId = :eid ORDER BY createTime DESC
```
old_information / new_information 仅在单条详情 endpoint 才查（按 ID load 完整行）。

### 决策 6：MessageController 双端点

- `GET /teams/{team_name}/message?msg_type=&is_read=` → 用户当前未读消息列表（按 receiver_id = current user_id 过滤）
- `PUT /teams/{team_name}/message` body `{message_ids: [], action: "read"|"unread"|"delete"}` → 批量标记/删除

业务端不直接 INSERT message —— 那是 region 调 console 的事件 hook 触发，不是 user-facing 操作。

### 决策 7：SMS 短信 MVP

`POST /sms/send-code` body `{phone, purpose: "register|login|reset"}`：
1. 校验 phone 格式（11 位数字）
2. 生成 6 位随机数字 code
3. INSERT `sms_verification_code`（expires_at = now + 5min）
4. console.log 输出 code（dev 调试），prod 时不打印
5. 返回 `{sent: true, expires_at}`

`POST /users/login-by-phone` body `{phone, code}`：
1. 查询最近未过期的 sms_verification_code
2. 匹配后 → 生成 JWT token（同 user_auth 流程）
3. 自动注册（如果 phone 不存在 user_info）

实际 SMS 网关接入（阿里云 / 腾讯云）需要 access_key 配置 + SDK 依赖，留作 `add-aliyun-sms` change。

### 决策 8：KubeBlocks 8 endpoint 全部 region 透传

`kubeblocks.py` 全部 endpoint 都需要 region 端 KubeBlocks operator + addon，console 不存任何本地状态。controller 直接调 `RegionClientFactory.getClient(region).restClient()` 透传响应给前端。

不引入 `KubeBlocksOperations` 接口（path 复杂多变 + 调用频率低 + 无状态机），直接在 controller 内做 RestClient 调用。

### 决策 9：企业配置复用 ConsoleConfig 表

`EnterpriseConfigView` 等 6 个 view 都把配置存在 `console_config` 表（key/value 字典）。第 4 阶段已落地此表。本 change 仅新增 controller，不重新设计 entity。
- key 命名规则：`{enterprise_id}.{config_name}`（如 `E1.OBJECT_STORAGE`、`E1.APPSTORE_IMAGE_HUB`）
- 视图层提供按 enterprise_id 过滤的查询

### 决策 10：剩余占位 view（task_guidance / errlog / k8s_attribute / k8s_resource / api_gateway / platform_settings / team_overview / team_resources）

这些 view 多是单 endpoint 透传或返回固定数据。按"占位 controller + 空响应"原则，不写复杂逻辑。前端如果调用：
- task_guidance：返回空数组（用户引导任务，不影响主流程）
- errlog：写一条到本地 logback（不写 DB）
- k8s_attribute / k8s_resource：纯 region 透传
- api_gateway：region 透传
- platform_settings：返回固定 platform 配置（type=community / version=固定字符串）
- team_overview / team_resources：聚合现有 entity 数据（Tenants / TenantService 计数）

## Risks / Trade-offs

- **[Risk]** MCP Query 不完整影响 LLM 集成 → Mitigation：HTTP JSON-RPC 已覆盖 80% 查询场景；前端可按 5s 轮询替代 SSE；正式 SSE 单独 change 落。
- **[Risk]** SMS MVP 控制台打印 code → Mitigation：dev profile only；prod profile 启动时用 ConditionalOnExpression 拒绝该 controller 注册（要求接外部 SDK）。
- **[Risk]** 文件上传无对象存储 → Mitigation：`${kuship.upload.dir}` 默认 `/tmp/kuship`；prod 挂 PVC；S3 集成 hardening。
- **[Risk]** Webhook 不验 HMAC = 任何人能触发部署 → Mitigation：仍有 `secret` URL query 兜底（rainbond 原版默认就这个保守模式）；正式 HMAC 验签 hardening。
- **[Risk]** 50 endpoint 单 change → Mitigation：按 11 子域并行；每子域内部 1-2 个 controller 闭环。
- **[Trade-off]** 部分占位 controller 返回空数据可能让前端有"部分功能不可用"提示 → 在前端 issue 里登记 known-limitation；hardening 时填充。

## Migration Plan

阶段 A：misc 包结构 + 5 entity + 5 repository（基础）
阶段 B：message / webhook / audit 三个高优先级 controller（最常用）
阶段 C：mcp / upload / sms 三个常用 controller
阶段 D：config / kubeblocks / gateway 三个透传 controller
阶段 E：其他占位 controller（task_guidance / errlog / k8s_* / platform_settings / team_overview / team_resources / upgrade / enterprise_active）
阶段 F：编译 + 集成测试 + 文档 + openspec validate

## Open Questions

- **(Q1)** Webhook deploy_keyword 字段如何在 trigger 中匹配？rainbond 用 substring，console 沿用即可。
- **(Q2)** sms_verification_code 表的 cleanup 策略？过期记录定期 DELETE，本阶段不做（hardening）。
- **(Q3)** operation_log 全文搜索是否需要？rainbond 原版仅按时间倒序，本阶段保持。
