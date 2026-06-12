## Why

团队管理还缺三个常用的"团队设置"写操作：移交团队管理权、修改团队名称、退出团队。它们都是纯 console 库写（不依赖 region 后端 provision），低风险且可对 7070 校准——适合在团队建/删（依赖 region）之前先补齐。

## What Changes

- 移交团队管理权（对齐 `UserPemTraView`，**owner-only**）：`POST /console/teams/{team_name}/pemtransfer` body `{user_id}` → 将团队 `creater` 改为该用户。仅当前团队创建者可操作，否则无权（403/10402）。
- 修改团队名称（对齐 `TeamNameModView`，团队成员可操作、无权限码门槛）：`POST /console/teams/{team_name}/modifyname` body `{new_team_alias, new_logo?}` → 更新 `tenant_alias`（及可选 `logo`）与 `update_time`，返回团队完整 bean。
- 退出团队（对齐 `TeamExitView`，成员自退）：`GET /console/teams/{team_name}/exit` → 团队创建者不可退（409「您是当前团队创建者，不能退出此团队」）；否则事务删除自身 `tenant_perms` 成员关系与本团队 `user_role`，返回「退出团队成功」。
- **不包含**（顺延）：团队建/删（依赖 region provision/销毁）、成员加入团队（企业级 `AdminAddUserView`，且会 provision region）。本轮仅纯 DB 的团队设置写。

## Capabilities

### New Capabilities
- `team-settings`: 团队管理权移交（owner-only）、团队名称/logo 修改、退出团队（创建者不可退），均纯 console 库写。

## Impact

- 代码：新增 `TeamSettingsService` 与 `TeamSettingsController`（`modules/team`）；复用 `TenantsRepository`（save）、`PermRelTenantRepository`（按用户删成员）、`UserRoleRepository`（删团队角色关联）。
- 数据：写 `tenant_info`（creater/alias/logo/update_time）、删 `tenant_perms`/`user_role`（退出）；无 schema 变更；不触 region。
- 接口：新增 3 个；modifyname bean 为团队完整 to_dict（对 7070 校准，update_time 易变除外）。
- 鉴权：pemtransfer 为 owner-only（非权限码，对齐 `TeamOwnerView`）；modifyname/exit 无权限码门槛（团队成员即可，对齐 rainbond 路由无 perms 标签）。
- 风险：pemtransfer/exit 改团队归属与成员关系——联调用可丢弃数据（viewer）并测毕还原，绝不留改 default 的 creater。
