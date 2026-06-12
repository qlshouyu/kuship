## Why

团队管理还差最后一块：创建团队与删除团队。rainbond 的建/删在绑定数据中心（region）时会调 Go region 后端 provision/销毁 namespace，但**不绑定 region 时（`useable_regions` 为空）建/删是纯 console 库操作**——已实测确认。kuship 当前无应用/region 域，团队天然无 region 绑定，故本轮做"无 region 绑定路径"的团队建/删，纯 DB 可校准；region provision 留待应用/region 域引入后补。

## What Changes

- 创建团队（对齐 `AddTeamView` + `team_services.create_team`，无 region 绑定路径）：`POST /console/teams/add-teams` body `{team_alias, namespace, useable_regions?, logo?}` →
  - 校验：`team_alias` 非空（否则「团队名不能为空」）、`namespace` 合法 k8s 名（`is_qualified_name`，非法返回 400「命名空间只能由小写字母、数字或-组成，并且必须以字母开始、以数字或字母结尾」）、`tenant_alias` 在本企业不重复（否则「该团队名已存在」）；
  - 事务建：`tenant_info`（随机 8 位 tenant_name、creater=当前用户、alias/namespace/logo、limit_memory=0）、`tenant_perms`（owner）、3 个默认角色（管理员/开发者/观察者 + 各自 `role_perms`，码取 `DEFAULT_TEAM_ROLE_PERMS`）、把"管理员"角色分配给创建者（`user_role`）；
  - `useable_regions` 非空时的 region provision **本轮不做**（无 region 域）；
  - 返回团队完整 bean（to_dict），`msg_show=团队添加成功`。
- 删除团队（对齐 `TeamDelView` + `delete_by_tenant_id`，无 region 路径）：`DELETE /console/teams/{team_name}/delete` → 删 `tenant_perms` + `tenant_info`（与 rainbond 一致，**不级联删 role_info/user_role**，留孤儿）；团队若有 region 绑定则需先卸载 region（本轮 kuship 团队无 region，不涉及）；返回 `msg_show=删除团队成功`。
- **不包含**（顺延/无依赖）：region 绑定 provision/卸载（依赖 region 域）；成员加入入口 `AdminAddUserView`（会建团队 + provision region）；改名/移交/退出（已在 P1-g）。

## Capabilities

### New Capabilities
- `team-lifecycle`: 团队创建（含默认角色初始化与 owner 关系）与删除（无 region 绑定路径），纯 console 库写。

## Impact

- 代码：新增 `TeamLifecycleService` 与 `TeamLifecycleController`（modules/team）；`PermsCatalog` 加 `DEFAULT_TEAM_ROLE_PERMS`（3 默认角色码表）；复用 Tenants/PermRelTenant/RoleInfo/RolePerms/UserRole 仓储写。
- 数据：写 `tenant_info`/`tenant_perms`/`role_info`/`role_perms`/`user_role`（建）、删 `tenant_perms`/`tenant_info`（删）；无 schema 变更；不触 region。
- 接口：新增 2 个；建团队 bean 为完整 to_dict（时间戳格式化对齐 7070）。
- 偏离：非法 namespace 时 rainbond 因文案含未转义引号实际抛 500/TypeError（源 bug）；kuship 返回干净 400（按校准打法对源 bug 可偏离）。
- 风险：建/删真实团队——联调用可丢弃团队（建后即删 + 清理孤儿 role_info），绝不动 default。
