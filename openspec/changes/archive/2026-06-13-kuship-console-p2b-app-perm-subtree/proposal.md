## Why

P1-b/d 实现权限树时，因 kuship 当时无应用域，把 `team_app_manage` 的 app 子模型恒置空（明确 defer）。P2-a 引入了 `ServiceGroup`（应用）实体。现在可回填这块 defer：让权限树里 `team_app_manage` 按团队实际应用（ServiceGroup）展开 `app_<id>` 子模型，与 7070 对齐。

## What Changes

- `PermsCatalog` 新增装配能力：`packAppBody(codes, isOwner)`（单应用布尔权限树 body）、`applyAppManage(tree, appIds, appPermsByApp, isOwner)`（按团队应用重建已打包团队树的 `team_app_manage`），`getPermsStructure(appIds)` 改为按应用展开（结构形态）。
- 回填三处权限树的 `team_app_manage` app 子模型（对齐 rainbond 同源函数）：
  - `GET /console/perms`（`get_perms_structure`）：每个 `app_<id>` → team_app_manage 模板结构（name/desc/code）；
  - `GET /console/teams/{t}/roles/{id}/perms`（`get_role_perms`）：每个 `app_<id>` → 该角色 app 级 `role_perms` 装配的布尔树，无则用默认子树；
  - `GET /console/teams/{t}/users/{uid}/perms` 与 `users/details.tenant_actions`（`get_roles_union_perms`）：owner 全 true；成员按其角色 app 级码装配。
- 行为修正：`team_app_manage` 从"恒空"改为"按团队应用展开"。团队无应用时仍为空（`{sub_models:[], perms:{}}` / 结构形态 `perms:[]`），与之前一致；有应用时出现 `app_<id>` 子模型。
- **不变**：`GET /console/teams/{t}/roles/perms`（列表，`get_roles_perms`）本就保留模型 7 个 app 子模型、不做 app 替换——不改。

## Capabilities

### New Capabilities
- `app-perm-subtree`: 权限树 `team_app_manage` 按团队应用（ServiceGroup）展开 `app_<id>` 子模型的统一规则，覆盖权限元数据树、单角色权限树、成员/tenant_actions 权限树。

## Impact

- 代码：`PermsCatalog` 加 packAppBody/applyAppManage、getPermsStructure(appIds)；`RbacReadService.getUserTeamActions` 与 `TeamRoleWriteService.getRolePerms` 注入 `ServiceGroupRepository` 并按应用重建；`PermsInfoController` 传 appIds。`ServiceGroupRepository` 加 `findByTenantId`。
- 数据：只读 `service_group`（取团队应用 ID）；无 schema 变更。
- 接口：3 处权限树 team_app_manage 内容变化（无应用时不变）；对 7070 deep-diff 校准（DB 直插应用行验非空）。
- 不变：应用列表读、角色列表权限树、其它接口不变。
