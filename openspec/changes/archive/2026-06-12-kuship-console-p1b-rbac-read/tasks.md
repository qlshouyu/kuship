## 1. 联调与参照准备

- [x] 1.1 沿用 P0/P1-a 环境（共享 console 库 + 同源 SECRET_KEY + 7070 all-in-one），确认 kuship-console 连 host:3306、测试用户 interop 可登录
- [x] 1.2 抓存 7070 `GET /console/users/details` 对 interop 的真实响应到 `docs/p1b-7070-reference.md`，重点记录 `permissions`、每团队 `role_name_list`、`tenant_actions`（完整树）
- [x] 1.3 查测试团队 default 在 7070 下 `tenant_actions.team.sub_models[2].team_app_manage` 是否含 `app_<id>` 子树，定夺 app 子树是天然对齐还是记 defer（design 决策 4 / Open Question）

## 2. 权限码体系移植（modules/rbac/perms）

- [x] 2.1 译 `ENTERPRISE` + `common_perms`（企业角色→权限映射）为 Java 静态结构，权限码与 perms.py 一一对应
- [x] 2.2 译 `TEAM` + `APP` 权限树定义（含分段码、嵌套层级）为 Java 静态结构
- [x] 2.3 实现 `getPerms`/`assemblePerms`（扁平权限项）与 `getTeamPermsModel`/`getAppPermsModel`/`getPermsModel`（布尔树模型）装配工具（布尔树由 `packRolePermsTree` 直接从模板产出，省去中间 get_model）
- [x] 2.4 实现 `packRolePermsTree(model, roleCodes, isOwner)`（权限码并集 → 布尔权限树，owner 全 true）
- [x] 2.5 实现 `listEnterprisePermsByRoles(roles)`（角色名 → `group.name` 字符串集合，含 admin 展开与 app_store.common 叠加）
- [x] 2.6 单测：权限码无重复（对齐 check_perms_metadata）；团队树模型结构快照与 7070 抓存的树对齐

## 3. RBAC 关系实体与只读仓储（modules/rbac）

- [x] 3.1 `RoleInfo` 实体 + 仓储（`@Table("role_info")`，字段 name/kind/kind_id；按 kind+kind_id 查团队角色）
- [x] 3.2 `UserRole` 实体 + 仓储（`@Table("user_role")`，字段 user_id/role_id；按 role_id∈? 且 user_id 查）
- [x] 3.3 `RolePerms` 实体 + 仓储（`@Table("role_perms")` 注意表名带 s，字段 role_id/perm_code/app_id 默认 -1；按 role_id∈? 查权限码并集）
- [x] 3.4 联调验证三表查询非空（用 interop 在 default 团队的真实角色/权限数据：role_info 3 行、user_role role_id=1、role_perms(role 1)=103 行）

## 4. 角色/权限解析服务（modules/rbac/service）

- [x] 4.1 `RbacReadService.listRoles(enterpriseId, userId)`：取 `enterprise_user_perm.identity` 逗号分隔（复用 EnterpriseUserPerm，无记录返回空）
- [x] 4.2 `getUserTeamRoles(tenantId, userId)`：role_info(kind=team,kind_id) ∩ user_role → `[{role_id, role_name}]`（对齐 get_user_roles 的 roles）
- [x] 4.3 `getUserTeamActions(tenantId, user, isOwner, isEntAdmin)`：取角色 role_perms 并集 → packRolePermsTree（全局 app_id=-1 落团队节点，team_app_manage 按无应用重建为空；owner/entAdmin 短路全 true）
- [x] 4.4 单测覆盖：owner 全 true、entAdmin 全 true、普通成员按码装配、非成员空角色

## 5. 接回 users/details（modules/account）

- [x] 5.1 `AccountProfileService` 注入 `RbacReadService`，企业 `permissions` 改为 `listEnterprisePermsByRoles(roles)`（替换空数组）；`roles` 也改为 `listRoles` 忠实实现
- [x] 5.2 每团队 `role_name_list` 改为 `getUserTeamRoles(...)`（替换空数组）
- [x] 5.3 每团队 `tenant_actions` 改为 `getUserTeamActions(...)`，复用既有 owner/entAdmin 判定口径（替换空对象）
- [x] 5.4 更新 `AccountProfileService` 类注释，移除"RBAC 字段留 P1-b"占位说明

## 6. 7070 校准与收尾

- [x] 6.1 用递归 deep-diff 脚本（写成文件 `bash` 跑）逐叶子比对 8000 vs 7070 的 `users/details`，聚焦 permissions/role_name_list/tenant_actions
- [x] 6.2 差异归零（permissions 集合语义、role_name_list、完整 tenant_actions 树逐叶子全一致；app 子树双方皆空天然对齐，无 defer 项）
- [x] 6.3 全量构建 + 现有单测通过（33/33）；`docs/p1b-7070-reference.md` §5 记录无遗留 defer 差异
- [x] 6.4 走 openspec 校验（`openspec validate` 通过），准备归档
