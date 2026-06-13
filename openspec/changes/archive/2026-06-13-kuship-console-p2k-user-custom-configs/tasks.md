## 1. 实现
- [x] 1.1 ConsoleConfig 实体 + ConsoleConfigRepository.findByUserNickName
- [x] 1.2 UserCustomConfigsService.list(nick) + UserCustomConfigsController GET
- [x] 1.3 单测：项字段/顺序、空
## 2. 校准
- [x] 2.1 deep-diff 8000 vs 7070(空 + DB 直插非空,含 update_time ISO,测毕删)
- [x] 2.2 全量构建+单测；docs；openspec 校验归档
