## Why
用户自定义配置列表 `users/custom_configs`（GET）是纯 console 库读、可对 7070 校准，补齐它。

## What Changes
- 用户自定义配置列表（对齐 `CustomConfigsUserCLView.get` + `list_by_user_nick_name`）：`GET /console/users/custom_configs` → 当前用户（nick_name）的 `console_config` 行（`.values()` 全列），每项 `{ID, key, value, description, update_time, user_nick_name}`，`msg_show=操作成功`。
- **不包含**：写（POST bulk_create_or_update）、删。

## Capabilities
### New Capabilities
- `user-custom-configs-read`: 用户自定义配置列表读（纯 console 库）。

## Impact
- 代码：`modules/account` 新增 ConsoleConfig 只读实体/仓储 + 服务 + controller。
- 数据：只读 console_config；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录），按当前用户 nick_name 过滤。
