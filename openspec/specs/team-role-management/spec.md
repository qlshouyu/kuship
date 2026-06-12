# team-role-management Specification

## Purpose
TBD - created by archiving change kuship-console-p1d-team-role-write. Update Purpose after archive.
## Requirements
### Requirement: 权限元数据树

系统 SHALL 提供 `GET /console/perms`（对齐 rainbond `PermsInfoLView`，免鉴权），返回权限元数据树（对齐 `get_perms_structure`）：以团队/企业为根、含 `sub_models` 嵌套，每个权限叶子带 `name`/`desc`/`code`。应用相关子模型按团队应用列表展开——kuship 无应用域时为空。

#### Scenario: 返回权限元数据树

- **WHEN** 请求 `GET /console/perms`
- **THEN** 返回 `code=200`，`data.bean` 为权限元数据树，结构与 7070 一致

### Requirement: 团队角色列表与创建

系统 SHALL 提供 `GET /console/teams/{team_name}/roles` 返回该团队角色（`kind=team` 且 `kind_id=tenant_id`）并入系统默认角色（`kind_id="default"`），每项含 `name` 与 `ID`。系统 SHALL 提供 `POST` 同路径创建团队角色：`name` 非空且在"团队角色 ∪ 默认角色"范围内唯一，否则报错；新角色 `kind=team`、`kind_id=tenant_id`。

#### Scenario: 列出团队角色

- **WHEN** 请求 `GET /console/teams/{team_name}/roles`
- **THEN** 返回该团队角色与默认角色的列表（每项 `name`/`ID`），与 7070 一致

#### Scenario: 创建角色

- **WHEN** `POST` 携带未占用的 `name`
- **THEN** 创建成功，返回 `msg_show=创建角色成功` 与新角色 bean

#### Scenario: 角色名冲突或空

- **WHEN** `name` 为空或与已有（含默认）角色重名
- **THEN** 返回错误（`角色名称不能为空` / `角色名称已存在`），不创建

### Requirement: 团队角色详情、改名与删除

系统 SHALL 提供 `GET/PUT/DELETE /console/teams/{team_name}/roles/{role_id}`：

- `GET`：返回角色详情（含默认角色），bean 去除 `kind`/`kind_id`；
- `PUT`：改名，名称非空且唯一（与他角色重名报错；与自身同名幂等返回）；仅团队自有角色可改，默认角色不可改；
- `DELETE`：删除团队自有角色，事务内连带删除该角色的 `role_perms` 关联与 `user_role` 成员关联；默认角色（`kind_id="default"`）或不存在的角色 SHALL 拒绝（`角色不存在或为默认角色`）。

#### Scenario: 查看角色详情

- **WHEN** `GET .../roles/{role_id}`
- **THEN** 返回角色 bean（不含 `kind`/`kind_id`），与 7070 一致

#### Scenario: 改名成功

- **WHEN** `PUT` 携带未占用的新 `name`
- **THEN** 角色改名成功，返回更新后 bean

#### Scenario: 删除角色连带清理

- **WHEN** `DELETE` 一个团队自有角色
- **THEN** 该角色及其 `role_perms`、`user_role` 关联被事务性删除，返回 `删除角色成功`

#### Scenario: 默认角色不可删

- **WHEN** `DELETE` 一个默认角色（`kind_id="default"`）或不存在的角色
- **THEN** 拒绝并返回 `角色不存在或为默认角色`

### Requirement: 角色权限树读取

系统 SHALL 提供 `GET /console/teams/{team_name}/roles/perms` 返回全部团队角色（含默认）各自的权限树（对齐 `get_roles_perms`，每项 `role_id` + `permissions` 布尔权限树）。系统 SHALL 提供 `GET /console/teams/{team_name}/roles/{role_id}/perms` 返回单个角色的权限树（对齐 `get_role_perms`，含按团队应用展开的应用子模型——无应用域则空）。布尔叶子按该角色 `role_perms` 命中置真。

#### Scenario: 列出所有角色权限树

- **WHEN** `GET .../roles/perms`
- **THEN** 返回各角色 `{role_id, permissions:树}` 列表，叶子真值与该角色 `role_perms` 一致

#### Scenario: 读取单角色权限树

- **WHEN** `GET .../roles/{role_id}/perms`
- **THEN** 返回该角色权限树 bean，与 7070 一致

### Requirement: 角色权限树更新

系统 SHALL 提供 `PUT /console/teams/{team_name}/roles/{role_id}/perms`：接收提交的权限树 `permissions`，事务内先删除该角色现有 `role_perms`，再将权限树降维（对齐 `unpack_role_perms_tree`：遍历树，对每个为 `true` 的叶子按"分组名_权限名 → 整型码"映射，写入 `role_perms(role_id, perm_code, app_id)`）。名↔码映射 SHALL 用硬编码 `get_perms_name_code_kv()`（不依赖 `perms_info` 表）。更新后 SHALL 返回该角色最新权限树。

#### Scenario: 提交权限树落库

- **WHEN** `PUT` 携带勾选了若干权限的 `permissions` 树
- **THEN** 该角色 `role_perms` 被替换为树中所有 `true` 叶子对应的码，返回更新后的权限树

#### Scenario: 取消权限

- **WHEN** 提交的树中某些原为 `true` 的叶子改为 `false`
- **THEN** 这些码从该角色 `role_perms` 移除（旧关联整体重建）

### Requirement: 角色写接口鉴权

团队角色相关接口（`teams/{team_name}/roles` 及子路径）SHALL 受强制鉴权保护，码对齐 `TEAM_ROLE_PERMS`：`get=630001`、`post=630002`、`put=630003`、`delete=630004`。`GET /console/perms` SHALL 免鉴权（对齐 `PermsInfoLView` 的 AllowAny）。

#### Scenario: 无角色管理权限被拒

- **WHEN** 不具备相应 `TEAM_ROLE_PERMS` 码的用户访问团队角色写接口
- **THEN** 返回 HTTP 403、信封 `code=10402`（沿用 P1-c 无权契约）

#### Scenario: 权限元数据免鉴权

- **WHEN** 任意已认证用户请求 `GET /console/perms`
- **THEN** 正常返回，不因团队权限被拒

