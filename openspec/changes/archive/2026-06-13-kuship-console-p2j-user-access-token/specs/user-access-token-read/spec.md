## ADDED Requirements

### Requirement: 用户访问令牌列表
系统 SHALL 提供 `GET /console/users/access-token`（对齐 `UserAccessTokenCLView.get`），返回当前登录用户的访问令牌（`user_access_key` 按 user_id），每项 `{note, expire_time, user_id, ID}`（`expire_time` 为整型，可空）。

#### Scenario: 返回令牌列表
- **WHEN** 已认证用户请求 `GET /console/users/access-token`
- **THEN** 返回 `code=200`，`data.list` 为该用户令牌项（无则空），与 7070 一致
