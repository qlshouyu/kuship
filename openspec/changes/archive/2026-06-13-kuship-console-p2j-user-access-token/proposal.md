## Why
用户访问令牌列表 `users/access-token`（GET）是纯 console 库读、可对 7070 校准，补齐它。

## What Changes
- 用户访问令牌列表（对齐 `UserAccessTokenCLView.get`）：`GET /console/users/access-token` → 当前用户 `user_access_key` 行，每项 `{note, expire_time, user_id, ID}`（expire_time 为 int，可空）。
- **不包含**：创建/删除令牌（POST/DELETE）。

## Capabilities
### New Capabilities
- `user-access-token-read`: 用户访问令牌列表读（纯 console 库）。

## Impact
- 代码：`modules/account` 新增 UserAccessKey 只读实体/仓储 + 服务 + controller。
- 数据：只读 user_access_key；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录），返回当前登录用户自己的令牌。
