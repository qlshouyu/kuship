## Context
`UserAccessTokenCLView.get`：get_user_access_key(user_id)=UserAccessKey.filter(user_id)→values("note","expire_time","user_id","ID")。纯 DB。expire_time int 可空。

## Goals / Non-Goals
**Goals:** 令牌列表读 + 对 7070 校准（空 + DB 直插非空）。
**Non-Goals:** 令牌增删。

## Decisions
### 决策 1：UserAccessKey 只读实体(@Table user_access_key: ID/note/user_id/access_key/expire_time) + findByUserId
### 决策 2：UserAccessTokenService.list(userId)→[{note,expire_time,user_id,ID}]；UserAccessTokenController GET（用 RequestContext.currentUser）
### 决策 3：JWTAuthApiView 仅登录；返回当前用户自己的令牌

## Risks / Trade-offs
- 极简纯 DB；expire_time int 直出（无日期格式坑）。

## Migration Plan
无 schema 变更，纯只读。校准：interop 空 + DB 直插临时令牌行非空，测毕删。

## Open Questions
- 无（项 4 字段、int expire_time，简单）。
