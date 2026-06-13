## Why
用户模糊查询 `users/query` 是纯 console 库读、可对 7070 完整校准（interop/viewer 真实数据），补齐它。

## What Changes
- 用户模糊查询（对齐 `UserFuzSerView.get`）：`GET /console/users/query?query_key=` → `query_key` 非空时按 `nick_name`/`email` 不区分大小写包含匹配（全局，无企业作用域），按 user_id 升序，每项 `{nick_name, email, user_id}`，`msg_show=查询用户成功`；`query_key` 为空 → 空 list、`msg_show=你没有查询任何用户`。

## Capabilities
### New Capabilities
- `user-query`: 用户模糊查询（纯 console 库读）。

## Impact
- 代码：`modules/account` 新增 UserSearchService + controller；复用 UserInfoRepository。
- 数据：只读 user_info；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录）。
