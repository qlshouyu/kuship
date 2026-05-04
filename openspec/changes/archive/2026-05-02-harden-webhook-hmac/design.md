## Context

Phase 11 (`migrate-console-misc`) 完工状态：
- `WebhookTriggerController` 3 endpoint（git / image / custom），匿名访问 + secret query 校验
- `WebhookManageController` 4 endpoint（get-url / trigger / status / updatekey），输出当前 secret query 形式 URL
- `ServiceWebhooks` entity 5 列（id / service_id / state / deploy_keyword / trigger / webhooks_type）
- 没有 secret 旋转策略外的安全加固

Rainbond Python 端的 webhook 校验同样停留在 secret query 阶段（rainbond-console 的 `/v2/webhooks/...` 路由也是这种校验，因此本 change 也涉及与 rainbond-console 的对齐）。但 rainbond-console 直接暴露 secret query 是历史遗留问题，与现代 webhook 安全实践脱节。本次 hardening 让 kuship-console 在保持 rainbond-console URL 兼容（旧 URL 仍 work）的前提下，把 trigger 路径升级到 header 签名模式。

业界基准：
- **GitHub**：`X-Hub-Signature-256: sha256=<hex>`（HMAC-SHA256 of raw body, key=webhook secret）+ `X-GitHub-Delivery: <uuid>` 抗重放头
- **GitLab**：`X-Gitlab-Token: <secret>` 单纯 token 比对（GitLab 不签 body，只验 token）+ `X-Gitlab-Event-UUID: <uuid>`
- **Harbor**：`Authorization: Bearer <auth_token>` token 比对
- **custom（kuship）**：`X-Kuship-Signature: sha256=<hex>` 镜像 GitHub 习惯 + `X-Kuship-Delivery: <uuid>`

## Goals / Non-Goals

**Goals:**
- trigger 三端点支持 GitHub / GitLab / Harbor / custom 四种 header 签名模式
- secret query 继续 work + WARN 日志（兼容期）
- HMAC 比对用常量时间（防侧信道）
- 反重放：同 delivery_id 5 分钟内重复请求拒绝
- `getUrl` 输出新增 v2 URL 字段（不带 secret query），供前端切换
- 不引入新依赖（JDK Mac + 现有 Caffeine）
- 现有 112/112 测试不破，新增 6-7 个 IT 用例

**Non-Goals:**
- 强制要求 header 签名（兼容期保留 secret query）
- 修改 rainbond-console Python 端（独立项目）
- 自定义 webhook 来源协议（custom 复用 GitHub HMAC 算法即可）
- 重写 webhook 触发后的部署逻辑（仍走 lifecycleOps.upgradeService）
- HSM / KMS 密钥管理（secret 仍存 tenant_service.secret 列）

## Decisions

### 1. secret 来源：保留 `tenant_service.secret` 列（与 rainbond-console 共享）

**选择**：HMAC key 复用现有 `tenant_service.secret` 列，不新增专用 webhook secret 列。

**为什么**：
- `tenant_service.secret` 是 rainbond-console 历史命名，跨服务共享 schema 不能擅自加列
- 一个 service 一个 secret 已足够（粒度合理）
- 用户在 `WebhookManageController.updateSecret` 旋转 secret 后所有 webhook 形式同步生效

### 2. 算法：HMAC-SHA256 一刀切

**选择**：GitHub / custom 使用 HMAC-SHA256(secret, body)；GitLab / Harbor 走 token 比对（无 HMAC，协议本身不签 body）

**为什么**：
- HMAC-SHA256 是 GitHub 现代默认（HMAC-SHA1 v1 已 deprecated）
- 算法与 `bcprov-jdk18on`（已在 BouncyCastle 依赖中）兼容；JDK `javax.crypto.Mac` 直接可用
- GitLab / Harbor 协议本身不要求 body 签名（GitLab 默认仅验 token）

**替代方案**：
- 用 SHA-1（兼容 GitHub legacy） → 不安全
- 用 RSA 签名 → 增加密钥管理复杂度

### 3. body 必须是 raw bytes

**选择**：`@RequestBody byte[] body`（之前是 `Map<String, Object>`）

**为什么**：
- HMAC 必须对原始请求体计算（不能先反序列化再序列化，序列化顺序差异会让签名失败）
- 反序列化在 verifier 之后做（拿到 body 后由业务自己 `objectMapper.readValue(body, Map.class)`）

### 4. 反重放：Caffeine in-memory cache

**选择**：`Cache<String, Boolean>` Caffeine cache，maxSize=1024 + expireAfterWrite=5min；key 是 `<service_id>:<delivery_id>`

