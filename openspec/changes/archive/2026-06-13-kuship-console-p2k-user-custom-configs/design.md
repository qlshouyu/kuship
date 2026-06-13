## Context
`CustomConfigsUserCLView.get`→`list(user.nick_name)`→`ConsoleConfig.objects.filter(user_nick_name).values()`（全列 dict）。项={ID,key,value,description,update_time,user_nick_name}。update_time DateTimeField→DRF ISO。纯 DB。

## Goals / Non-Goals
**Goals:** custom_configs 列表读 + 对 7070 校准（空 + DB 直插非空）。
**Non-Goals:** 写/删。

## Decisions
### 决策 1：ConsoleConfig 只读实体(@Table console_config: ID/key`不保留字?`/value/description/update_time/user_nick_name) + findByUserNickName
### 决策 2：项按 .values() 顺序 {ID,key,value,description,update_time,user_nick_name}；update_time 原始 LocalDateTime(Jackson ISO，同 P2-c)
### 决策 3：UserCustomConfigsController GET（RequestContext.currentUser.nick_name）；JWTAuthApiView 仅登录

## Risks / Trade-offs
- key 是 SQL 保留字→@Column(name="`key`") 反引号。update_time ISO(同 users)。

## Migration Plan
无 schema 变更，纯只读。校准：interop 空 + DB 直插临时配置行非空，测毕删。

## Open Questions
- 无（项=全列 .values()，update_time ISO 已知）。
