## Context

Phase 11 (`migrate-console-misc`) 实现：
- 3 endpoint：`POST /console/sms/send-code` / `register-by-phone` / `login-by-phone`
- 1 entity：`SmsVerificationCode`（6 列，PK 是小写 `id`）
- 1 repository：`SmsVerificationCodeRepository`
- 1 controller：`SMSConfigController`（占位 GET/PUT 返 `{enabled:false}`）
- 1 controller：`SMSVerificationController`（dev 打印 code）

阿里云 SMS（dysmsapi）业界基准：
- SDK：`com.aliyun:dysmsapi20170525:3.0.0`（最新稳定版 2026 年仍维护）
- 调用流程：`Client(Config) -> sendSms(SendSmsRequest) -> SendSmsResponse`
- Config：access-key-id + access-key-secret + endpoint
- Request：phone + signName + templateCode + templateParam（JSON `{"code":"123456"}`）
- 错误码：`isv.BUSINESS_LIMIT_CONTROL`（限流）/ `isv.MOBILE_NUMBER_ILLEGAL`（手机号非法）等

国内 SMS provider 商业生态：
- 阿里云 dysmsapi（最常见）
- 腾讯云 SMS（次普及，SDK `tencentcloud-sdk-java`）
- 华为云 / 网易云信（小众）
- twilio（国际）

本次首版仅接阿里云。腾讯云作为 sibling provider 留作 `add-tencent-sms` change（同样实现 `SmsProvider` 接口）。

## Goals / Non-Goals

**Goals:**
- prod profile 真实发送 SMS（阿里云 dysmsapi）
- dev / local / contract-test profile 沿用 logging 行为（不引入对真实凭据的依赖）
- `SmsProvider` 接口可扩展（多 provider 切换）
- 60s 单手机号限流防骚扰
- 5 分钟 5 次失败窗口防暴破
- prod 启动时若 `kuship.sms.provider=aliyun` 但凭据缺失 → 立即拒绝启动
- 不破现有 135/135 测试

**Non-Goals:**
- 国际短信支持（仅大陆 11 位手机号）
- 短信模板自动审批 / 创建（手动在阿里云控台预先创建模板，配置 templateCode）
- 多 provider 投递回退（如阿里云失败自动切腾讯云）
- 短信回执回调处理（阿里云回调 webhook 留作独立 hardening）
- 用户级（非企业级）SMS 模板自定义
- 短信定时延迟发送

## Decisions

### 1. provider 抽象接口

**选择**：`SmsProvider` 接口 + Spring `@Conditional` profile 选择实现

```java
public interface SmsProvider {
    SmsResult send(String phone, String code, String purpose);
}

@Component
@ConditionalOnProperty(name = "kuship.sms.provider", havingValue = "logging", matchIfMissing = true)
class LoggingSmsProvider implements SmsProvider { ... }

@Component
@ConditionalOnProperty(name = "kuship.sms.provider", havingValue = "aliyun")
class AliyunSmsProvider implements SmsProvider { ... }
```

**为什么**：
- 让未来加 tencent / huawei provider 时只加新 `@Component`
- prod / dev 切换通过 yaml 配置，不需要 profile 黑魔法
- `matchIfMissing=true` 让默认值是 `logging`（不破 dev 测试）

**替代方案**：
- 反射加载 `Class.forName(provider)` → 不利于 GraalVM native；放弃
- 强制 prod profile 用 aliyun → 灵活性差，单元测试不便

### 2. AliyunSmsProvider 启动时延迟构造 `Client`

**选择**：`@Bean Client aliyunSmsClient(...)` 构造方法 + `@ConditionalOnProperty(provider=aliyun)`

**为什么**：
- 应用启动时构造 SDK Client（Config + endpoint），整个生命周期复用
- `@ConditionalOnProperty` 让 dev profile 时 Bean 不存在，不引入 access-key 依赖
- prod 启动时 access-key 缺失 → bean 构造抛 IllegalStateException → Spring fail-fast

### 3. 启动校验：`@PostConstruct`

**选择**：`AliyunSmsProvider.@PostConstruct check()` 验证 access-key-id / access-key-secret / sign-name / template-code 全部非空，否则抛 IllegalStateException 拒绝启动

**为什么**：
- 比"运行时调用时才发现"更早
- 给运维清晰的启动失败信号
- 与 `JwtTokenService` 的 prod 启动校验一致

### 4. 限流：Caffeine cache + `tryLock`

**选择**：
- key=`sms:send:<phone>` value=Boolean.TRUE
- TTL=60s（GitHub 推荐间隔）
- maxSize=10000
- `tryAccept(phone)` 命中已有 key → false（限流）；新 key 写入 cache → true