**为什么**：
- 5 分钟窗口足够防 GitHub 默认重试（GitHub 重试间隔 60s，最多 3 次）
- in-memory：单实例集群足够（多实例需要分布式 cache，留作下游 hardening）
- maxSize 1024 ≈ 单 service 高负载（每秒 3 trigger）的 5 分钟窗口；超出 LRU 驱逐
- key 加 service_id 前缀避免不同 service 间 delivery_id 冲突

**替代方案**：
- Redis cache → 引入 Redis 依赖；当前部署不强制 Redis
- 不去重 → 网络抖动重发会触发部署多次，业务有回滚成本

### 5. 兼容 fallback 策略：secret query 保留 + WARN

**选择**：trigger 端点逻辑：
1. 检查 header 签名（GitHub / GitLab / Harbor / custom）
2. 命中任一 header 模式 → 校验签名 → 通过/拒绝
3. 没有任何 header → 检查 `?secret=` query → 命中即通过 + WARN 日志
4. 都没有 → 401

**为什么**：
- rainbond-console Python 端老用户（已配置 secret query webhook）不破坏
- WARN 日志便于运维抓出仍在用 secret query 的 service，主动通知改用 header
- 6 个月后 `enforce-webhook-signatures` change 移除 fallback（独立 change，独立审批）

### 6. URL v2 输出格式

**选择**：`getUrl` 返回原 3 字段（git_webhook_url / image_webhook_url / custom_webhook_url，带 secret query 兼容）+ 新 3 字段（git_webhook_url_v2 / image_webhook_url_v2 / custom_webhook_url_v2，不带 secret query），并加 `signature_examples` 字段提示用户如何在 GitHub/GitLab webhook 配置页填密钥

**为什么**：
- 前端可以在 UI 切换显示 v1/v2 URL；用户配 GitHub webhook 时复制 v2 URL 并把 secret 填到 webhook 的 "Secret" 字段
- 渐进式迁移，不破坏现有 v1 URL 仍贴在用户配置页的体验

### 7. 常量时间比对

**选择**：用 `java.security.MessageDigest.isEqual(byte[], byte[])`（内部常量时间）

**为什么**：
- 字符串 `equals()` 提前短路返回，会被攻击者用计时侧信道猜测前缀
- `MessageDigest.isEqual` 是 JDK 标准做法

## Risks / Trade-offs

- **[Risk]** Caffeine in-memory dedup 在多实例集群下不去重（不同实例独立 cache）
  → **Mitigation**：本 change 文档化 single-instance 假设；多实例支持留作 `add-distributed-webhook-dedup` change（用 Redis）；多实例下重放仅触发 region upgrade（幂等）
- **[Risk]** raw byte[] 后反序列化失败（malformed JSON 等）
  → **Mitigation**：try-catch + 200 OK 返回 `{"triggered":false,"error":"..."}`（与 webhook 礼仪：始终 200，避免 GitHub 把 webhook 标记为 failing）
- **[Risk]** secret 弱（短长度 / 字典词）让 HMAC 暴力破解
  → **Mitigation**：`updateSecret` 已用 UUID.substring(0, 16) 即 16 字符随机 hex；建议进一步用 32 字符（独立 change）
- **[Risk]** 6 个月 deprecation 太短或太长
  → **Mitigation**：在 CLAUDE.md 写明日期；运维通过 WARN 日志聚合监控决定何时收紧
- **[Trade-off]** GitLab/Harbor 用单纯 token 比对（不签 body）：与 GitHub HMAC 相比安全弱
  → **Mitigation**：协议本身限制；用户可在反向代理（Nginx）层加 mTLS 或 source IP 白名单加固

## Migration Plan

不涉及生产部署变更（compat fallback 保证）。运维侧动作：
1. 跑 `mvn test` 确认测试通过
2. WebhookManageController.getUrl 返回 v1 + v2 字段；前端自动展示 v2 给用户
3. 用户在 GitHub webhook 配置页用 v2 URL + 把 secret 填到 GitHub webhook 的 Secret 输入框
4. 运维监控 WARN 日志 `webhook X using deprecated query secret`，主动通知未迁移用户
5. 6 个月后跑 `enforce-webhook-signatures` change 移除 fallback

回滚：移除 verifier + 还原 trigger controller。`tenant_service.secret` 列保留无害。

## Open Questions

- 是否给 `webhook_call_log` 表加审计：每次 trigger 落 (service_id, kind, signature_verified, source_ip, delivery_id, timestamp)？倾向 NO（首版）：现有 OperationLog 已覆盖运维审计；专用 webhook 审计留作 `add-webhook-audit` change
- 是否同时支持 SHA-1 签名（GitHub legacy）？倾向 NO：GitHub 自 2023 起强烈推荐 SHA-256，向新用户提供 SHA-1 选项是反模式
- 是否给 GitLab webhook 也支持 HMAC body 签？GitLab 不主动发送 HMAC（只发 token），但配合 GitLab `X-Gitlab-Token: <secret>` 已能验证来源，足够
