## 1. 实现
- [x] 1.1 UserAccessKey 实体 + UserAccessKeyRepository.findByUserId
- [x] 1.2 UserAccessTokenService.list + UserAccessTokenController GET /console/users/access-token
- [x] 1.3 单测：映射 {note,expire_time,user_id,ID}、空
## 2. 校准
- [x] 2.1 deep-diff 8000 vs 7070(空 + DB 直插非空，测毕删)
- [x] 2.2 全量构建+单测；docs；openspec 校验归档
