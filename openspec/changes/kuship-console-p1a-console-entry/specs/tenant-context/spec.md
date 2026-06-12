## ADDED Requirements

### Requirement: 企业上下文解析

系统 SHALL 在已认证请求中解析企业上下文：以当前用户的 `enterprise_id` 解析 `tenant_enterprise` 实体并注入 RequestContext。enterprise 作用域路由的 `{enterprise_id}` MUST 与当前用户企业一致，否则按 rainbond-console 行为拒绝。

#### Scenario: 注入当前用户企业

- **WHEN** 已认证请求进入 enterprise 作用域端点
- **THEN** RequestContext 中可取到当前用户 `enterprise_id` 对应的企业实体

#### Scenario: 越权企业被拒

- **WHEN** 路由 `{enterprise_id}` 与当前用户企业不一致
- **THEN** 请求被拒绝（与 7070 一致的状态码/文案）

### Requirement: 团队上下文解析

系统 SHALL 对 team 作用域端点按 `{team_name}` + 当前用户 `enterprise_id` 解析 `tenant_info` 团队实体并注入 RequestContext.team；解析不到 MUST 返回 `msg="team not found"` / `msg_show="团队不存在"`，与 rainbond-console `TenantHeaderView` 一致。本轮仅校验"团队属于当前用户企业"，不做逐用户成员/权限码强校验（留 P1-b）。

#### Scenario: 解析团队注入上下文

- **WHEN** team 作用域端点收到当前用户企业下存在的 `{team_name}`
- **THEN** RequestContext.team 被赋为对应 `tenant_info` 实体（含 tenant_id/tenant_name/enterprise_id）

#### Scenario: 团队不存在统一错误

- **WHEN** `{team_name}` 在当前用户企业下不存在
- **THEN** 返回 `msg="team not found"` / `msg_show="团队不存在"` 信封
