## Context

kuship-ui 顶层 layout 在用户进入登录页时通过 `getRainbondInfo`（`src/services/api.js:712`，`passAuthorization: false`）匿名拉取站点元数据，DVA model `global.fetchRainbondInfo` 把响应 bean 存到 redux `global.rainbondInfo`。 grep 显示 18 个组件读取 `rainbondInfo.{title,logo,favicon,document,footer,version,is_public,oauth_services,enterprise_center_oauth,is_saas,default_market_url,diy_customer,...}` 等字段，登录页 / 用户中心 / 应用市场入口都依赖此响应。

rainbond-console 端逻辑：`ConfigRUDView(AlowAnyApiView)`（`views/logos.py:61`）匿名公开 → 调 `platform_config_service.initialization_or_get_config`（property，懒加载 + 缺失则写入默认）→ 加企业 / 环境变量字段 → `general_message` 包装。

## Goals

- 让 kuship-ui 登录页能正常渲染（解除 401 阻塞）
- 与 rainbond-console 共享 `console_sys_config` 表，使 rainbond-console 端的运维更新（rainbond Django admin / Python script）对 kuship-console 立即生效，反之亦然
- PUT/DELETE 完整迁移，覆盖运维侧"改 logo/标题/footer/SHADOW 开关"等真实场景
- 占位字段（OAuth / custom_fields / enterprise merge）明确标注为 follow-up

## Non-Goals

- OAuth services 真查（依赖 `OAuthServices` entity 未迁移，独立 change）
- enterprise_center_oauth 真查（同上）
- `get_custom_fields()` 自定义字段（依赖 CustomConfig 表，未迁移）
- 用户登录态合并 EnterpriseConfigService（已有 EnterpriseConfigController 但未串通调用，独立 change）
- 实时配置变更广播（rainbond Python 端有 `custom_settings.reload()`，本次不实现）

## Decisions

### 决策 1：复用 `console_sys_config` 表，不复用 `console_config`

`console_config`（kuship 已有）和 `console_sys_config`（本次新增）是 **rainbond 历史划分的两张不同表**：

| 表 | 用途 | schema |
|----|------|--------|
| `console_sys_config` | 站点平台级 KV（22 列种子已存在） | `ID/key/type/value(varchar4096)/desc/enable/create_time/enterprise_id` |
| `console_config` | 用户级 / 企业配置 KV | `ID/key/value/description/update_time/user_nick_name` |

二者 schema 不同（前者有 `enable/type/desc`，后者有 `update_time/user_nick_name`），且数据语义不同。强行复用一表会破坏 rainbond-console 的写路径。

### 决策 2：默认值常量化在 Java，不存到 DB

rainbond 把默认值写在 `services/config_service.py::cfg_keys_value` Python dict，每次启动如果发现 DB 没有就 INSERT。kuship-console 复刻该行为：

- `PlatformConfigDefaults.CFG_DEFAULTS` / `BASE_CFG_DEFAULTS` 是 `Map<String, DefaultEntry>` 静态常量
- `PlatformConfigService.initializationOrGetConfig()` 第一次调用时如发现 DB 缺 key 就 INSERT
- 这样 rainbond-console 与 kuship-console 都跑过一遍后，DB 里的 key 集合是 22 个并集；任一端可读可改

**为什么不在 Flyway 里灌默认值**：违反 kuship-console 宪章 "Flyway baseline-only，db/migration 目录刻意为空，schema 演进权属于 rainbond-console"。如果用 Flyway 灌种子，第一次跑会成功，但 rainbond-console 后续如果改了 key 集合（例如 v6.8 加新 cfg_key）会与 kuship 默认表分裂。运行时按需写入是已验证的兼容路径。

### 决策 3：PUT/DELETE 强制 sys_admin 校验

rainbond-console 原版 `ConfigRUDView` 用 `AlowAnyApiView`（**完全无鉴权**），意味着任何人都能改站点 logo / TITLE。这是 rainbond 的历史漏洞，不应在 kuship-console 复刻。

