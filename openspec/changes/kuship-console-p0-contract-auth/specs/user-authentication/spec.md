## ADDED Requirements

### Requirement: 用户登录签发 token

系统 SHALL 提供 `POST /console/users/login`，接受 form 参数 `nick_name` 与 `password`，校验 `user_info` 表中对应用户与密码，成功后签发 HS256 JWT 并在 `data.bean.token` 返回。路径与出参 MUST 与 rainbond-console 一致。

#### Scenario: 登录成功返回 token

- **WHEN** 以正确的 `nick_name` + `password` POST `/console/users/login`
- **THEN** 返回信封 `data.bean.token` 为可被 kuship-console 与 rainbond-console 双向互认的 JWT

#### Scenario: 密码错误登录失败

- **WHEN** 以存在的 `nick_name` 但错误 `password` 登录
- **THEN** 返回鉴权失败信封，不签发 token

#### Scenario: 用户不存在登录失败

- **WHEN** 以 `user_info` 中不存在的 `nick_name` 登录
- **THEN** 返回鉴权失败信封，不签发 token

### Requirement: 用户登出

系统 SHALL 提供登出接口，把当前请求 token 写入 Redis 会话黑名单使其立即失效。

#### Scenario: 登出后 token 失效

- **WHEN** 已登录用户调用登出接口，随后再用同一 token 访问受保护端点
- **THEN** 该 token 被黑名单拦截，返回 401
