## ADDED Requirements

### Requirement: SmsProvider 抽象接口

系统 SHALL 提供 `SmsProvider` 接口（位于 `cn.kuship.console.modules.misc.sms.provider`），暴露 `send(String phone, String code, String purpose)` 方法返回 `SmsResult`；至少 SHALL 提供 `LoggingSmsProvider`（默认 + dev / local / contract-test profile）和 `AliyunSmsProvider`（prod）两种实现，通过 `kuship.sms.provider` 配置项选择；缺省值 `logging`。

#### Scenario: 默认配置使用 LoggingSmsProvider
- **WHEN** 应用启动且 `kuship.sms.provider` 未设置或等于 `logging`
- **THEN** Spring 上下文 SHALL 注入 `LoggingSmsProvider`
- **AND** 调用 `send` SHALL 仅打印 `[SMS-MVP] phone=... code=...` 日志，不发真实短信

#### Scenario: aliyun 配置使用 AliyunSmsProvider
- **WHEN** 应用启动且 `kuship.sms.provider=aliyun`
- **AND** 4 个必需配置项（access-key-id / access-key-secret / sign-name / template-code）全部非空
- **THEN** Spring 上下文 SHALL 注入 `AliyunSmsProvider`
- **AND** 调用 `send` SHALL 通过 aliyun-dysmsapi SDK 发送真实短信

### Requirement: AliyunSmsProvider 启动校验

`AliyunSmsProvider` SHALL 在 `@PostConstruct` 阶段验证 4 个必需配置项（access-key-id / access-key-secret / sign-name / template-code），缺任一项 SHALL 抛 `IllegalStateException` 让 Spring fail-fast 拒绝启动。

#### Scenario: 缺 access-key-id 拒绝启动
- **WHEN** 应用以 `kuship.sms.provider=aliyun` 启动但 `kuship.sms.aliyun.access-key-id` 为空
- **THEN** AliyunSmsProvider 的 @PostConstruct SHALL 抛 IllegalStateException("aliyun-sms: access-key-id is required")
- **AND** Spring SHALL 拒绝启动整个 ApplicationContext

#### Scenario: 4 项齐全则启动通过
- **WHEN** 4 项配置都非空
- **THEN** AliyunSmsProvider SHALL 成功初始化 SDK Client 实例
- **AND** 控制台日志 SHALL 输出 `aliyun-sms provider initialised, endpoint=<endpoint>`

### Requirement: 单手机号 60s 限流

`SMSVerificationController.sendCode` SHALL 在调 SmsProvider 前查 `SmsRateLimiter` 限流；同一手机号 60 秒窗口内最多发 1 条；命中限流 SHALL 返回 429 + `{detail: "rate limited", code: 429}` 风格错误。

#### Scenario: 60s 内第二次请求被拒
- **WHEN** 客户端 1 秒内对同一手机号连续调 send-code 两次
- **THEN** 第二次 SHALL 返回业务错误 + msg 含"频率"
- **AND** 数据库 SHALL 仅插入一条 sms_verification_code 记录

#### Scenario: 不同手机号互不影响
- **WHEN** phone-a 与 phone-b 各自请求 send-code
- **THEN** 两次都 SHALL 通过限流
- **AND** 各自插入 sms_verification_code 记录

### Requirement: 验证码失败次数限流

注册 / 登录验证码比对 SHALL 在 5 分钟窗口对单 (phone, purpose) 计数失败次数；失败 ≥ 5 次后 SHALL 拒绝继续比对（不去查 DB），并返回业务错误"验证码已锁定，请稍后重试"。

#### Scenario: 第 5 次失败正常返回 401
- **WHEN** 客户端连续 5 次用错码 verify
- **THEN** 5 次都 SHALL 返回 401 "code mismatch"
- **AND** 计数器累加到 5

#### Scenario: 第 6 次失败被锁定
- **WHEN** 已经失败 5 次后再次 verify
- **THEN** SHALL 立即返回 429 + "验证码已锁定，请稍后重试"
- **AND** 不查询 sms_verification_code 表

#### Scenario: 5 分钟后窗口重置
- **WHEN** 失败 5 次后等待 5 分钟以上
- **THEN** 计数器 SHALL 因 cache 过期被清空
- **AND** 下次 verify SHALL 重新走 DB 比对

### Requirement: 配置项规范

应用 SHALL 通过以下 yaml 配置项控制 SMS 行为；prod profile 推荐通过环境变量注入凭据：

| 配置项 | 默认值 | 来源建议 |
|---|---|---|
| `kuship.sms.provider` | `logging` | yaml |
| `kuship.sms.aliyun.access-key-id` | （空） | env `ALIYUN_SMS_ACCESS_KEY_ID` |
| `kuship.sms.aliyun.access-key-secret` | （空） | env `ALIYUN_SMS_ACCESS_KEY_SECRET` |
| `kuship.sms.aliyun.sign-name` | （空） | yaml |
| `kuship.sms.aliyun.template-code` | （空） | yaml |
| `kuship.sms.aliyun.endpoint` | `dysmsapi.aliyuncs.com` | yaml |
| `kuship.sms.rate-limit.enabled` | `true` (prod) / `false` (dev) | yaml |

#### Scenario: 配置项默认值正确
- **WHEN** 应用以 dev profile 启动
- **AND** 未显式设 `kuship.sms.*`
- **THEN** Spring 配置 SHALL 解析出 provider=logging、rate-limit.enabled=false
- **AND** 应用启动通过 + 调用 send-code 不发真实短信

### Requirement: GraalVM Native 兼容

aliyun-dysmsapi SDK SHALL 在 GraalVM native binary 下可用；如 SDK 自带的 `META-INF/native-image/` hint 不全，SHALL 在 `KuShipConsoleRuntimeHints` 补充必要反射类型。

#### Scenario: native binary 启动 aliyun provider
- **WHEN** 用 native 模式启动应用
- **AND** `kuship.sms.provider=aliyun` 且 4 项配置齐全
- **THEN** AliyunSmsProvider SDK Client SHALL 成功构造
- **AND** 调用 send 不抛 ClassNotFoundException 或 NoSuchMethodException

### Requirement: 文档与运维指引

`kuship-console/CLAUDE.md` SHALL 新增 "SMS 集成（add-aliyun-sms）" 段落，列出：provider 选择策略、阿里云模板创建步骤、RAM 子账号 + K8s Secret 注入流程、限流策略、暴破防护、未来 hardening（多 provider / 回执回调 / 分布式限流）。

#### Scenario: 文档段落齐全
- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档 SHALL 包含 "SMS 集成（add-aliyun-sms）" 段落
- **AND** 段落 SHALL 至少含：provider 切换矩阵、RAM 子账号建议、限流参数、暴破防护说明
