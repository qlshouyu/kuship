## Why

Phase 11 (`migrate-console-misc`) 落地了 7 个 webhook 端点（manage 4 + trigger 3），但 trigger 端点目前只用 secret query 参数比对（`?secret=xxx`）做校验：

- secret 在 URL 中明文出现 → 易泄露到日志、代理、git history、bookmark
- 任何转发该 URL 的中间设施（CDN / WAF）都能拿到 secret
- 不抗重放：同一 secret URL 任何时刻、任何人重复 POST 即可触发部署
- 不抗签名伪造：MITM 中间人改 body 后用同 secret 重发，被部署的不是源仓库的 commit

GitHub / GitLab / Harbor（rainbond 三大 registry/repo 集成对象）都早已是 HMAC-SHA256 签名头模式：

| 来源 | 签名头 | 算法 | 备注 |
|------|--------|------|------|
| GitHub | `X-Hub-Signature-256` | HMAC-SHA256(secret, body) | `sha256=<hex>` 前缀 |
| GitLab | `X-Gitlab-Token` | 直接 token 比对 | 无 HMAC，token 即 secret，但走 header 不走 query |
| Harbor | `Authorization: Bearer <token>` | OAuth-style bearer | 也是 token 比对，body 不签名 |
| custom（kuship 自定义） | `X-Kuship-Signature` | HMAC-SHA256(secret, body) | 与 GitHub 同算法 |

本次 hardening 把上述协议接入 trigger controller，secret query 仅作 fallback（带 deprecation 日志）。

## What Changes

- 新增 `WebhookSignatureVerifier` 共享 verifier：
  - `verifyGitHub(byte[] body, String headerSig, String secret)` —— 解析 `sha256=<hex>` 并 HMAC-SHA256 比对
  - `verifyGitLab(String headerToken, String secret)` —— 直接常量时间比对
  - `verifyHarbor(String authHeader, String secret)` —— 解析 `Bearer <token>` + 常量时间比对
  - `verifyCustom(byte[] body, String headerSig, String secret)` —— `X-Kuship-Signature: sha256=<hex>` HMAC-SHA256
- `WebhookTriggerController` 改造：
  - `git/{service_id}` —— 优先读 `X-Hub-Signature-256`（GitHub）→ 否则读 `X-Gitlab-Token`（GitLab）→ 否则用 secret query（fallback，记 WARN 日志）
  - `image/{service_id}` —— 优先读 `Authorization: Bearer`（Harbor）→ 否则用 secret query（fallback）
  - `custom/deploy/{service_id}` —— 优先读 `X-Kuship-Signature`（HMAC）→ 否则 secret query（fallback）
- body 改为 `byte[]`（必要：HMAC 必须对 raw bytes 签）；解析为 Map 留给业务逻辑
- 反重放：`X-GitHub-Delivery` / `X-Gitlab-Event-UUID` / `X-Kuship-Delivery` 头去重（5 分钟窗口 in-memory Caffeine cache）
- secret query 兼容继续工作但发 WARN 日志 `"webhook X using deprecated query secret; switch to header signature"`，CLAUDE.md 写明 deprecation 时间表（6 个月后转为强制，转独立 change）
- `WebhookManageController.getUrl` 响应新增 `git_webhook_url_v2` / `image_webhook_url_v2` / `custom_webhook_url_v2` 字段（不带 secret query，期待用户改用 header），保留旧字段兼容
- 5 个集成测试场景：GitHub HMAC 通过 / GitHub HMAC 失败 / GitLab token 通过 / Harbor bearer 通过 / custom HMAC 通过 / secret query fallback 通过 + WARN log / 反重放命中

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `kuship-console-app`: 在 webhook 域加 HMAC / token / bearer 三种 header 签名校验、反重放、deprecation policy。

## Impact

- **代码改动**：1 个新工具类（WebhookSignatureVerifier，~150 LOC）+ WebhookTriggerController 改造（body 改 byte[]、新增 4 个 verifyXxx 调用）+ getUrl 响应加 v2 字段。
- **依赖**：0 新依赖（`javax.crypto.Mac` JDK 自带，Caffeine 已在用）。
- **测试覆盖**：现有 112/112 不破；新增 6-7 个 webhook IT 用例。
- **运行时**：trigger 路径多一次 HMAC 计算（< 1ms）；in-memory delivery dedup cache（默认 1024 entries × 5 min TTL）。
- **安全收益**：secret 不再出现在 URL；body 防篡改；5 分钟内同 delivery_id 抗重放；GitHub/GitLab/Harbor 三个主流 webhook 来源能开箱即用。
- **deprecation 时间表**：本 change 起 secret query 仍 work + WARN 日志；6 个月后单独 `enforce-webhook-signatures` change 移除 fallback。
