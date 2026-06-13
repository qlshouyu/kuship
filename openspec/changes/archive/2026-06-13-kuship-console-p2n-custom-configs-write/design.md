## Context
`CustomConfigsUserCLView.put`→data 须 list(否则 AbortRequest 请求参数必须为列表)→bulk_create_or_update(configs,nick)：old=list_by_user_nick_name；exist={key:value}；每项无 key skip；exist_value 为真→update(key 入 delete_keys) 否则 create；delete(delete_keys)(ConsoleConfig.filter(key__in).delete() 全局)；bulk_create(all,user_nick_name=nick,update_time null)。响应 general_message(200,success,操作成功,list=data 回显)。纯 DB 写。

## Goals / Non-Goals
**Goals:** custom_configs PUT bulk upsert + 对 7070 校准(回显/DB)。
**Non-Goals:** 单删、GET(P2-k)。

## Decisions
### 决策 1：复用 ConsoleConfig；ConsoleConfigRepository 补 deleteByKeyIn(List)
### 决策 2：UserCustomConfigsService.bulkCreateOrUpdate(nick,list)：old by nick→existTruthy{key:value 真}；每项无 key skip，按 existTruthy 含 key 分 update(收集 key)/create；deleteByKeyIn(updateKeys)+saveAll(全部 new ConsoleConfig(key,value,nick,update_time=null))
### 决策 3：controller PUT @RequestBody Object→非 List→400 请求参数必须为列表；List→处理，回显入参 list、操作成功
### 决策 4：JWTAuthApiView 仅登录，写当前用户；delete 按 key 全局(对齐 rainbond)

## Risks / Trade-offs
- **[全局 key 删]** 对齐 rainbond(ConsoleConfig.filter(key__in).delete() 不限用户)；校准用唯一 key 安全；测后清理。
- **[update_time null]** bulk_create 不设 update_time→null(对齐)。
- **[回显入参]** 响应 list=入参原样回显(非读库)。

## Migration Plan
无 schema 变更。校准：interop PUT 临时配置→回显+GET 验→清理。

## Open Questions
- 回显 list 是否原样入参（含未知字段）——rainbond list=data 原样；kuship 回显入参。
