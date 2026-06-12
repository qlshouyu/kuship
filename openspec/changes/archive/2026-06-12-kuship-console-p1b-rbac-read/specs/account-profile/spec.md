## MODIFIED Requirements

### Requirement: 当前登录用户详情

系统 SHALL 提供 `GET /console/users/details`，对已认证请求返回当前登录用户（取自 P0 认证后注入的 RequestContext.currentUser）的详情信封。bean 字段集以 rainbond-console(7070) 实测为准。其中 RBAC 派生字段 SHALL 返回真实值（不再为 P1-a 的空占位）：企业 `permissions` 取自 `list_enterprise_perms_by_roles(roles)`；每个团队的 `role_name_list` 取自该用户在该团队的角色解析；每个团队的 `tenant_actions` 取自该用户在该团队的权限树解析（团队创建者或企业管理员短路为全权限）。这些字段 SHALL 与 7070 同接口逐叶子一致。

#### Scenario: 已登录返回当前用户详情

- **WHEN** 携带有效 token 请求 `GET /console/users/details`
- **THEN** 返回 `code=200` 信封，`data.bean` 含当前用户标识（至少 `user_id`、`nick_name`/`username`、`email`、`enterprise_id`），与 7070 同接口字段一致

#### Scenario: 企业权限码非空

- **WHEN** 已认证用户在其企业拥有角色（如 `admin`）
- **THEN** `data.bean.permissions` 为该用户角色展开后的企业权限标识集合，与 7070 一致（不再为空数组）

#### Scenario: 每团队 RBAC 字段填充

- **WHEN** 返回的 `data.bean.teams` 含某团队
- **THEN** 该团队对象的 `role_name_list` 为该用户在该团队的角色项列表、`tenant_actions` 为该用户在该团队的权限树；团队创建者或企业管理员的 `tenant_actions` 全 `true`；均与 7070 同团队一致（不再为空占位）

#### Scenario: 未认证被拒

- **WHEN** 不带 token 请求 `GET /console/users/details`
- **THEN** 返回 401（沿用 P0 鉴权），不泄漏用户信息
