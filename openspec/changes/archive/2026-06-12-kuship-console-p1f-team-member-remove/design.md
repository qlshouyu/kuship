## Context

P1-e 落地成员列表/角色读 + 成员角色写（`user_role`）+ 成员权限树，但只读 `tenant_perms`。本轮补：未加入用户列表（读）+ 批量移除成员（首次写 `tenant_perms`）。

rainbond 对应：
- `NotJoinTeamUserView.get`（`teams/{t}/notjoinusers`）→ `get_not_join_users(enterprise, tenant, query)`：`user_info` 中 `enterprise_id=企业` 且 `user_id ∉ (该团队 tenant_perms)` 的用户，分页（page/page_size），`data` 顶层带 page/page_size/total。
- `UserDelView.delete`（`teams/{t}/users/batch/delete`）：校验 user_ids 非空 / 非自身 / 非创建者 → `batch_delete_users(team_name, user_ids)` = 删 `PermRelTenant`（user_id∈ids 且 tenant_id=团队PK）+ 删 `UserRole`（user_id∈ids 且 role_id∈团队角色）。

## Goals / Non-Goals

**Goals:** notjoinusers 读 + batch/delete 移除（事务删 tenant_perms+user_role，自身/创建者/空校验）；2 接口标 TEAM_MEMBER_PERMS；对 7070 校准。
**Non-Goals:** 成员加入（企业级 AdminAddUserView）、邀请、pemtransfer、团队生命周期（顺延）。

## Decisions

### 决策 1：复用 P1-e 的 TeamMemberRoleService 扩展
- 在 `TeamMemberRoleService` 加 `listNotJoinUsers(team, query, page, pageSize)` 与 `batchRemoveMembers(team, requesterUserId, userIds)`；`TeamMemberController` 加 2 路由。保持成员域内聚。

### 决策 2：notjoinusers = 企业用户 − 团队成员（不用原始 SQL）
- `userInfoRepository.findByEnterpriseId(eid)` 减去 `permRelTenantRepository.findByTenantId(teamPK)` 的 user_id 集合，query 模糊 nick_name，服务层分页切片。等价 `get_not_join_users` 的 NOT IN 子查询，避免裸 SQL。`data` 顶层 page/page_size/total 用 `GeneralMessage` 的扩展位（与 7070 一致）。

### 决策 3：移除成员事务删两表
- `@Transactional`：`permRelTenantRepository.deleteByUserIdInAndTenantId(userIds, teamPK)` + `userRoleRepository.deleteByUserIdInAndRoleIdIn(userIdStrs, teamRoleIdStrs)`。对齐 `batch_delete_users`。
- 校验顺序与文案对齐 UserDelView：空→「删除成员不能为空」、含自身→「不能删除自己」、含创建者→「不能删除团队创建者！」（均 400）。

### 决策 4：响应对 7070 实测校准
- notjoinusers 项字段顺序 {user_id,nick_name,enterprise_id,email}、data 顶层 page/page_size/total；batch/delete 成功 msg_show「删除成功」、各 400 文案——以 docs/p1f-7070-reference.md 抓存为准。

## Risks / Trade-offs

- **[首写 tenant_perms 删除]** 误删成员关系 → 用 viewer 可丢弃成员校准；事务保证一致；删后回读 notjoinusers 验证；测毕恢复。
- **[user_ids 类型]** 请求体 user_ids 为 int 列表；自身/创建者比较用 Integer。
- **[page/page_size 顶层位置]** 复用 ApiResult.putExtra（P1-a 分页已有 total 顶层）；notjoinusers 还需 page/page_size，确认 GeneralMessage 支持或扩展。

## Migration Plan

无 schema 变更；引入对 tenant_perms 的删 + user_role 删。回滚移除 2 路由与服务方法。联调：7070 抓基线 → 实现 → 移除后回读 diff；用 viewer 可丢弃成员，测毕恢复其成员关系状态（默认 viewer 非 default 成员）。

## Open Questions

- batch/delete 成功响应是否 `data:{bean:{},list:[]}`（general_message 无数据）？以 7070 实测为准。
- notjoinusers 的 `enterprise_id` 字段是字符串 eid（user_info.enterprise_id）——已确认（7070 返回 hex 串）。
