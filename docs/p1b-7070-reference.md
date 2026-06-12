# P1-b（RBAC 读路径）7070 校准基线

> 环境同 P0/P1-a：rainbond-console v6.9.0-release(7070)，共享 console 库；
> 测试用户 interop(user_id=700002)，企业 b16bf28d…(KuShip)，团队 default(tenant_id=a274b41c…, owner=700002)。
> users/details 的完整响应（含 permissions/role_name_list/tenant_actions 整棵树）已存于 `docs/p1a-7070-reference.md`，本文件只记 P1-b 特有的校准事实与决策。

## 1. 权限码体系来源（决策 1 验证）

- 运行版 7070 容器内 `console/utils/perms.py`（616 行，md5 `b7e1baac34c3560f388bd1fb0bbd7084`）与本仓 `reference/rainbond-console/console/utils/perms.py` **逐字节相同**。
- 结论：reference 子模块的 `perms.py` 即权威源，直接据其移植 `ENTERPRISE`/`common_perms`/`TEAM`/`APP` 与装配函数。

## 2. 企业 permissions（interop=admin，决策 3 验证）

`list_enterprise_perms_by_roles(["admin"])` → 12 条（集合，顺序无意义）：
```
app_store.create_app, app_store.edit_app, app_store.delete_app, app_store.import_app,
app_store.export_app, app_store.create_app_store, app_store.get_app_store,
app_store.edit_app_store, app_store.delete_app_store, app_store.edit_app_version,
app_store.delete_app_version, app_store.get_ent_teams
```
推导：admin 角色展开 ENTERPRISE["app_store"] 的 11 个权限名（前缀 `app_store.`），叠加 `common_perms` 的 6 个名（仅 `get_ent_teams` 为新增），并集 12 个。`roles` = `enterprise_user_perm.identity.split(",")` = `["admin"]`。

## 3. 团队 RBAC 数据（interop @ default，决策来源）

DB 实测：
- `role_info`：default 团队（kind=team, kind_id=a274b41c…）有 3 个角色 ID=1/2/3（管理员/开发者/观察者，对应 perms.py 的 `DEFAULT_TEAM_ROLE_PERMS`）。
- `user_role`：interop(user_id="700002") 仅 role_id="1"。
- → `role_name_list` = `[{"role_id":"1","role_name":"管理员"}]`（role_id 为字符串，取自 user_role.role_id；role_name 取自 role_info.name）。

注意表/列：`role_info(ID int, name varchar32, kind varchar32, kind_id varchar64)`、`user_role(ID, user_id varchar32, role_id varchar32)`、`role_perms(ID, role_id int, perm_code int, app_id int)`。表名是 **role_perms**（带 s）。`kind_id` 存 **tenant_id**（非 PK）。

## 4. tenant_actions 形态（决策 2 + 决策 4 验证）

- 叶子是 `{name: bool}`，**不含 code**（pack 后剥离）。owner / 企业管理员 → 全 `true`。
- interop 是 owner+企业管理员 → 整棵 `team` 树全 true。
- `tenant_actions = {"team": {...}}`；`team.sub_models` 共 6 个，顺序固定：
  `[0]team_overview(2) [1]team_app_create(1) [2]team_app_manage [3]team_gateway_manage(4 subs) [4]team_plugin_manage(4) [5]team_manage(5 subs)`。
- **决策 4 落定（app 子树 defer）**：`team_app_manage` 实测为 `{"sub_models": [], "perms": {}}`（`perms` 是空 **dict** 非 list）。源于 `get_roles_union_perms` 用团队下应用列表（ServiceGroup）重建该节点；interop 的 default 团队无应用 → 空。kuship 暂无应用域，按"无应用"装配即天然与 7070 一致，**无需 defer 差异**。待应用域引入后再补 `app_<id>` 子树。

## 5. 校准结论

interop 用例下，P1-b 三字段（permissions / role_name_list / tenant_actions）均可与 7070 逐叶子对齐，无遗留 defer 差异（app 子树因双方皆空而天然一致）。
