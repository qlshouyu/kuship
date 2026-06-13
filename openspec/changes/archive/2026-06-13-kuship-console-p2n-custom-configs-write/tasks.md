## 1. 实现
- [x] 1.1 ConsoleConfigRepository.deleteByKeyIn
- [x] 1.2 UserCustomConfigsService.bulkCreateOrUpdate(nick,list)
- [x] 1.3 controller PUT /console/users/custom_configs（非 list→400；回显 list+操作成功）
- [x] 1.4 单测：create/update 分流、无 key skip、非 list 400
## 2. 校准
- [x] 2.1 interop PUT→回显一致+DB 写入；GET 读回；非 list 400；测后清理
- [x] 2.2 全量构建+单测；docs；openspec 校验归档
