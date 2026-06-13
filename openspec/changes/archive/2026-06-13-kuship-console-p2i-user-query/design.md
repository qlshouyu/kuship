## Context
`UserFuzSerView.get`：query_key→Q(nick_name__icontains)|Q(email__icontains)→get_user_by_filter（无企业作用域，全局）→[{nick_name,email,user_id}]。空 query_key→general_message(无 list)。

## Goals / Non-Goals
**Goals:** users/query 纯 DB 读 + 对 7070 校准（命中/空）。
**Non-Goals:** 用户写、企业作用域过滤（rainbond 此接口全局）。

## Decisions
### 决策 1：modules/account 新增 UserSearchService.search(queryKey)：UserInfoRepository.findAll 流过滤 icontains(nick|email)，user_id 升序，映射 {nick_name,email,user_id}
### 决策 2：UserQueryController GET /console/users/query；空 query_key→message(查询用户成功? 不——"你没有查询任何用户")，list 空
### 决策 3：JWTAuthApiView 仅登录；全局无企业作用域（对齐 rainbond）

## Risks / Trade-offs
- **[全表扫描]** findAll 流过滤（用户量小）；如需可加 repo @Query。本环境 2 用户，无碍。
- 空 query_key msg_show="你没有查询任何用户"、命中"查询用户成功"——文案对齐。

## Migration Plan
无 schema 变更，纯只读。校准：interop/viewer deep-diff（命中 inter/kuship、空）。

## Open Questions
- 排序：get_user_by_filter 默认 PK(user_id)升序——已据实测(interop 先 viewer)。
