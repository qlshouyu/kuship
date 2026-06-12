## 1. 联调与参照准备

- [x] 1.1 确认环境在线 + 真实 SECRET_KEY 回写
- [x] 1.2 抓存 7070 读基线到 `docs/p1e-7070-reference.md`：`users`、`users/roles`、`users/{uid}/roles`、`users/{uid}/perms`（用 interop）
- [x] 1.3 抓写基线：用可丢弃成员（DB 直插 viewer 的 perm_rel_tenant 成员关系），走 PUT/DELETE users/{uid}/roles，记录响应与 user_role 落库；测毕清理

## 2. 仓储与查询支撑

- [x] 2.1 `PermRelTenantRepository.findByTenantId(Integer)`（团队 PK → 成员关系）
- [x] 2.2 `UserInfoRepository.findByUserIdIn(List<Integer>)`（成员用户对象）
- [x] 2.3 `UserRoleRepository`：`findByUserIdAndRoleIdIn`、`deleteByUserIdAndRoleIdIn`、`saveAll`（成员角色重建用）

## 3. 成员角色服务

- [x] 3.1 `getTeamUsers(team, query)`：perm_rel_tenant → user_info（query 模糊 nick/real_name）
- [x] 3.2 `listUsers(team, requester, query, page)`：分页(8) + 每项 role_info（取 requester 角色，忠实 rainbond）+ total
- [x] 3.3 `getUsersRoles(team)`：每成员 {nick_name,email,user_id,roles}，创建者附 {role_id:0,role_name:拥有者}
- [x] 3.4 `getUserRoles(team, userId)`：{nick_name,user_id,roles:[{role_id,role_name}]}
- [x] 3.5 `updateUserRoles(team, userId, roleIds)`（@Transactional：删团队内该用户 user_role + roleIds∩团队角色 批量写；全不可分配且非空→报错）
- [x] 3.6 `deleteUserRoles(team, userId)`：清空该用户团队 user_role
- [x] 3.7 `getUserPerms(team, targetUserId, requester)`：{user_id, permissions}（复用 getUserTeamActions，用 requester owner/entAdmin 标志）
- [x] 3.8 单测：角色重建/交集校验/清空/拥有者标记/perms 包裹（mock 仓储）

## 4. 接口与鉴权

- [x] 4.1 `TeamMemberController`：GET users；GET users/roles；GET/PUT/DELETE users/{uid}/roles；GET users/{uid}/perms
- [x] 4.2 标 `@RequiresPerms(TEAM,...)`：get=610001 / put=610003 / delete=610004
- [x] 4.3 响应包裹对齐（general_message list/bean/total、role_id 类型、msg_show）

## 5. 实跑校准与收尾

- [x] 5.1 读接口对 7070 deep-diff（users/users-roles/uid-roles/uid-perms）逐叶子一致
- [x] 5.2 写接口校准：对可丢弃成员 PUT 角色→读回→DELETE，8000 与 7070 一致；user_role 落库正确；测毕清理
- [x] 5.3 鉴权校准：viewer（无 610xxx）访问成员接口 → 403/10402
- [x] 5.4 全量构建 + 单测通过；docs 记差异/defer；清理临时成员关系
- [x] 5.5 openspec 校验，准备归档
