## ADDED Requirements

### Requirement: 站点平台配置端点

kuship-console SHALL 实现 `GET /console/config/info`（公开匿名）+ `PUT /console/config/info?key=X` + `DELETE /console/config/info?key=X`（sys_admin 必需）共 3 endpoint。`ConsoleSysConfig` Entity 落地 `console_sys_config` 表（8 列含 `enable`/`type`/`value(varchar 4096)`），与 rainbond-console 共享同一张表，运维侧任一端写入对另一端立即可见。

#### Scenario: GET /console/config/info 匿名公开返回 22 个站点 key + 11 个顶层平铺字段

- **WHEN** 未登录浏览器调 `GET /console/config/info`
- **THEN** kuship-console 返回 HTTP 200，响应体 `data.bean` 含 22 个 key（含 `is_public`/`market_url`/`enterprise_center_oauth`/`version`/`is_user_register`/`oauth_services`/`title`/`logo`/`favicon`/`login_image`/`document`/`official_demo`/`is_regist`/`is_alarm`/`captcha_code`/`header_color`/`header_writing_color`/`sidebar_color`/`sidebar_writing_color`/`footer`/`shadow`/`enterprise_edition`/`security_restrictions`），每个 key 对应 `{enable: bool, value: any}` 对象
- **AND** `data.bean` 顶层另含 11 个平铺字段：`enterprise_id`/`is_disable_logout`/`is_offline`/`sso_enable`/`diy`/`enable_yum_oauth`/`diy_customer`/`is_delivery_version`/`portal_site`/`default_market_url`/`disable_logo`
- **AND** `data` 顶层含 `initialize_info: {}` 占位
- **AND** 响应包装符合 general_message 形状 `{code:200, msg:"query success", msg_show:"Logo获取成功", data:{bean,list:[]}}`
- **AND** `JwtAuthenticationFilter` 不拦截该端点（`SecurityConfig` GET permitAll 白名单已加 `/console/config/info[/]`）

#### Scenario: GET 时表内缺失的 key 自动写入默认值

- **GIVEN** `console_sys_config` 表内不存在 `TITLE` 行
- **WHEN** 任意客户端调 `GET /console/config/info`
- **THEN** kuship-console INSERT `console_sys_config (key='TITLE', type='string', value='', desc='Rainbond web tile', enable=1, enterprise_id='')`
- **AND** 响应 bean 含 `title: {enable: true, value: ""}`

#### Scenario: GET 时 type=json 的 value 反序列化为对象

- **GIVEN** `console_sys_config.DOCUMENT` 行 `type='json' value='{"platform_url":"https://www.rainbond.com/"}'`
- **WHEN** 调 `GET /console/config/info`
- **THEN** 响应 bean `document.value` 是 JSON 对象 `{"platform_url":"https://www.rainbond.com/"}`，不是字符串

#### Scenario: GET 兼容 rainbond 历史 Python repr 格式 value

- **GIVEN** `console_sys_config.DOCUMENT.value` 为 Python 风格 `{'platform_url': 'https://www.rainbond.com/'}`（单引号）
- **WHEN** 调 GET 端点
- **THEN** kuship-console 先尝试标准 JSON 解析失败 → fallback 用单引号转双引号再解析 → 成功返回对象
- **AND** 仍解析失败时 fallback 到默认值 + WARN 日志

#### Scenario: PUT 改 cfg_key 的 value 与 enable

- **GIVEN** 用户 sys_admin=true 已携带有效 JWT
- **WHEN** 调 `PUT /console/config/info?key=TITLE` body `{"value":"Kuship","enable":true}`
- **THEN** kuship-console UPDATE `console_sys_config SET value='Kuship', enable=1 WHERE key='TITLE'`
- **AND** 响应 `data.bean` 含 `title: {enable: true, value: "Kuship"}`

#### Scenario: PUT base_cfg_key 仅切 enable 不改 value

- **GIVEN** 用户 sys_admin=true
- **WHEN** 调 `PUT /console/config/info?key=IS_USER_REGISTER` body `{"value":true,"enable":false}`
- **THEN** kuship-console 仅 UPDATE enable=0；value 保持 base_cfg_keys_value 默认值
- **AND** 响应 `data.bean` 含 `is_user_register: {enable: false, value: <env-derived default>}`

#### Scenario: 非 sys_admin 用户 PUT 被拒

- **GIVEN** 普通用户（sys_admin=false）携带有效 JWT
- **WHEN** 调 `PUT /console/config/info?key=TITLE`
- **THEN** kuship-console 抛 `ServiceHandleException(403, "需要平台管理员权限")`
- **AND** 响应 `code: 403, msg_show: "需要平台管理员权限"`，HTTP 状态 200（rainbond 解耦约定）

#### Scenario: DELETE 重置 cfg_key 到默认值

- **GIVEN** 用户 sys_admin=true，`console_sys_config.TITLE.value='Custom'`
- **WHEN** 调 `DELETE /console/config/info?key=TITLE` body `{"value":"any"}`
- **THEN** kuship-console UPDATE `console_sys_config SET value='', enable=1, desc='Rainbond web tile' WHERE key='TITLE'`
- **AND** 响应 `data.bean` 含 `title: {enable: true, value: ""}`

#### Scenario: DELETE 不允许重置 base_cfg_key

- **WHEN** 调 `DELETE /console/config/info?key=IS_PUBLIC`
- **THEN** kuship-console 返回 `code:404, msg:"can not delete key value", msg_show:"该配置不可重置"`，不修改 DB

#### Scenario: 占位字段不在响应中（UI 端 || 兜底）

- **WHEN** 调 `GET /console/config/info`
- **THEN** 响应 bean 中**不包含** `customer_service_qrcode` / `login_slogan` / `login_title` / `privacy_policy_url` / `service_agreement_url` / `enterprise_alias`（依赖未迁移的 CustomConfig 表 + EnterpriseConfigService merge）
- **AND** UI 全部 `rainbondInfo.X || defaultValue` 形式访问，不会 NPE
