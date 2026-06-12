# tenant-context Specification

## Purpose

TBD - created by archiving change kuship-console-p0-contract-auth. Update Purpose after archive.

## Requirements

### Requirement: 多租户请求上下文注入

系统 SHALL 提供请求级（`@RequestScope`）`RequestContext`，在鉴权通过后由认证过滤器写入真实加载的当前用户，并由拦截器从 URL 路径变量 `{team_name}`/`{region_name}` 注入 `teamName`/`regionName`。上下文 MUST 在同一请求内对 service 层可见，请求结束即销毁。

#### Scenario: 注入当前用户

- **WHEN** 携带有效 token 的请求通过认证
- **THEN** `RequestContext` 中可取到从 `user_info` 真实加载的当前用户

#### Scenario: 从路径变量注入团队与区域

- **WHEN** 请求路径含 `{team_name}` 与 `{region_name}`
- **THEN** `RequestContext.teamName` / `regionName` 被赋为对应路径值

#### Scenario: 上下文请求隔离

- **WHEN** 两个并发请求携带不同的 team/region/user
- **THEN** 各自 `RequestContext` 互不串扰，请求结束后销毁
