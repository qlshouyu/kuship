## Why
企业信息 `enterprise/{eid}/info` 是企业域核心读，多源（tenant_enterprise + console_sys_config 配置）。补齐它。

## What Changes
- 企业信息（对齐 `EnterpriseRUDView.get`）：`GET /console/enterprise/{enterprise_id}/info` → bean = tenant_enterprise.to_dict（ID/enterprise_id/enterprise_name/enterprise_alias/create_time(**空格格式**)/enterprise_token/is_active/enable_team_resource_view） + default_region({}) + 配置项（OAUTH_SERVICES + 23 cfg_keys，console_sys_config 按 key 全局唯一读取，key.lower()→{enable,value}）+ default_market_url("")/disable_logo(false)。
- 新增 `ConsoleSysConfig` 只读实体（console_sys_config）。json 类型 value 为 Python repr，解析为结构（None→null/True→true/False→false/单→双引号 + jackson）。
- **不包含**：config 写（PUT info?key=）、ENABLE_CLUSTER provision、自定义字段/企业版字段（env 派生，本环境默认）。

## Capabilities
### New Capabilities
- `enterprise-info-read`: 企业信息读（tenant_enterprise + console_sys_config 配置多源）。

## Impact
- 代码：`ConsoleSysConfig` 实体/仓储；`EnterpriseInfoService`；`EnterpriseController` 加 GET info。pom 显式 jackson-databind（解析 config json 值）。
- 数据：只读 tenant_enterprise + console_sys_config；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录）。
- 简化：default_region={}（ENABLE_CLUSTER 未开）；default_market_url/disable_logo 取 env 默认；config 行按 key 全局唯一查（eid 仅元数据）。
