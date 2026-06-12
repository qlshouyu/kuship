## Context

P1-b：RBAC 只读实体 + `RbacReadService`（含 `getUserTeamRoles`、`getUserTeamActions`）。P1-c：`@RequiresPerms`/`CheckPermsInterceptor`。P1-d：`role_info`/`role_perms`/`user_role` 仓储写 + 角色 CRUD。本轮做成员角色分配（写 `user_role`）+ 成员/角色读 + 成员权限树视图。

rainbond 对应（`console/views/perms.py` + `team.py` + `perm_repo.py`）：
- `TeamUserView.get`（`teams/{t}/users`）：`get_team_users`（`perm_rel_tenant` 按团队 PK → `user_info`）分页(8)，每项含 `role_info`（**rainbond 现状取 self.user 的角色**，逐行相同）。
- `TeamUsersRolesLView.get`（`users/roles`）：`get_users_roles`，每成员 `{nick_name,email,user_id,roles}`，创建者额外 `{role_id:0,role_name:"拥有者"}`。
- `TeamUserRolesRUDView`（`users/{uid}/roles`）：GET `get_user_roles`；PUT `update_user_roles`（删该用户团队角色 + 按 `roleIds ∩ 团队角色` 批量写，全不可分配且非空→报错）；DELETE `delete_user_roles`（清空）。
- `TeamUserPermsLView.get`（`users/{uid}/perms`）：`get_user_perms(user=target, is_owner=requester.is_team_owner, is_ent_admin=requester.is_enterprise_admin)`。

## Goals / Non-Goals

**Goals:** 成员列表/角色读、成员角色写（user_role 重建）、成员权限树视图；6 接口标 `@RequiresPerms(TEAM, TEAM_MEMBER_PERMS)`；对 7070 校准。
**Non-Goals:** `notjoinusers`、成员加入/移除团队（`perm_rel_tenant` 写、`UserDelView`）、`pemtransfer`、团队生命周期（P1-f）。

## Decisions

### 决策 1：只写 user_role，不动 perm_rel_tenant
- 本轮"成员角色"= `user_role`（用户↔角色）。团队成员关系（`perm_rel_tenant`）的增删属"成员加入/移除"，留 P1-f。`update_user_roles` 仅重建 `user_role`。

### 决策 2：updateUserRoles 事务重建，交集校验
- `@Transactional`：先删该用户在本团队角色的 `user_role`（`role_id ∈ 团队角色ID` 且 `user_id=target`），再按 `roleIds ∩ 团队角色ID` 批量写。`roleIds` 非空但交集空 → `ServiceHandleException(404,"传入角色不可被分配，请检查参数")`（对齐 `update_user_roles`）。`user_role.user_id`/`role_id` 均为字符串。

### 决策 3：忠实复刻 rainbond 现状细节
- `TeamUserView` 的 `role_info` 取**当前请求用户**的角色（rainbond 现状，逐行相同）——照搬，避免引入与 7070 的差异。
- `users/roles` 创建者额外 `{role_id:0(int), role_name:"拥有者"}`；普通角色 `role_id` 为字符串（取自 `user_role.role_id`）。混合类型照搬。
- `users/{uid}/perms` 用**请求者**的 is_owner/is_ent_admin（rainbond 现状）：复用 `RbacReadService.getUserTeamActions(tenantId, targetUserId, requesterIsOwner, requesterIsEntAdmin)`，外层包 `{user_id:targetUserId, permissions:tree}`。requesterIsOwner=团队 creater==当前用户；requesterIsEntAdmin=当前用户存在 enterprise_user_perm 行（对齐 TenantHeaderView 宽松判定）。

### 决策 4：成员查询复用 PermRelTenant
- `getTeamUsers`：`permRelTenantRepository.findByTenantId(team.getId())` → user_ids → `userInfoRepository.findByUserIdIn`。分页(8)在服务层切片，`total` 落 `data.total`。

### 决策 5：响应对 7070 实测校准
- bean/list 包裹、msg/msg_show、role_id 类型（string vs int 0）、分页 total 位置等以 7070 抓存为准（已存 docs/p1e-7070-reference.md）。

## Risks / Trade-offs

- **[role_id 混合类型]** roles 普通项 string、拥有者 0 为 int → 照搬，deep-diff 验证。
- **[perms 视图请求者标志]** 用请求者 is_owner/is_ent_admin（非目标用户）→ 忠实 rainbond；interop(owner) 请求任意成员 perms 均全 true，与 7070 一致。
- **[首写 user_role]** 误写污染 → 用 viewer/可丢弃成员校准；事务保证一致；不动 interop 的角色 1。
- **[成员关系只读]** 本轮不建成员关系，故只能对已是成员的用户测角色分配；viewer 非 default 成员 → 其角色分配测试需先确认其在 perm_rel_tenant（否则 getTeamUsers 不含它）。校准时按需用 DB 直插一条 perm_rel_tenant 让 viewer 成为成员（可丢弃）。

## Migration Plan

无 schema 变更；引入对 `user_role` 的写。回滚移除 controller/service 与标注。联调：7070 抓基线 → 实现 → 写后回读 diff；写用可丢弃成员/角色。

## Open Questions

- `users/{uid}/roles` 的 `user_id` 路径变量类型与 `get_team_users().filter(user_id=..)`——kuship 以 Integer 解析。目标用户非团队成员时 7070 行为（user=None→get_user_roles 抛"用户不存在"）以实测为准。
