# account-profile Specification

## Purpose

TBD - created by archiving change kuship-console-p1a-console-entry. Update Purpose after archive.

## Requirements

### Requirement: 当前登录用户详情

系统 SHALL 提供 `GET /console/users/details`，对已认证请求返回当前登录用户（取自 P0 认证后注入的 RequestContext.currentUser）的详情信封。bean 字段集以 rainbond-console(7070) 实测为准。

#### Scenario: 已登录返回当前用户详情

- **WHEN** 携带有效 token 请求 `GET /console/users/details`
- **THEN** 返回 `code=200` 信封，`data.bean` 含当前用户标识（至少 `user_id`、`nick_name`/`username`、`email`、`enterprise_id`），与 7070 同接口字段一致

#### Scenario: 未认证被拒

- **WHEN** 不带 token 请求 `GET /console/users/details`
- **THEN** 返回 401（沿用 P0 鉴权），不泄漏用户信息
