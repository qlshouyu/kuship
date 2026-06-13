## ADDED Requirements

### Requirement: 用户模糊查询
系统 SHALL 提供 `GET /console/users/query?query_key=`（对齐 `UserFuzSerView.get`）。`query_key` 非空时，返回 `nick_name` 或 `email` 不区分大小写包含 `query_key` 的用户（全局、按 user_id 升序），每项 `{nick_name, email, user_id}`，`msg_show=查询用户成功`。`query_key` 为空时返回空 list、`msg_show=你没有查询任何用户`。

#### Scenario: 命中查询
- **WHEN** 带非空 `query_key` 且有匹配用户
- **THEN** 返回 `code=200`、`msg_show=查询用户成功`，`data.list` 为匹配用户项，与 7070 一致

#### Scenario: 空 query_key
- **WHEN** 不带或空 `query_key`
- **THEN** 返回 `code=200`、`msg_show=你没有查询任何用户`、`data.list=[]`
