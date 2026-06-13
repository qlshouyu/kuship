## 1. 实现
- [x] 1.1 TenantServiceInfo 扩全列 + toDict()
- [x] 1.2 ServiceGroupRelation 实体 + repo
- [x] 1.3 ComponentDetailService（to_dict+组+命名空间+disk_cap+ws+is_third）
- [x] 1.4 ComponentDetailController GET .../detail
- [x] 1.5 单测：to_dict 字段/顺序、未分组、is_third
## 2. 校准
- [x] 2.1 建组件 deep-diff 8000 vs 7070(bean 全字段)
- [x] 2.2 全量构建+单测；docs；清理；openspec 校验归档
