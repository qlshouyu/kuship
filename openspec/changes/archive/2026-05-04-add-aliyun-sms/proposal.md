## Why

Phase 11 (`migrate-console-misc`) 落地了 SMS 验证码端点（`/console/sms/send-code` / `register-by-phone` / `login-by-phone`）但只在 dev / local profile 把 code 直接 `log.info("[SMS-MVP] phone=... code=...")` 打到控制台：

- **prod 不可用**：现有代码没有真实 SMS 网关接入，prod 启动会用 dev 路径打日志（既泄露 code 又不发短信）
- **enterprise SMS 配置端点是占位**：`SMSConfigController` 返回 `{enabled:false, provider:"stub"}`，没有真实 provider 切换
- **限流缺失**：同一手机号无限制秒级 send-code，可被滥用做骚扰短信攻击
- **频率窗口缺失**：注册/登录失败的验证码不限制次数，可被暴力破解（10⁶ 全空间 6 位数）

阿里云 SMS（旧名"短信服务"，aliyun-dysms）是中国大陆事实标准 SMS provider：
- 国内主流 console / SaaS 默认接入
- 有 SDK V2.0（`com.aliyun:dysmsapi20170525`，maven central 可用）
- 资费 0.045 元/条，免费试用 100 条
- 支持模板 + 签名 + 国际短信
- API 通过阿里云 RAM AccessKey 鉴权（不是云控台密码）

本次 hardening 把阿里云 SMS 接入为默认 provider，dev profile 仍保留打印日志策略（无网关时也能跑），prod profile 不开启时拒绝启动 SMS 端点，限流 + 验证码次数限制兜底。

## What Changes

- 新增 SMS provider 抽象：`SmsProvider` 接口（`send(phone, code, purpose)` 返回 SmsResult）；2 实现：
  - `LoggingSmsProvider`（dev / local / contract-test 默认）—— 沿用 `[SMS-MVP] phone=... code=...` 日志
  - `AliyunSmsProvider`（prod 默认）—— 用 aliyun-dysmsapi 2.0 SDK 调用 SendSms API
- `SMSVerificationController.sendCode` 改造：发完落 DB 后调 `smsProvider.send(...)`，失败回滚事务（日志 ERROR）
- 新增配置项：
  - `kuship.sms.provider`：`logging` (default) / `aliyun`
  - `kuship.sms.aliyun.access-key-id` / `access-key-secret`（从环境变量 `ALIYUN_SMS_ACCESS_KEY_ID` / `ALIYUN_SMS_ACCESS_KEY_SECRET` 注入）
  - `kuship.sms.aliyun.sign-name`（短信签名，例如 "kuship"）
  - `kuship.sms.aliyun.template-code`（模板 ID，例如 "SMS_123456789"）
  - `kuship.sms.aliyun.endpoint`（默认 `dysmsapi.aliyuncs.com`）
- 新增限流：`SmsRateLimiter`（Caffeine cache）—— 单手机号每 60 秒最多 1 条；prod 默认开启，dev 关闭
- 新增验证码失败次数限制：单手机号 + purpose 5 分钟窗口最多 5 次失败比对 → 第 6 次起拒绝（防暴力破解）
- `SMSConfigController.update` 把 enterprise 级覆盖写入 `console_config` (key=`enterprise.{eid}.SMS_CONFIG`)，让多企业用不同模板 ID
- prod profile 启动检查：`kuship.sms.provider=aliyun` 时如果 access-key 缺失立即拒绝启动（`@PostConstruct` 校验）
- 集成测试：LoggingSmsProvider 路径（不破现有）+ AliyunSmsProvider 路径（mock SDK 客户端）+ 限流 60s 窗口 + 验证码 5 次失败拒绝
- 文档：CLAUDE.md 新增 "SMS 集成（add-aliyun-sms）" 段落

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `kuship-console-app`: 在 SMS 子域加 provider 抽象 / 阿里云接入 / 60s 限流 / 5 次失败暴破防护 / prod 启动检查 / enterprise 级配置覆盖。

## Impact

- **代码改动**：`SmsProvider` 接口 + 2 实现 (~120 LOC)；`SmsRateLimiter` (~60 LOC)；`SMSVerificationController` 注入 + 限流路径 (~30 LOC delta)；`SMSConfigController` 写 ConsoleConfig (~30 LOC)；启动校验组件 (~30 LOC)
- **依赖**：新增 `com.aliyun:dysmsapi20170525:3.0.0`（约 200KB JAR + transitive aliyun-tea / aliyun-credentials；总计约 2MB）
- **测试覆盖**：现有 135 不破；新增 6 个 IT 用例
- **运行时**：dev / local 行为不变（仍 log）；prod 用阿里云 SMS（每条调用 < 200ms）
- **配置增加**：5 个新 `kuship.sms.*` 配置项 + 2 个环境变量
- **GraalVM Native**：aliyun SDK 反射 hint 自动注册（SDK 已自带 `META-INF/native-image/` 元数据）；本 change 无需手写
