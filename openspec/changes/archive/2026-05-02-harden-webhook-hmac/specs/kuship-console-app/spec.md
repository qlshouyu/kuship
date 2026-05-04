## ADDED Requirements

### Requirement: WebhookSignatureVerifier

系统 SHALL 提供 `WebhookSignatureVerifier` 共享组件，封装 GitHub `X-Hub-Signature-256`、GitLab `X-Gitlab-Token`、Harbor `Authorization: Bearer`、kuship custom `X-Kuship-Signature` 四种 webhook 签名/令牌验证；HMAC 算法使用 HMAC-SHA256；secret 比对使用 `MessageDigest.isEqual` 常量时间方法。

#### Scenario: GitHub HMAC-SHA256 通过
- **WHEN** 客户端 POST 带 `X-Hub-Signature-256: sha256=<hex>` 头，hex 是 HMAC-SHA256(secret, raw_body)
- **THEN** verifier `verifyGitHub` 返回 true

#### Scenario: GitHub HMAC 签名错误
- **WHEN** `X-Hub-Signature-256` 头存在但 hex 与 HMAC 不一致
- **THEN** verifier 返回 false
- **AND** 不触发任何下游业务调用

#### Scenario: GitLab token 通过
- **WHEN** 客户端 POST 带 `X-Gitlab-Token: <secret>` 头，secret 等于 service.secret
- **THEN** verifier `verifyGitLab` 返回 true（常量时间比对）

#### Scenario: Harbor bearer 通过
- **WHEN** 客户端 POST 带 `Authorization: Bearer <secret>` 头，secret 等于 service.secret
- **THEN** verifier `verifyHarbor` 返回 true

#### Scenario: custom HMAC 通过
- **WHEN** 客户端 POST 带 `X-Kuship-Signature: sha256=<hex>` 头，hex 是 HMAC-SHA256(secret, raw_body)
- **THEN** verifier `verifyCustom` 返回 true

### Requirement: trigger 端点 header 签名优先

`WebhookTriggerController` 的 git / image / custom 三个 trigger 端点 SHALL 优先检查对应 header 签名（git: GitHub/GitLab；image: Harbor；custom: kuship），命中即按 header 验签；任意 header 命中且签名失败 SHALL 拒绝（不 fallback 到 secret query）。

#### Scenario: git trigger 优先 GitHub HMAC
- **WHEN** POST `/console/webhooks/{service_id}` 带有效 `X-Hub-Signature-256`
- **THEN** trigger SHALL 仅用 GitHub HMAC 验签
- **AND** 不读取 `secret` query 参数

#### Scenario: git trigger 缺 GitHub 头时尝试 GitLab token
- **WHEN** POST `/console/webhooks/{service_id}` 缺 `X-Hub-Signature-256` 但有 `X-Gitlab-Token`
- **THEN** trigger SHALL 用 GitLab token 验证
- **AND** 不读取 `secret` query

#### Scenario: image trigger 优先 Harbor bearer
- **WHEN** POST `/console/image/webhooks/{service_id}` 带 `Authorization: Bearer <token>`
- **THEN** trigger SHALL 用 Harbor bearer 验证
- **AND** 不读取 `secret` query

#### Scenario: header 签名错误不退回 query
- **WHEN** POST 带 `X-Hub-Signature-256` 但签名错误
- **THEN** trigger SHALL 立即返回 401
- **AND** 不再读取 `secret` query 重试

### Requirement: secret query fallback 与 deprecation

trigger 端点 SHALL 在所有 header 签名都缺失时退回 `?secret=<x>` query 校验；fallback 走通时 SHALL 输出 WARN 日志 `webhook <kind> for service <service_id> using deprecated query secret; switch to header signature`，并 SHALL 在 `WebhookManageController.getUrl` 输出 deprecation 提示字段。

#### Scenario: 缺 header 时 query 仍 work
- **WHEN** POST `/console/webhooks/{service_id}?secret=<x>` 不带任何 header 签名
- **AND** secret 等于 service.secret
- **THEN** trigger SHALL 通过校验并返回 200
- **AND** 输出 WARN 日志记录 deprecation

#### Scenario: getUrl 输出 v2 URL
- **WHEN** GET `/console/teams/{team_name}/apps/{service_alias}/webhooks/get-url`
- **THEN** 响应 SHALL 包含 `git_webhook_url_v2`、`image_webhook_url_v2`、`custom_webhook_url_v2` 三个不带 secret query 的 URL
- **AND** 响应 SHALL 包含 `signature_examples` 字段提示用户三种 header 写法
- **AND** 旧字段 `git_webhook_url` / `image_webhook_url` / `custom_webhook_url`（带 secret query）保留兼容

### Requirement: 反重放 delivery dedup

trigger 端点 SHALL 在收到 `X-GitHub-Delivery`、`X-Gitlab-Event-UUID` 或 `X-Kuship-Delivery` 头时，使用 Caffeine 内存缓存（key=`<service_id>:<delivery_id>`、TTL 5 分钟、maxSize 1024）做去重；命中重复 delivery_id 时直接返回 200 + `{triggered: false, dedup: true}`，不调下游 region API。

#### Scenario: 同 delivery_id 5 分钟内不重复触发
- **WHEN** POST 带 `X-GitHub-Delivery: <uuid>` 通过 HMAC 验签后
- **AND** 客户端 1 分钟后再发同 uuid
- **THEN** trigger SHALL 第一次正常触发，第二次返回 200 + `{triggered:false, dedup:true}`
- **AND** lifecycleOps.upgradeService 仅被调用一次

#### Scenario: 不同 service 的相同 delivery_id 不冲突
- **WHEN** service_a 收到 delivery `abc` 后
- **AND** service_b 收到 delivery `abc`
- **THEN** 两次 trigger SHALL 都正常执行
- **AND** cache key 包含 service_id 前缀避免冲突

### Requirement: 常量时间签名比对

WebhookSignatureVerifier 的所有 secret / HMAC 比对 SHALL 使用 `MessageDigest.isEqual(byte[], byte[])` 常量时间方法，禁止使用 `String.equals()` 或 `Arrays.equals()`。

#### Scenario: 长度差异不提前返回
- **WHEN** verifier 比对一个 64 字节签名与一个 1 字节签名
- **THEN** 比对耗时 SHALL 与同长度比对耗时无显著差异（通过 unit test 多次采样的 stddev 检验通过即可）

### Requirement: 文档与 deprecation 时间表

`kuship-console/CLAUDE.md` 的 webhook 段落 SHALL 列出：四种 header 签名格式表、secret query deprecation 时间表（本 change 起 6 个月警告，此后独立 `enforce-webhook-signatures` change 移除）、运维监控 WARN 日志的方法。

#### Scenario: 文档段落齐全
- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档 SHALL 包含 "Webhook HMAC 签名（harden-webhook-hmac）" 段落
- **AND** 段落 SHALL 至少包含 4 行签名格式说明 + deprecation 路径 + 监控指引
