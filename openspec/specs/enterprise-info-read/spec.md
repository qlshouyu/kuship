# enterprise-info-read Specification

## Purpose
TBD - created by archiving change kuship-console-p2h-enterprise-info. Update Purpose after archive.
## Requirements
### Requirement: 企业信息
系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/info`（对齐 `EnterpriseRUDView.get`），返回 bean = tenant_enterprise 字段（`ID`/`enterprise_id`/`enterprise_name`/`enterprise_alias`/`create_time`(空格格式 yyyy-MM-dd HH:mm:ss)/`enterprise_token`/`is_active`/`enable_team_resource_view`） + `default_region`（{}）+ 配置项（`OAUTH_SERVICES` 与 23 个 cfg_keys：每个 `console_sys_config` 行按 key 唯一读取，输出 `key.lower(): {enable, value}`，json 类型 value 解析为结构）+ `default_market_url`("")、`disable_logo`(false)。

#### Scenario: 返回企业信息
- **WHEN** 已认证用户请求 `GET /console/enterprise/{enterprise_id}/info`
- **THEN** 返回 `code=200`、`msg_show=查询成功`，bean 含企业字段 + default_region + 各配置项 {enable,value}，与 7070 一致

#### Scenario: 配置项 json 值解析
- **WHEN** 某配置项 type=json（如 appstore_image_hub/visual_monitor/oauth_services）
- **THEN** 其 `value` 为解析后的结构（dict/list），与 7070 一致

