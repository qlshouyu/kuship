## 1. 实现
- [x] 1.1 UserAccessKeyRepository 补 findByUserIdAndId、(save/delete 继承)
- [x] 1.2 UserAccessTokenService 补 create(note 必填/generateKey/expire_time)/getById/regenerate/delete
- [x] 1.3 controller：POST /console/users/access-token + UserAccessTokenRUDController GET/PUT/DELETE /{id}
- [x] 1.4 单测：create(note 空/成功 access_key 40hex)/get 404/delete
## 2. 校准
- [x] 2.1 interop：note 空→400；create→验 bean 结构+DB；get/{id}→diff;put→换 key;delete→200+gone；确定性分支对 7070 一致
- [x] 2.2 全量构建+单测；docs；清理临时令牌；openspec 校验归档
