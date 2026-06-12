## ADDED Requirements

### Requirement: JWT 验签兼容 djangorestframework-jwt

系统 SHALL 以 HS256 验签请求头 `Authorization` 中的 JWT，密钥 `JWT_SECRET_KEY` MUST 与 rainbond-console 同源。接受 `GRJWT`（主）与 `jwt`（兼容）前缀，前缀大小写不敏感。claims MUST 直接采用 Django 风格 `user_id/username/nick_name/email/exp/orig_iat`，不做名字转换。

#### Scenario: GRJWT 前缀验签通过

- **WHEN** 请求头为 `Authorization: GRJWT <有效token>`
- **THEN** 验签通过，进入业务处理

#### Scenario: 兼容 jwt 前缀且大小写不敏感

- **WHEN** 请求头前缀为 `jwt` 或 `JWT`（任意大小写）携带有效 token
- **THEN** 验签通过

#### Scenario: 密钥未配置则拒绝启动

- **WHEN** 在非 local profile 下启动且 `JWT_SECRET_KEY` 为空
- **THEN** 应用启动失败（fail-fast），不进入服务状态

#### Scenario: user_id 不存在返回 401

- **WHEN** token 验签通过但其 `user_id` 在 `user_info` 表中不存在
- **THEN** 返回 401，`msg` 为具体原因（如 `user not found`），`msg_show` 为统一中文文案

#### Scenario: 无 token 访问受保护端点

- **WHEN** 访问受保护端点但未携带 `Authorization`
- **THEN** 返回 401

### Requirement: Redis 会话黑名单

系统 SHALL 在每次请求验签通过后查询 Redis 会话黑名单，命中即拒绝。登出/强制下线 MUST 把该 token（或 `user_id`+`orig_iat`）写入黑名单，TTL 对齐 token 剩余 `exp`。

#### Scenario: 黑名单命中拒绝访问

- **WHEN** 携带一个验签有效但已被加入黑名单的 token
- **THEN** 返回 401，请求不进入业务处理

#### Scenario: 黑名单条目按 exp 过期

- **WHEN** 一个被拉黑 token 的原始 `exp` 到期
- **THEN** 其黑名单条目随 TTL 自动清除，不再占用 Redis
