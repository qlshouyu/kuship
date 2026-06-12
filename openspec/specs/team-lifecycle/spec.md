# team-lifecycle Specification

## Purpose
TBD - created by archiving change kuship-console-p1h-team-lifecycle. Update Purpose after archive.
## Requirements
### Requirement: 创建团队（无 region 绑定路径）

系统 SHALL 提供 `POST /console/teams/add-teams`（对齐 `AddTeamView` + `create_team`），创建团队。请求体 `team_alias`（必填）、`namespace`、`useable_regions`（本轮不绑定 region）、`logo`。

校验：

- `team_alias` 为空 SHALL 返回 400「团队名不能为空」；
- `namespace` 不符合 k8s 命名规范（小写字母/数字/`-`，字母开头、字母或数字结尾）SHALL 返回 400「命名空间只能由小写字母、数字或-组成，并且必须以字母开始、以数字或字母结尾」；
- `tenant_alias` 在当前企业已存在 SHALL 返回 400「该团队名已存在」。

校验通过后 SHALL 事务创建：`tenant_info`（随机 8 位 `tenant_name`、`creater`=当前用户、`tenant_alias`/`namespace`/`logo`、`limit_memory=0`）、`tenant_perms`（identity=owner）、3 个默认角色（管理员/开发者/观察者）及各自 `role_perms`（码取默认角色权限表）、并把"管理员"角色分配给创建者（`user_role`）。返回团队完整信息 bean（对齐 `tenant.to_dict()` 12 字段），`msg_show=团队添加成功`。

#### Scenario: 创建成功

- **WHEN** `POST /console/teams/add-teams` 携带非空且不重复的 `team_alias` 与合法 `namespace`
- **THEN** 创建团队及其 owner 关系、3 默认角色与创建者管理员角色，返回 `code=200`、`msg_show=团队添加成功` 与团队完整 bean

#### Scenario: 团队名为空被拒

- **WHEN** `team_alias` 为空
- **THEN** 返回 400「团队名不能为空」，不创建

#### Scenario: 团队名重复被拒

- **WHEN** `team_alias` 在当前企业已存在
- **THEN** 返回 400「该团队名已存在」，不创建

#### Scenario: 非法命名空间被拒

- **WHEN** `namespace` 不符合 k8s 命名规范
- **THEN** 返回 400「命名空间只能由小写字母、数字或-组成，并且必须以字母开始、以数字或字母结尾」，不创建

### Requirement: 删除团队（无 region 绑定路径）

系统 SHALL 提供 `DELETE /console/teams/{team_name}/delete`（对齐 `TeamDelView` + `delete_by_tenant_id`），删除团队：删除该团队的 `tenant_perms` 成员关系与 `tenant_info`（与 rainbond 一致，不级联删除 `role_info`/`user_role`）。团队不存在 SHALL 返回 404「{team}团队不存在」。返回 `msg_show=删除团队成功`。

#### Scenario: 删除成功

- **WHEN** `DELETE /console/teams/{team_name}/delete` 删除一个无 region 绑定的团队
- **THEN** 该团队 `tenant_perms` 与 `tenant_info` 被删除，返回 `code=200`、`msg_show=删除团队成功`

#### Scenario: 团队不存在

- **WHEN** `{team_name}` 不存在
- **THEN** 返回 404、`msg_show` 含「团队不存在」