新策略：
- `GET` 保持公开（登录页必须能匿名拉）
- `PUT` / `DELETE` 走 JWT 默认通道，controller 内显式 `if (!ctx.isSysAdmin()) throw 403`

理由：站点级配置只对平台管理员有意义；普通用户改 LOGO 是攻击面。`RequestContext.sysAdmin` 已经在 JwtAuthenticationFilter 里加载好。

### 决策 4：响应 bean 形状严格对齐 rainbond，不转驼峰

UI 直接 `rainbondInfo.title`、`rainbondInfo.is_public`、`rainbondInfo.default_market_url` —— 全部 snake_case。Jackson 输出时统一 `key.toLowerCase()`，顶层平铺字段同样 snake_case。绝对不能输出 `defaultMarketUrl`/`isPublic`，会破坏 UI。

GeneralMessageResponseBodyAdvice 自动包 `Map<String,Object>` 为 `data.bean`，不需要 `@JsonProperty` 单独注解。

### 决策 5：JSON value 用 Spring Boot 4 的 Jackson 3 (`tools.jackson.databind`)

Spring Boot 4 用 Jackson 3.x（包名 `tools.jackson.databind.*`），项目宪章 CLAUDE.md 已明确。`type=json` 的 cfg_key（如 `DOCUMENT={"platform_url":"..."}`）落库存为 string，读取时 `objectMapper.readValue(value, Object.class)` 反序列化。**不要**用 Python `eval()` 风格的兜底（rainbond 端用 `eval(repr(value))` 是历史毛刺，Java 端走标准 JSON）。

种子数据 `console_sys_config.value` 里 `DOCUMENT` 现存为 `{'platform_url': 'https://www.rainbond.com/'}`（**Python repr，单引号**），不是合法 JSON。读到这个会抛 `JsonParseException`。容错策略：

- 优先 `objectMapper.readValue(value, Object.class)` 解析
- 失败时 fallback 用 `value.replace('\'', '"')` 单转双引号再解
- 二次失败：用默认值 + WARN 日志（标记 service-side data corruption）

把 fallback 写明在 service `parseValue()` method，不要散在多处。

### 决策 6：占位字段 explicit 标注

UI 实际访问的字段中以下 6 个无对应 cfg_key（rainbond 端从 custom_fields 取，依赖未迁移的表）：

- `customer_service_qrcode` / `login_slogan` / `login_title` / `privacy_policy_url` / `service_agreement_url` / `enterprise_alias`

**响应 bean 中不输出这些字段**。grep 验证 UI 全部用 `rainbondInfo.X || defaultValue` 兜底，不会 NPE。后续 `migrate-console-custom-config` change 落地时再补全。

文档化在响应 controller 注释，且 `proposal.md::Impact` 列了 follow-up 路线。

## Risks / Trade-offs

- **Risk: 写并发**：rainbond-console 与 kuship-console 同时启动时都会触发 `initializationOrGetConfig()` 写默认值，可能 race。已有 `console_sys_config.key` UNIQUE index 兜底（DESC 输出确认），并发写第二者抛 DuplicateKeyException → service 捕获后切换为 `findByKey` 重读。`@Transactional` + try-catch 在 service.upsertDefault 内闭合。
- **Risk: 老种子数据 `eval(repr())` 格式**：DOCUMENT 等 json 列存 Python single-quote 形式。决策 5 的 fallback 处理；如未来 rainbond 端写入新格式（双引号）也兼容。
- **Trade-off: PUT 行为偏离 rainbond**：rainbond 端 PUT 接口允许任何登录用户改（甚至 anonymous）。本次收口到 sys_admin。如有运维脚本依赖匿名 PUT 会断（未发现）；如发现可改为 `@RequirePerm` 形式开放白名单。
- **Trade-off: Flyway 不灌种子**：第一次启动有 22 次 INSERT 写入；冷启动慢约 100ms。可接受。

## Migration Plan

无 schema 变更（共享 rainbond 已有表）。仅代码上线。

## Open Questions

无。占位字段已在 proposal 中文档化，等后续 change 推进。
