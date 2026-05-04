## 1. WebhookSignatureVerifier 工具类

- [x] 1.1 新增 `cn.kuship.console.modules.misc.webhook.security.WebhookSignatureVerifier`
- [x] 1.2 实现 `verifyGitHub(byte[] body, String headerSig, String secret)` —— 解析 sha256= 前缀 + HMAC-SHA256 + MessageDigest.isEqual
- [x] 1.3 实现 `verifyGitLab(String headerToken, String secret)` —— 常量时间比对
- [x] 1.4 实现 `verifyHarbor(String authHeader, String secret)` —— 解析 Bearer + 常量时间比对
- [x] 1.5 实现 `verifyCustom(byte[] body, String headerSig, String secret)` —— 复用 GitHub HMAC 算法
- [x] 1.6 hex string 解析：手写 hexToBytes，失败返 null（不依赖 Apache Commons）
- [x] 1.7 单元测试 `WebhookSignatureVerifierTest` —— 5 个 verify 方法 12 用例（pass / fail / 边界）→ 12/12 pass

## 2. WebhookDeliveryDeduper（反重放）

- [x] 2.1 新增 `WebhookDeliveryDeduper` (@Component，Caffeine cache)
- [x] 2.2 cache 配置：maxSize=1024 + expireAfterWrite=Duration.ofMinutes(5)
- [x] 2.3 提供 `tryAccept(String serviceId, String deliveryId)` —— 返 true 接受新 delivery
- [x] 2.4 deliveryId 为 null 或 blank 时直接 returns true（向后兼容）
- [x] 2.5 单元测试 `WebhookDeliveryDeduperTest` 3/3 pass

## 3. WebhookTriggerController 改造

- [x] 3.1 把 `@RequestBody Map<String,Object>` 改为 `@RequestBody(required=false) byte[] body`（HMAC 必须对 raw bytes）
- [x] 3.2 注入 `WebhookSignatureVerifier` + `WebhookDeliveryDeduper`
- [x] 3.3 git trigger：优先 X-Hub-Signature-256 → 否则 X-Gitlab-Token → 否则 secret query fallback + WARN
- [x] 3.4 image trigger：优先 Authorization Bearer → 否则 secret query fallback + WARN
- [x] 3.5 custom trigger：优先 X-Kuship-Signature → 否则 secret query fallback + WARN
- [x] 3.6 通过验签后调 deduper.tryAccept，命中重复返 200 + `{triggered:false, dedup:true}`
- [x] 3.7 通过验签 + 通过 dedup 后调 lifecycleOps.upgradeService 返 200 + `{triggered:true}`

## 3.5 SecurityConfig permitAll（实施时发现）

- [x] 3.5.1 给 `POST /console/webhooks/*` / `POST /console/image/webhooks/*` / `POST /console/custom/deploy/*` 加 permitAll —— 否则 GitHub/GitLab 调用方（不带 JWT）会先被 Spring Security 401 拦掉

## 4. WebhookManageController.getUrl 升级

- [x] 4.1 响应新增 `git_webhook_url_v2`（不带 secret query）
- [x] 4.2 响应新增 `image_webhook_url_v2`
- [x] 4.3 响应新增 `custom_webhook_url_v2`
- [x] 4.4 响应新增 `signature_examples` 字段（4 种 curl 示例）
- [x] 4.5 旧字段 `git_webhook_url` 等保留兼容
- [x] 4.6 文档化 signature_examples 字段供前端 UI 渲染（在 CLAUDE.md webhook 段落写明）

## 5. 集成测试

- [x] 5.1 新增 `WebhookHmacIntegrationTest`（@SpringBootTest，local + contract-test）
- [x] 5.2 case：GitHub HMAC 通过 → 200 + triggered:true
- [x] 5.3 case：GitHub HMAC 错误 → 401
- [x] 5.4 case：GitLab token 通过 → 200
- [x] 5.5 case：Harbor bearer 通过 → 200
- [x] 5.6 case：custom HMAC 通过 → 200
- [x] 5.7 case：secret query fallback 通过（WARN 日志通过 controller log 输出验证）
- [x] 5.8 case：同 delivery_id 第二次 → dedup:true 且 lifecycleOps 调用次数受控
- [x] 5.9 case：v2 URL（无 query）+ HMAC header 通过

## 6. 文档

- [x] 6.1 `kuship-console/CLAUDE.md` 新增"Webhook HMAC 签名（harden-webhook-hmac）"段落
- [x] 6.2 4 种 header 签名格式表（GitHub / GitLab / Harbor / custom）+ curl 示例
- [x] 6.3 secret query deprecation 时间表（6 个月警告期 + enforce-webhook-signatures 后续 change）
- [x] 6.4 运维监控 WARN 日志指引（grep "using deprecated query secret"）
- [x] 6.5 dedup cache 局限：单实例 in-memory，多实例集群推荐 Redis（add-distributed-webhook-dedup hardening）

## 7. 验证收尾

- [x] 7.1 `mvn test` → **135/135 pass**（112 既有 + 12 verifier 单测 + 3 deduper 单测 + 8 webhook IT）
- [x] 7.2 `bash scripts/native-test.sh --quick` → **[SUMMARY] passed=4 failed=0 skipped=0**
- [x] 7.3 `openspec validate harden-webhook-hmac --strict` 通过
