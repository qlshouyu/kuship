## Why

kuship-ui 登录页加载时匿名调用 `/console/config/info` 获取站点元数据（logo / title / favicon / version / OAuth 列表 / 企业版标识 / shadow / 默认市场 URL 等），用于渲染。`migrate-console-misc`（第 11 阶段）漏掉了这一站点级 KV 配置端点的迁移——目前 kuship-console 既没有对应 controller，也没有把它放入 `SecurityConfig` permitAll 白名单，导致登录页 401，前端整页无法渲染。

继续走临时桩会让站点配置（TITLE / LOGO / SHADOW 等）始终是硬编码默认值，永远不会从 `console_sys_config` 表读取真实数据。本次按 13 阶段路线把 `views/logos.py::ConfigRUDView` + `services/config_service.py::PlatformConfigService` 两段 Python 代码完整迁移到 kuship-console，与 rainbond-console 共享同一张 `console_sys_config` 表（22 个种子 key 已存在）。

## What Changes

- **新增 entity**：`ConsoleSysConfig`（`console_sys_config` 表，8 列）—— 与 `ConsoleConfig`（`console_config` 表，第 4 阶段已有）是不同表，不可复用
- **新增 service**：`PlatformConfigService.initializationOrGetConfig()` —— 复刻 `ConfigService.initialization_or_get_config` property + 5 个 base_cfg key + 17 个 cfg key 的默认值表
- **新增 controller**：`PlatformConfigController`：
  - `GET /console/config/info`（公开）—— 返回 22 个 key 的 `{enable, value}` 对 + 11 个顶层平铺字段（`enterprise_id`/`is_disable_logout`/`is_offline`/`sso_enable`/`diy`/`enable_yum_oauth`/`diy_customer`/`is_delivery_version`/`portal_site`/`default_market_url`/`disable_logo`）+ `initialize_info` 占位 `{}`
  - `PUT /console/config/info?key=X`（sys_admin 必需）—— base_cfg 改 enable / cfg 改 value+enable
  - `DELETE /console/config/info?key=X`（sys_admin 必需）—— 重置为默认值
- **修改 SecurityConfig**：`GET /console/config/info[/]` 加入 permitAll 白名单（与既有 `/console/enterprise/info`、`/console/perms` 同级）

## Capabilities

### Modified Capabilities

- `kuship-console-app`: 新增 1 条 Requirement —— 站点平台配置 `console_sys_config` 端点 (`GET/PUT/DELETE /console/config/info`)。

## Impact

- **新增包**：`cn.kuship.console.modules.misc.platformconfig`（3 个类：`PlatformConfigController` / `PlatformConfigService` / `PlatformConfigDefaults` 常量表）
- **新增 entity**：`cn.kuship.console.modules.misc.config.entity.ConsoleSysConfig`（写在既有 `misc.config` 子域内，与 `EnterpriseConfigController` 同级）
- **新增 repository**：`ConsoleSysConfigRepository`（`findByKey`、`saveAll`、`existsByKey`）
- **不引入新依赖**：默认值常量纯 Java 常量；环境变量通过 `@Value("${...:default}")` 读取
- **占位字段**（文档化为 follow-up）：
  - `oauth_services` / `enterprise_center_oauth` —— 占位返回 `[]` / `null`，依赖未迁移的 `OAuthServices` entity
  - `is_user_register` —— 占位返回 `IS_PUBLIC` 同值
  - 6 个 custom 字段（customer_service_qrcode / login_slogan / login_title / privacy_policy_url / service_agreement_url / enterprise_alias）—— 不输出，UI 已做 `||` 兜底
  - 用户登录态企业配置 merge —— 跳过（始终返回平台默认；`EnterpriseConfigService` 串通留作独立 hardening）
- **测试**：扩展 1 个集成测试断言 GET 返回 22 个 key 形状正确 + 公开访问无需 JWT
