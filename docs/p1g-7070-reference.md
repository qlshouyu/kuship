# P1-g（团队设置写：移交/改名/退出）7070 校准基线

> 环境同前；interop(700002, default owner/creater)、viewer(700003)。均纯 console 库写，不触 region。
> pemtransfer/modifyname/exit 继承 RegionTenantHeaderView 需 `?region_name=`。

## 1. POST teams/{t}/pemtransfer  body {"user_id":700003}（owner-only，TeamOwnerView）

```
{"code":200,"msg":"success","msg_show":"移交成功","data":{"bean":{},"list":[]}}
```
- 改 `tenant_info.creater = user_id`。非 owner → 403/10402（TeamOwnerView raise NoPermissionsError）。
- 注：移交后原 owner 失去 owner 权，无法移回 → 联调测后**直接 DB 还原** creater=700002。

## 2. POST teams/{t}/modifyname  body {"new_team_alias":"...","new_logo":""?}（成员可操作，无 perms 标签）

```
{"code":200,"msg":"update success","msg_show":"团队信息修改成功","data":{"bean":{
 "ID":1,"tenant_id":"a274b41c…","tenant_name":"default","is_active":true,
 "create_time":"2026-06-12 21:09:00","creater":700002,"limit_memory":0,
 "update_time":"<now>","tenant_alias":"<new>","enterprise_id":"b16bf28d…",
 "namespace":"default","logo":""},"list":[]}}
```
- bean = 完整 tenant to_dict（12 字段）。`update_time` 每次保存刷新为当前时间（Django auto_now）→ **校准排除该字段**。日期格式 `yyyy-MM-dd HH:mm:ss`。is_active=bool true。
- 仅 new_team_alias 非空才更新；new_logo 非空才更新 logo。

## 3. GET teams/{t}/exit（成员自退，无 perms 标签）

- 创建者：`HTTP 409 {"code":409,"msg":"not allow exit.","msg_show":"您是当前团队创建者，不能退出此团队"}`
- 成员成功：`HTTP 200 {"code":200,"msg":"success","msg_show":"退出团队成功","data":{"bean":{},"list":[]}}`；删自身 tenant_perms + 本团队 user_role。

## 4. 校准要点

- pemtransfer owner-only 是 TeamOwnerView 门槛（非权限码）；modifyname/exit 无码门槛（团队成员/登录即可）。
- 写联调用 viewer：exit 用 viewer 自退（只删 viewer）；pemtransfer 移交给 viewer 后 DB 还原 creater=700002；modifyname 改后还原 alias。

## 5. 实跑校准结果（8000 vs 7070）

- **modifyname**：bean 12 字段除 update_time(now,易变)外与 7070 一致；`create_time` 修正为 `yyyy-MM-dd HH:mm:ss`（曾误为 ISO，已用 DateTimeFormatter 格式化，对齐 TeamReadService）。is_active=true。msg=update success/团队信息修改成功。测后还原 alias。✓
- **pemtransfer**：非 owner(viewer)→403/10402；owner(interop)移交 viewer→200 移交成功，creater 改后 DB 还原 700002。✓
- **exit**：interop(creater)→409 您是当前团队创建者，不能退出此团队；viewer(入 default)自退→200 退出团队成功，成员关系删除。✓
- **清理**：creater 还原 700002、default 成员仅 700002、alias 还原。

结论：团队设置写(移交/改名/退出)与 7070 一致，无遗留 defer。**坑**：LocalDateTime 默认 Jackson 序列化为 ISO(带 T+微秒)，与 7070 的 `yyyy-MM-dd HH:mm:ss` 不符——含时间戳的 bean 必须显式格式化（用 TeamReadService 的 TS 同款 formatter）。
