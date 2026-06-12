# rbac-read Specification

## Purpose
TBD - created by archiving change kuship-console-p1b-rbac-read. Update Purpose after archive.
## Requirements
### Requirement: 权限码体系与权限树装配

系统 SHALL 提供与 rainbond-console `console/utils/perms.py` 一致的权限码体系：分段权限码常量（企业 `1xxxxx`、团队 `2xxxxx`、应用 `3xxxxx`、组件及网关/证书等更细分段）、企业角色→权限映射（`ENTERPRISE`、`common_perms`）、团队与应用权限树定义（`TEAM`、`APP`），以及权限树装配能力（对齐 `get_perms`/`assemble_perms`/`get_team_perms_model`/`get_app_perms_model`/`pack_role_perms_tree`）。装配产物的结构（kind 名、`sub_models` 嵌套层级、`perms` 叶子的 `名称:布尔` 与 `code`）SHALL 与 7070 一致。

#### Scenario: 权限码无重复

- **WHEN** 加载权限码体系（团队 + 企业全部权限）
- **THEN** 不存在重复的权限名称或重复的权限编码（对齐 rainbond `check_perms_metadata` 的校验）

#### Scenario: 团队权限树模型结构对齐

- **WHEN** 构造团队权限树模型（`get_team_perms_model` 等价物）
- **THEN** 产出以 `team` 为根、含 `sub_models` 嵌套与每节点 `perms`（`{名称:false, code}`）的树，结构与 7070 的团队权限树一致

### Requirement: 用户企业角色与企业权限解析

系统 SHALL 提供解析"某用户在某企业下的角色名列表"的能力 `list_roles(enterprise_id, user_id)`：取自 `enterprise_user_perm.identity` 以逗号分隔的角色名（用户无记录时返回空列表）。系统 SHALL 在此基础上提供 `list_enterprise_perms_by_roles(roles)`，将角色名展开为企业权限标识集合（形如 `group.name` 字符串），规则与 rainbond `perms.py` 同名函数一致（含 `admin` 角色展开全部企业子权限、并叠加 `app_store.<common_perms>`）。

#### Scenario: 企业管理员的权限集合

- **WHEN** 对企业角色含 `admin` 的用户调用 `list_enterprise_perms_by_roles`
- **THEN** 返回展开后的企业权限标识集合，与 7070 同用户 `users/details.permissions` 一致

#### Scenario: 无企业角色记录

- **WHEN** 用户在该企业无 `enterprise_user_perm` 记录
- **THEN** `list_roles` 返回空列表，`permissions` 为空集合（仍叠加 `common_perms` 派生项，按 7070 实测为准）

### Requirement: 用户团队角色解析

系统 SHALL 提供解析"某用户在某团队下的角色列表"的能力（对齐 `user_kind_role_service.get_user_roles(kind="team", kind_id=tenant_id, user)`）：基于 `role_info`（按 `kind`/`kind_id` 过滤出该团队角色）与 `user_role`（按 `user_id` 与上述角色交集）关联，产出该用户在团队下的角色项列表，每项含 `role_id` 与 `role_name`。该列表用于 `users/details` 中每团队的 `role_name_list`。

#### Scenario: 团队成员的角色列表

- **WHEN** 解析某用户在某团队（`kind="team"`，`kind_id=tenant_id`）下的角色
- **THEN** 返回该用户被分配的角色项列表（每项 `{role_id, role_name}`），与 7070 同团队 `role_name_list` 一致

#### Scenario: 非该团队成员

- **WHEN** 用户在该团队没有任何 `user_role` 记录
- **THEN** 返回空角色列表

### Requirement: 用户团队权限树解析

系统 SHALL 提供解析"某用户在某团队下的权限树"的能力（对齐 `user_kind_perm_service.get_user_perms(kind="team", kind_id=tenant_id, user, is_owner, is_ent_admin)`）：取用户在该团队的角色对应的 `role_perms` 权限码并集，按团队权限树模型装配为 `tenant_actions` 权限树（全局权限 `app_id=-1` 落在团队节点，应用级权限按 `app_id` 落在 `team_app_manage` 下对应应用子模型）。当用户为团队创建者（`is_owner`）或企业管理员（`is_ent_admin`）时 SHALL 短路为全权限（权限树所有叶子为 `true`）。

#### Scenario: 团队 owner 全权限短路

- **WHEN** 用户是团队创建者，解析其团队权限树
- **THEN** 返回的 `tenant_actions` 权限树所有叶子为 `true`，与 7070 同情形一致

#### Scenario: 企业管理员全权限短路

- **WHEN** 用户是企业管理员（非创建者）解析其团队权限树
- **THEN** 同样短路为全 `true` 权限树

#### Scenario: 普通成员按角色权限码装配

- **WHEN** 用户为普通团队成员，解析其团队权限树
- **THEN** 仅其角色 `role_perms` 并集对应的叶子为 `true`，其余为 `false`；结构（含各应用子模型）与 7070 同用户 `tenant_actions` 一致

### Requirement: RBAC 关系实体只读访问

系统 SHALL 以只读方式访问共享 console 库的 RBAC 关系表 `role_info`、`user_role`、`role_perms`（rainbond 既有表，本轮不做 schema 变更、不提供写入）。实体字段与库表一致：`role_info(name, kind, kind_id)`、`user_role(user_id, role_id)`、`role_perms(role_id, perm_code, app_id 默认 -1)`。

#### Scenario: 仅读不写

- **WHEN** 本轮任何 RBAC 解析路径访问上述表
- **THEN** 只执行查询，不产生任何插入/更新/删除（写接口留 P1-c）

