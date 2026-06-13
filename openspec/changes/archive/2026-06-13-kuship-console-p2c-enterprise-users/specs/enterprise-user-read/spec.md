## ADDED Requirements

### Requirement: 企业用户列表

系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/users`（对齐 `EnterPriseUsersCLView.get`），返回企业用户分页列表：`user_info` 按 `enterprise_id` 过滤、按 `user_id` 升序；`query` 非空时模糊匹配 `nick_name`/`real_name`/`phone`/`email`；分页（`page` 默认 1、`page_size` 默认 10），`data` 顶层含 `page`/`page_size`/`total`。每项含 `email`、`nick_name`、`real_name`（为 null 时取 `nick_name`）、`user_id`、`phone`、`create_time`（ISO 序列化）、`default_favorite_name`、`default_favorite_url`（无收藏域时为 null）。

#### Scenario: 返回企业用户分页

- **WHEN** 已认证用户请求 `GET /console/enterprise/{enterprise_id}/users`
- **THEN** 返回 `code=200`，`data.list` 为企业用户项、顶层含 `page`/`page_size`/`total`，与 7070 一致

#### Scenario: query 过滤

- **WHEN** 带 `query` 请求
- **THEN** 仅返回 `nick_name`/`real_name`/`phone`/`email` 匹配的用户

#### Scenario: real_name 回退

- **WHEN** 某用户 `real_name` 为 null
- **THEN** 该项 `real_name` 取其 `nick_name`
