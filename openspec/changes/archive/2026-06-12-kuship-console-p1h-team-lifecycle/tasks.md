## 1. 联调与参照准备

- [x] 1.1 确认环境 + 已实测：建无 region 团队(200 团队添加成功+完整 bean)、删(200 删除团队成功，仅删 tenant_perms+tenant_info 留 role 孤儿)、校验(空名/重名 400；非法 namespace rainbond 源 bug)
- [x] 1.2 建团队后读 bean 确认 is_active 等字段值，记 `docs/p1h-7070-reference.md`

## 2. PermsCatalog 默认角色码表

- [x] 2.1 加 `DEFAULT_TEAM_ROLE_PERMS`：管理员(103)/开发者(78)/观察者(24) 码表（移植 perms.py）
- [x] 2.2 单测：三角色码数量与关键码

## 3. 服务（TeamLifecycleService）

- [x] 3.1 `is_qualified_name` 校验（k8s 命名正则）
- [x] 3.2 随机 tenant_name(8 位 a-z0-9，查重)
- [x] 3.3 `createTeam(user, alias, namespace, logo)`（@Transactional）：校验(空名/namespace/重名)→建 tenant_info+tenant_perms(owner)+3 默认角色(+role_perms)+创建者管理员 user_role→返回 to_dict bean
- [x] 3.4 `deleteTeam(teamName, eid)`：团队不存在→404；否则删 tenant_perms+tenant_info
- [x] 3.5 单测：建(成功/空名/重名/非法 namespace)、删(成功/不存在)、默认角色与 owner 关系建立

## 4. 接口

- [x] 4.1 `TeamLifecycleController`：POST /console/teams/add-teams、DELETE /console/teams/{team_name}/delete
- [x] 4.2 响应包裹对齐（建：团队添加成功+bean；删：删除团队成功；校验文案/HTTP）

## 5. 实跑校准与收尾

- [x] 5.1 建团队校准：8000 建无 region 团队→bean(除随机 tenant_name/时间戳)与 7070 一致；DB 确认 3 角色+role_perms+owner+管理员 user_role
- [x] 5.2 校验校准：空名/重名 400 文案一致；非法 namespace 返回干净 400（记录与 7070 源 bug 的偏离）
- [x] 5.3 删团队校准：8000 删→200 删除团队成功，tenant_perms+tenant_info 删除；清理孤儿 role_info/user_role
- [x] 5.4 全量构建 + 单测；docs 记结论；清理所有测试团队，确认 default 完好
- [x] 5.5 openspec 校验，准备归档