**为什么**：
- 60s 是阿里云推荐的最低发送间隔
- in-memory：单实例够用；多实例集群留 `add-distributed-sms-rate-limit` change（Redis 后端）
- maxSize 10000 ≈ 10000 用户峰值

### 5. 验证码失败限流（暴破防护）

**选择**：
- 第二个 Caffeine cache：key=`sms:verify-fail:<phone>:<purpose>`，value=Long counter
- TTL=5min，maxSize=10000
- 每次 verify 失败计数 +1，命中 5 次后拒绝（直到 cache 过期）

**为什么**：
- 6 位 code 暴破空间 10⁶；不限速攻击者 60s 内能跑完
- 5 次窗口取自 GitHub / Google 通用做法
- 与单手机号 60s 发送限流配合，攻击者最多 5 分钟能发 5 次 code，每次 code 5 次猜测 → 攻击空间 25/10⁶ = 0.0025% 命中

### 6. enterprise 级模板覆盖：复用 ConsoleConfig

**选择**：`SMSConfigController.update` 写 `console_config` (key=`enterprise.{eid}.SMS_CONFIG`)，存 JSON `{provider, signName, templateCode}`；`AliyunSmsProvider.send` 在发送时按 enterprise_id 查覆盖

**为什么**：
- ConsoleConfig 已有 key 命名约定 `enterprise.{eid}.{name}`
- 多企业 / 多租户场景下不同企业用不同模板 + 签名（avoiding 多模板审批共用）
- 默认值仍是 application.yaml 全局配置

### 7. 短信文本：模板参数 `{"code":"<code>"}`

**选择**：阿里云 templateCode 假设是 `"您的验证码是${code}，5分钟内有效"`，本服务发送 templateParam=`{"code":"<6 digit>"}`

**为什么**：
- 阿里云 SMS 必须用预审批模板；`templateCode` 来自控台
- 单一参数 `code` 便于换 provider（腾讯云 / 华为云）时复用模板

## Risks / Trade-offs

- **[Risk]** 阿里云 access-key 泄露 → 攻击者发送骚扰短信耗费用户钱
  → **Mitigation**：CLAUDE.md 写明 RAM 子账号 + 仅授权 `AliyunDysmsFullAccess`；prod 用 K8s Secret + ServiceAccount 注入
- **[Risk]** 60s 限流绕过：攻击者从多 IP 用同一手机号
  → **Mitigation**：限流 key 是 phone（不是 IP），跨 IP 仍生效
- **[Risk]** 5 分钟 5 次失败仍可被攻击者利用：每 5min 重发 + 5 次猜测，1 天 = 288 个 5min 窗口 = 1440 次猜 → 命中率 0.144%
  → **Mitigation**：在登录端额外加 reCAPTCHA / 滑块，本 change 不覆盖；CLAUDE.md 写明
- **[Risk]** in-memory 限流多实例不共享
  → **Mitigation**：CLAUDE.md 写明 single-instance 假设；多实例 hardening 留独立 change
- **[Risk]** GraalVM native 与 aliyun SDK 反射兼容
  → **Mitigation**：aliyun SDK 自带 `META-INF/native-image/com.aliyun/dysmsapi20170525/native-image.properties`；如启动失败补 RuntimeHints

## Migration Plan

dev / local / contract-test 行为不变（默认 provider=logging）。prod 切换流程：
1. 在阿里云控台创建短信签名 + 模板 → 拿到 signName + templateCode
2. 创建 RAM 子账号 → 拿 access-key-id + access-key-secret
3. K8s Secret：
   ```yaml
   apiVersion: v1
   kind: Secret
   metadata: { name: kuship-sms }
   stringData:
     ALIYUN_SMS_ACCESS_KEY_ID: <id>
     ALIYUN_SMS_ACCESS_KEY_SECRET: <secret>
   ```
4. Deployment 注入 env：`envFrom: - secretRef: { name: kuship-sms }`
5. application-prod.yaml 配置：
   ```yaml
   kuship:
     sms:
       provider: aliyun
       aliyun:
         sign-name: kuship
         template-code: SMS_123456789
   ```
6. 部署后 curl `/console/sms/send-code` 验证

回滚：`kuship.sms.provider=logging` 切换回打日志。无 schema / 数据迁移影响。

## Open Questions

- 是否同时实现腾讯云 provider？倾向 NO 首版；用户需要时单独 `add-tencent-sms` change
- 短信回执回调（delivery report）如何处理？倾向 NO 首版；阿里云回调走独立 webhook 端点 `/console/sms/aliyun-callback`，留作独立 hardening
- 是否给 SMS 调用记审计（OperationLog）？倾向 NO：SMS 不涉及业务变更，runtime log 足够；如需要单独 change
