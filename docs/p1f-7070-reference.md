# P1-f（成员移除 + 未加入列表）7070 校准基线

> 环境同前；interop(700002, default owner+creater)、viewer(700003)。成员关系表 `tenant_perms`。
> `notjoinusers`、`users/batch/delete` 继承 RegionTenantHeaderView 需 `?region_name=`。

## 1. GET teams/{t}/notjoinusers

```
?region_name=rainbond[&page=&page_size=&query=]
{"code":200,"msg":null,"msg_show":null,"data":{"bean":{},"list":[
  {"user_id":700003,"nick_name":"viewer","enterprise_id":"b16bf28d…","email":"viewer@kuship.cn"}
],"page":1,"page_size":10,"total":1}}
```
- list 项字段顺序 {user_id, nick_name, enterprise_id(string hex), email}；`data` 顶层含 `page`/`page_size`/`total`。
- 语义：企业内（user_info.enterprise_id=eid）未在该团队 tenant_perms 的用户；query 模糊 nick_name。

## 2. DELETE teams/{t}/users/batch/delete  body {"user_ids":[...]}

- 空 `user_ids`：`HTTP 400 {"code":400,"msg":"failed","msg_show":"删除成员不能为空","data":{"bean":{},"list":[]}}`
- 含自身：`400 {"msg":"failed","msg_show":"不能删除自己"}`
- 含团队创建者：`400 {"msg":"failed","msg_show":"不能删除团队创建者！"}`（校验顺序：先自身、后创建者；default creater=interop=自身，故 live 无法单独触发创建者分支，留单测）
- 成功：`HTTP 200 {"code":200,"msg":"delete the success","msg_show":"删除成功","data":{"bean":{},"list":[]}}`；删 `tenant_perms`(user_id∈ids, tenant_id=团队PK) + `user_role`(user_id∈ids, role_id∈团队角色)。

## 3. 鉴权

TEAM_MEMBER_PERMS：notjoinusers get=610001、batch/delete delete=610004。

## 4. 校准要点

- 用 viewer 作可丢弃成员：临时 `INSERT tenant_perms(700003,1,...)` → 测移除 → 应回到「default 仅 700002」。
- 校验文案与 HTTP 400 精确对齐；成功 data 为空壳 {bean:{},list:[]}。

## 5. 实跑校准结果（8000 vs 7070）

- **notjoinusers deep-diff 全 0**（含 query 过滤）：list 项 {user_id,nick_name,enterprise_id,email} + data 顶层 page/page_size/total 逐叶子一致。✓
- **batch/delete**（临时加 viewer 入 default）：空→400 删除成员不能为空；自己→400 不能删除自己；删 viewer 700003→200 删除成功，default 还原为仅 700002；回读 notjoinusers 重含 viewer。与 7070 一致。✓
- **创建者保护**：default creater=interop=自身，live 无法单独触发（自身校验先命中）；单测覆盖「非请求者的创建者」分支。
- **鉴权**：viewer（无 610xxx）notjoinusers/batch-delete → 403。✓
- **清理**：测毕 viewer 已被移除，default 成员还原为 700002。

结论：成员移除 + 未加入列表与 7070 行为一致，无遗留 defer。
