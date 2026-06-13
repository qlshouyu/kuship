## 1. 实现
- [x] 1.1 UserSearchService.search(queryKey)：icontains(nick|email)，user_id 升序，{nick_name,email,user_id}
- [x] 1.2 UserQueryController GET /console/users/query（空 query_key→空 list+你没有查询任何用户）
- [x] 1.3 单测：命中/大小写/空
## 2. 校准
- [x] 2.1 deep-diff 8000 vs 7070（query_key=inter/kuship/空）
- [x] 2.2 全量构建+单测；docs；openspec 校验归档
