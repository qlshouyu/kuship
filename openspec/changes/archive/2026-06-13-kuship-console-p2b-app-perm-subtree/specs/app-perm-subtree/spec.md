## ADDED Requirements

### Requirement: 权限树按团队应用展开 team_app_manage

系统的权限树（`team_app_manage` 节点）SHALL 按团队实际应用（`service_group` 按 `tenant_id` 过滤所得 app ID 集合）展开 `app_<id>` 子模型，对齐 rainbond `get_perms_structure` / `get_role_perms` / `get_roles_union_perms`。团队无应用时 `team_app_manage` SHALL 为空（布尔树 `{sub_models:[], perms:{}}`；结构树 `{sub_models:[], perms:[]}`），与引入应用域前一致。

#### Scenario: 无应用时为空

- **WHEN** 团队无任何 `service_group` 应用
- **THEN** 三处权限树（`/console/perms`、`roles/{id}/perms`、`users/{uid}/perms`）的 `team_app_manage` 均为空，与之前及 7070 一致

#### Scenario: 有应用时展开 app 子模型

- **WHEN** 团队有 N 个应用
- **THEN** `team_app_manage.sub_models` 含 N 个 `app_<id>` 子模型，结构/布尔形态与 7070 一致

### Requirement: 权限元数据树的应用子模型

`GET /console/perms?tenant_id=<tid>` 的 `team_app_manage` SHALL 对该租户每个应用展开 `app_<id>`，其子模型为 team_app_manage 模板的结构形态（`{name, desc, code}` 叶子），对齐 `get_perms_structure`。

#### Scenario: 含应用的权限元数据

- **WHEN** 租户有应用且请求 `GET /console/perms?tenant_id=<tid>`
- **THEN** `team_app_manage.sub_models` 每项为 `{app_<id>: {sub_models:[7 个应用子树结构], perms:[]}}`，与 7070 一致

### Requirement: 角色与成员权限树的应用子模型

`GET /console/teams/{t}/roles/{id}/perms`（单角色）与 `GET /console/teams/{t}/users/{uid}/perms`（成员）及 `users/details.tenant_actions` 的 `team_app_manage` SHALL 对团队每个应用展开 `app_<id>`：该应用有 app 级 `role_perms` 码则按 APP 模型装配布尔树，否则用默认 team_app_manage 子树；owner/企业管理员全 true。

#### Scenario: 单角色应用子树

- **WHEN** 请求某角色的 `roles/{id}/perms`，团队有应用
- **THEN** `team_app_manage.sub_models` 每项 `{app_<id>: 布尔树}`，叶子真值按该角色对该应用的 `role_perms`（无则默认子树）与 7070 一致

#### Scenario: owner 成员应用子树全 true

- **WHEN** owner/企业管理员请求成员权限树或 tenant_actions
- **THEN** 各 `app_<id>` 子模型叶子全 true，与 7070 一致
