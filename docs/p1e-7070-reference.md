# P1-e（团队成员角色管理）7070 校准基线

> 环境同前；interop(700002, default owner)、**viewer(700003)** 本轮 DB 直插 default 成员（`tenant_perms` row：user_id=700003,tenant_id=1,identity='',enterprise_id=1）。
> `users`、`users/roles`、`users/{uid}/perms` 继承 RegionTenantHeaderView 需 `?region_name=`；`users/{uid}/roles` 继承 TenantHeaderView 不需要。
> **成员关系表 = `tenant_perms`**（user_id int, tenant_id int=团队PK, identity, enterprise_id int, role_id）；角色分配表 = `user_role`（user_id/role_id varchar）。

## 1. 读接口 bean/list 形态

- `GET teams/{t}/users?region_name=&page=&query=`：`data.list=[{user_id, user_name, nick_name, email, role_info}]`，`data.total`。**role_info 取「当前请求用户」在该团队的角色**（rainbond 现状，逐行相同），形如 `[{role_id:"1",role_name:"管理员"}]`。分页每页 8。
- `GET teams/{t}/users/roles?region_name=`：`data.list=[{nick_name, email, user_id, roles}]`；`roles=[{role_id,role_name}]`（取自 user_role，role_id 为 **string**）；**团队创建者额外附 `{role_id:0(int), role_name:"拥有者"}`**。
- `GET teams/{t}/users/{uid}/roles`：`data.bean={nick_name, user_id, roles:[{role_id(string),role_name}]}`。
- `GET teams/{t}/users/{uid}/perms?region_name=`：`data.bean={user_id, permissions:树}`。permissions 树同 P1-b tenant_actions（team 根）。用**请求者** is_owner/is_ent_admin（rainbond 现状）：interop(owner) 请求任意成员 → 全 true。

## 2. 写接口（user_role 重建，viewer=700003）

- `PUT teams/{t}/users/{uid}/roles` body `{"roles":[2]}`：`bean={nick_name,user_id,roles:[{role_id:"2",role_name:"开发者"}]}`，msg_show=null。落库 `user_role`(700003,2)。先删该用户团队内 user_role 再按 `roleIds∩团队角色ID` 批量写。
- `PUT` body `{"roles":[9999]}`（非空且无有效）：HTTP 404 `{"code":404,"msg":"no found can update params","msg_show":"传入角色不可被分配，请检查参数"}`。
- `DELETE teams/{t}/users/{uid}/roles`：清空该用户团队 user_role，`bean={nick_name,user_id,roles:[]}`，msg_show=null。

## 3. 鉴权

TEAM_MEMBER_PERMS：get=610001 / post=610002 / put=610003 / delete=610004。

## 4. 校准要点

- role_id 混合类型：roles 普通项 string、拥有者 0 为 int——照搬。
- `users` 的 role_info 是请求者角色（非各行用户）——忠实复刻。
- 写联调用 viewer（可丢弃成员关系 + 角色），测毕清理 tenant_perms row 与 user_role。勿动 interop 角色 1。
- 残留校验：DELETE/PUT 后 user_role(700003) 应与预期一致。

## 5. 实跑校准结果（8000 vs 7070）

- **读接口 deep-diff 全 0**：`users`、`users/roles`、`users/{uid}/roles`(interop & viewer)、`users/{uid}/perms`（带 region_name）逐叶子一致（含 role_info=请求者角色、拥有者 {role_id:0} int、roles role_id string、perms 树）。✓
- **写流程**（viewer 700003）8000 与 7070 一致：PUT roles=[2]→bean roles[{role_id:"2",role_name:"开发者"}]、落库 user_role(700003,2)；PUT [9999]→404 传入角色不可被分配；DELETE→roles:[]、user_role 清空。✓
- **鉴权**：viewer（无 610xxx）GET users / PUT users/{uid}/roles → 403。✓
- **清理**：测毕删除 viewer 的 tenant_perms 成员行与 user_role，default 成员还原为仅 interop(700002)。

结论：团队成员角色管理底座与 7070 行为一致，无遗留 defer。
