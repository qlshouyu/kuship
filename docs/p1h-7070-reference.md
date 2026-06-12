# P1-h（团队创建/删除，无 region 路径）7070 校准基线

> 环境同前；interop(700002)。企业 PK=1（tenant_perms.enterprise_id 用此 int）。均纯 console 库（useable_regions 为空不触 region）。

## 1. POST /console/teams/add-teams  body {team_alias, namespace, useable_regions:"", logo?}

成功（无 region）：
```
{"code":200,"msg":"success","msg_show":"团队添加成功","data":{"bean":{
 "ID":4,"tenant_id":"<32hex>","tenant_name":"<随机8位a-z0-9>","is_active":true,
 "create_time":"<now yyyy-MM-dd HH:mm:ss>","creater":700002,"limit_memory":0,
 "update_time":"<now>","tenant_alias":"<alias>","enterprise_id":"<eid str>",
 "namespace":"<ns>","logo":""},"list":[]}}
```
建团队事务副作用（实测 TID 验证）：
- `tenant_info`：is_active=true、limit_memory=0、tenant_name 随机 8 位、creater=当前用户。
- `tenant_perms`：user_id=创建者、identity="owner"、enterprise_id=**1（企业 PK int）**。
- 3 默认角色 `role_info`(kind=team,kind_id=tenant_id)：管理员/开发者/观察者。
- `role_perms`：管理员 **103**、开发者 **78**、观察者 **24** 条（码取 DEFAULT_TEAM_ROLE_PERMS，app_id=-1）。
- `user_role`：创建者 ← 管理员角色。

校验：
- team_alias 空 → 400「团队名不能为空」。
- tenant_alias 本企业重复 → 400「该团队名已存在」。
- namespace 非法 → rainbond **源 bug**（ErrQualifiedName msg_show 含未转义引号 → TypeError/500，返回 code 10401 traceback）。**kuship 偏离：返回干净 400「命名空间只能由小写字母、数字或-组成，并且必须以字母开始、以数字或字母结尾」**。

## 2. DELETE /console/teams/{team_name}/delete

```
{"code":200,"msg":"delete a tenant successfully","msg_show":"删除团队成功","data":{"bean":{},"list":[]}}
```
- 仅删 `tenant_perms`(tenant_id=团队PK) + `tenant_info`。**不级联删 role_info/user_role**（rainbond delete_by_tenant_id 如此，留孤儿）。
- 团队不存在 → 404「{team}团队不存在」。
- 有 region 绑定的团队删除会先卸载 region（本轮 kuship 团队无 region，不涉及）。

## 3. 校准要点

- tenant_name 随机、create_time/update_time=now → 这三字段不参与逐字节 diff（验存在/格式）；其余 bean 字段比对。
- 时间戳格式 yyyy-MM-dd HH:mm:ss（见 playbook 坑）。
- 联调用可丢弃团队：建→校准→删→**清理孤儿 role_info/user_role**；绝不动 default。

## 4. 实跑校准结果（8000 vs 7070）

- **建团队**：200 团队添加成功；bean is_active=true/limit_memory=0/creater/ns/alias 一致，tenant_id 32 位、tenant_name 8 位、create_time `yyyy-MM-dd HH:mm:ss`（随机名/时间戳不逐字节比，其余一致）。✓
- **建团队副作用**：role_perms 管理员103/开发者78/观察者24、tenant_perms(owner,enterprise_id=1)、创建者←管理员 user_role，与 7070 完全一致。✓
- **校验**：空名→400 团队名不能为空；重名→400 该团队名已存在；非法 namespace→**400 干净文案**（rainbond 此处源 bug 抛 500，kuship 偏离返回规范 400）。✓
- **删团队**：200 删除团队成功，删 tenant_perms+tenant_info（role_info/user_role 留孤儿，对齐 rainbond）；不存在→404「{team}团队不存在」。✓
- **清理**：测试团队建后即删 + 清孤儿 role_info/user_role；非 default 团队=0，default 完好。

结论：团队建/删（无 region 路径）与 7070 一致；region 绑定 provision/卸载留待 region 域（明确 defer）。
