# user-custom-configs-read Specification

## Purpose
TBD - created by archiving change kuship-console-p2k-user-custom-configs. Update Purpose after archive.
## Requirements
### Requirement: 用户自定义配置列表
系统 SHALL 提供 `GET /console/users/custom_configs`（对齐 `CustomConfigsUserCLView.get`），返回当前登录用户（按 `user_nick_name`）的 `console_config` 行，每项含全列 `{ID, key, value, description, update_time, user_nick_name}`（`update_time` ISO，可空），`msg_show=操作成功`。

#### Scenario: 返回自定义配置列表
- **WHEN** 已认证用户请求 `GET /console/users/custom_configs`
- **THEN** 返回 `code=200`、`msg_show=操作成功`，`data.list` 为该用户配置项（无则空），与 7070 一致

