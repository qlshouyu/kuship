## Context

P2-a 引入 ServiceGroup。本轮回填 P1-b/d 明确 defer 的 `team_app_manage` app 子树。rainbond 三处同源逻辑：
- `get_perms_structure(tenant_id)`：app_perms={"app_<id>": team_app_manage 模板}；team_app_manage = get_structure(app_perms,"app").app（结构形态 name/desc/code）。
- `get_role_perms`：每 app_<id> → 该角色 app 级码 pack(get_app_perms_model) 或默认 team_app_manage 子树（布尔）。
- `get_roles_union_perms`（tenant_actions/成员 perms）：同上，owner 全 true。
- `get_roles_perms`（角色列表）：**不做 app 替换**，保留模型 7 子树——不改。

均按 `ServiceGroup.objects.filter(tenant_id=...)`（不分 region）取 app ID。

## Goals / Non-Goals

**Goals:** PermsCatalog 加 packAppBody/applyAppManage、getPermsStructure(appIds)；3 处按应用重建；对 7070 校准（DB 直插应用验非空）。
**Non-Goals:** 应用创建/运行态（region）；角色列表权限树不改。

## Decisions

### 决策 1：装配逻辑集中在 PermsCatalog
- `packAppBody(codes,isOwner)` = pack(app() 模板).app body。`applyAppManage(tree, appIds, appPermsByApp, isOwner)` 就地替换打包团队树的 team_app_manage：每 app 有码 packAppBody 否则复用默认子树；appIds 空 → {sub_models:[],perms:{}}（替代旧 applyEmptyAppManage）。`getPermsStructure(appIds)` 结构形态展开。

### 决策 2：appIds 来源 ServiceGroupRepository.findByTenantId（不分 region）
- 对齐 rainbond 的 ServiceGroup.filter(tenant_id)。RbacReadService/TeamRoleWriteService 注入 ServiceGroupRepository；PermsInfoController 用 tenant_id 参数查。

### 决策 3：成员/角色的 app 级码
- getRolePerms：角色 role_perms 按 app_id 分组（app_id != -1）。getUserTeamActions：成员所有角色 role_perms 收集，全局码→树根、app 级码→分组；owner 短路全 true（appPermsByApp 空，默认子树在 owner pack 下全 true）。

### 决策 4：无应用回归
- appIds 空时 applyAppManage 产出 {sub_models:[],perms:{}}、getPermsStructure 产出 {sub_models:[],perms:[]}，与 P1-b/d 既有零差异行为完全一致（既有校准不回归）。

## Risks / Trade-offs

- **[改动已归档行为]** 三处权限树装配 → 缓解：无应用回归路径保持不变（既有单测/校准仍绿）；有应用路径 DB 直插应用行对 7070 deep-diff 校准。
- **[结构 vs 布尔两形态]** /console/perms 用结构(name/desc/code, perms list)、role/member 用布尔(perms {})——分别用 getStructure / packAppBody，校准覆盖三端。
- **[默认子树引用共享]** 多 app 复用同一默认 body 引用（rainbond 同样 reuse removed_value）——只读序列化无碍。

## Migration Plan

无 schema 变更。回滚还原三处为恒空 team_app_manage。联调：app_2 已在 default（测试残留，正好用于非空校准）→ 三端 deep-diff → 测毕删 app_2。

## Open Questions

- 无（三处形态已抓 7070 目标：team_app_manage 含 1 个 app_<id>{sub_models:[7],perms:[]}，/console/perms 与 role/member 的 perms 容器分别为 list/{}）。
