## Why

P1-e 让团队能查看成员、分配成员角色，但还不能把成员移出团队，也看不到"可加入"的企业用户。本轮补成员关系的读与移除：未加入用户列表（`notjoinusers`）+ 批量移除成员（`users/batch/delete`）。这是团队成员管理闭环的剩余读 + 第一个动 `tenant_perms`（成员关系）的写。

## What Changes

- 成员移除服务（对齐 `user_services.batch_delete_users`）：按 `user_ids` + 团队，事务删除 `tenant_perms`（成员关系，tenant_id=团队 PK）与该批用户在本团队角色的 `user_role`。校验（对齐 `UserDelView`）：`user_ids` 非空（否则"删除成员不能为空"）、不能含当前请求用户自身（"不能删除自己"）、不能含团队创建者（"不能删除团队创建者！"）。
- 未加入用户查询（对齐 `team_repo.get_not_join_users`）：企业（`user_info.enterprise_id`）内、未在该团队 `tenant_perms` 的用户，支持 `query` 模糊 nick_name，分页（page/page_size），`data` 顶层带 page/page_size/total。
- 接口（团队级，TEAM_MEMBER_PERMS 鉴权）：
  - `GET /console/teams/{team_name}/notjoinusers`：未加入用户分页列表（get=610001）；
  - `DELETE /console/teams/{team_name}/users/batch/delete`：批量移除成员（delete=610004）。
- **不包含**（顺延）：成员加入团队（企业级 `AdminAddUserView` /enterprise/admin/add-user，独立 perms 模型）、邀请加入、移交团队管理权（`pemtransfer`）、团队生命周期（建/退/改名/删）。本轮只做"移除成员"与"未加入列表"，不做"加入"。

## Capabilities

### Modified Capabilities
- `team-member-management`: 新增"未加入用户列表"与"批量移除成员"（移除成员事务删 `tenant_perms` + 团队内 `user_role`，含自身/创建者保护）。

## Impact

- 代码：`modules/rbac` 的成员服务补移除与未加入查询；`TeamMemberController` 加 2 路由；`PermRelTenantRepository` 补按用户批量删、`UserRoleRepository` 补按用户批量删团队角色关联、`UserInfoRepository` 补按企业查用户。
- 数据：首次写 `tenant_perms`（删成员关系）+ 删 `user_role`；无 schema 变更。写具破坏性，联调用可丢弃成员（viewer）。
- 接口：新增 2 个成员接口；读对 7070 deep-diff、移除后回读校准；viewer 验无权 403。
- 不变：不改现有接口；不引入成员加入/移交/团队生命周期。
