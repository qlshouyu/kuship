## 1. PermsCatalog 装配

- [x] 1.1 `packAppBody(codes, isOwner)`、`applyAppManage(tree, appIds, appPermsByApp, isOwner)`、`getPermsStructure(appIds)` 按应用展开
- [x] 1.2 `ServiceGroupRepository.findByTenantId`

## 2. 三处回填

- [x] 2.1 `PermsInfoController` 传 appIds（tenant_id 查 service_group）
- [x] 2.2 `RbacReadService.getUserTeamActions` 注入 ServiceGroupRepository，按 appIds + 成员 app 级码 applyAppManage
- [x] 2.3 `TeamRoleWriteService.getRolePerms` 注入 ServiceGroupRepository，按 appIds + 角色 app 级码 applyAppManage
- [x] 2.4 移除旧 applyEmptyAppManage，更新受影响单测构造/调用

## 3. 实跑校准

- [x] 3.1 无应用回归：default（删 app_2 前先确认）三端 team_app_manage 仍空、与 7070 一致
- [x] 3.2 有应用：app_2 在场，三端（/console/perms、roles/1/perms、users/700002/perms）deep-diff 8000 vs 7070 一致
- [x] 3.3 全量构建 + 单测通过
- [x] 3.4 清理 app_2，确认 default 应用数=0
- [x] 3.5 openspec 校验，准备归档
